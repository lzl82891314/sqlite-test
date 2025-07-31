package com.grapecity.phoenix.sqlitetest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Users表操作类
 */
public class UserRepository {
    
    private static final Logger logger = LoggerFactory.getLogger(UserRepository.class);
    private final JdbcTemplate jdbcTemplate;
    private final Random random = new Random();
    
    public UserRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }
    
    /**
     * 创建users表
     */
    public void createUsersTable() {
        String createTableSql = """
            CREATE TABLE IF NOT EXISTS users (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                email TEXT,
                age INTEGER,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                data TEXT
            )
            """;
        
        try {
            jdbcTemplate.execute(createTableSql);
            logger.info("Users table created or already exists");
        } catch (Exception e) {
            logger.error("Failed to create users table", e);
            if (NfsTestException.isDatabaseCorruption(e)) {
                throw new NfsTestException("Database corruption detected while creating table", e);
            }
            throw new NfsTestException("Failed to create users table", e);
        }
    }
    
    /**
     * 插入初始测试数据
     */
    public void insertInitialData(int count) {
        logger.info("Inserting {} initial users", count);
        
        try {
            for (int i = 1; i <= count; i++) {
                String name = "User" + i;
                String email = "user" + i + "@test.com";
                int age = 20 + random.nextInt(50);
                String data = "InitialData-" + UUID.randomUUID().toString().substring(0, 8);
                
                insertUser(name, email, age, data);
            }
            logger.info("Successfully inserted {} initial users", count);
        } catch (Exception e) {
            logger.error("Failed to insert initial data", e);
            if (NfsTestException.isDatabaseCorruption(e)) {
                throw new NfsTestException("Database corruption detected while inserting initial data", e);
            }
            throw new NfsTestException("Failed to insert initial data", e);
        }
    }
    
    /**
     * 插入单个用户
     */
    public void insertUser(String name, String email, int age, String data) {
        String insertSql = "INSERT INTO users (name, email, age, data) VALUES (?, ?, ?, ?)";
        
        try {
            jdbcTemplate.update(insertSql, name, email, age, data);
        } catch (Exception e) {
            if (NfsTestException.isDatabaseCorruption(e)) {
                throw new NfsTestException("Database corruption detected while inserting user", e);
            }
            throw new NfsTestException("Failed to insert user: " + name, e);
        }
    }
    
    /**
     * 随机条件查询用户
     */
    public List<Map<String, Object>> queryRandomUsers(String processName) {
        try {
            // 随机选择查询条件
            int queryType = random.nextInt(3);
            String sql;
            Object[] params;
            
            switch (queryType) {
                case 0:
                    // 按年龄范围查询
                    int minAge = 20 + random.nextInt(30);
                    int maxAge = minAge + random.nextInt(20);
                    sql = "SELECT * FROM users WHERE age BETWEEN ? AND ? LIMIT 10";
                    params = new Object[]{minAge, maxAge};
                    break;
                case 1:
                    // 按名称模糊查询
                    String namePattern = "User%";
                    sql = "SELECT * FROM users WHERE name LIKE ? LIMIT 10";
                    params = new Object[]{namePattern};
                    break;
                default:
                    // 随机获取用户
                    sql = "SELECT * FROM users ORDER BY RANDOM() LIMIT 5";
                    params = new Object[]{};
            }
            
            List<Map<String, Object>> results = jdbcTemplate.queryForList(sql, params);
            logger.debug("Process {} queried {} users", processName, results.size());
            return results;
            
        } catch (Exception e) {
            logger.error("Process {} failed to query users", processName, e);
            if (NfsTestException.isDatabaseCorruption(e)) {
                throw new NfsTestException("Database corruption detected while querying users", e);
            }
            throw new NfsTestException("Failed to query users", e);
        }
    }
    
    /**
     * 批量插入用户（模拟1秒的写入操作）
     */
    public int batchInsertUsers(String processName, int processId) {
        long startTime = System.currentTimeMillis();
        long endTime = startTime + 1000; // 1秒
        int insertCount = 0;
        
        logger.debug("Process {} starting batch insert for 1 second", processName);
        
        try {
            while (System.currentTimeMillis() < endTime) {
                insertCount++;
                String name = processName + "-User-" + insertCount;
                String email = processName.toLowerCase() + insertCount + "@test.com";
                int age = 18 + random.nextInt(60);
                String data = processName + "-Data-" + System.currentTimeMillis();
                
                insertUser(name, email, age, data);
                
                // 小延迟以模拟真实写入场景，减少延迟增加并发压力
                Thread.sleep(5 + random.nextInt(10));
            }
            
            logger.info("Process {} inserted {} users in 1 second", processName, insertCount);
            return insertCount;
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("Process {} batch insert was interrupted", processName);
            return insertCount;
        } catch (Exception e) {
            logger.error("Process {} batch insert failed after {} inserts", processName, insertCount, e);
            if (NfsTestException.isDatabaseCorruption(e)) {
                throw new NfsTestException("Database corruption detected during batch insert", e);
            }
            throw new NfsTestException("Batch insert failed for process " + processName, e);
        }
    }
    
    /**
     * 获取用户总数
     */
    public int getUserCount() {
        try {
            Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users", Integer.class);
            return count != null ? count : 0;
        } catch (Exception e) {
            logger.error("Failed to get user count", e);
            if (NfsTestException.isDatabaseCorruption(e)) {
                throw new NfsTestException("Database corruption detected while counting users", e);
            }
            throw new NfsTestException("Failed to get user count", e);
        }
    }
    
    /**
     * 检查数据库连接是否正常
     */
    public boolean isConnectionHealthy() {
        try {
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            return true;
        } catch (Exception e) {
            logger.warn("Database connection health check failed", e);
            return false;
        }
    }
}