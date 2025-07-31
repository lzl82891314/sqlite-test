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
     * 创建users表并应用激进的SQLite配置
     */
    public void createUsersTable() {
        try {
            // 应用激进的SQLite PRAGMA设置以增加NFS问题触发概率
            applyAggressivePragmaSettings();
            
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
            
            jdbcTemplate.execute(createTableSql);
            logger.info("Users table created or already exists with aggressive SQLite settings");
        } catch (Exception e) {
            logger.error("Failed to create users table", e);
            if (NfsTestException.isDatabaseCorruption(e)) {
                throw new NfsTestException("Database corruption detected while creating table", e);
            }
            throw new NfsTestException("Failed to create users table", e);
        }
    }
    
    /**
     * 应用激进的SQLite PRAGMA设置以最大化NFS并发问题触发概率
     */
    private void applyAggressivePragmaSettings() {
        try {
            // 确保WAL模式
            jdbcTemplate.execute("PRAGMA journal_mode = WAL");
            
            // 关闭同步，最大风险但最高性能
            jdbcTemplate.execute("PRAGMA synchronous = OFF");
            
            // 减少锁等待时间
            jdbcTemplate.execute("PRAGMA busy_timeout = 500");
            
            // 增加内存缓存
            jdbcTemplate.execute("PRAGMA cache_size = -50000"); // 50MB
            
            // 内存存储临时文件
            jdbcTemplate.execute("PRAGMA temp_store = MEMORY");
            
            // 启用内存映射I/O
            jdbcTemplate.execute("PRAGMA mmap_size = 536870912"); // 512MB
            
            // 激进的WAL自动检查点
            jdbcTemplate.execute("PRAGMA wal_autocheckpoint = 100");
            
            // 优化页面大小
            jdbcTemplate.execute("PRAGMA page_size = 4096");
            
            // 启用增量清理
            jdbcTemplate.execute("PRAGMA auto_vacuum = INCREMENTAL");
            
            logger.info("Applied aggressive SQLite PRAGMA settings for NFS stress testing");
            
        } catch (Exception e) {
            logger.warn("Failed to apply some PRAGMA settings, continuing anyway", e);
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
     * 批量插入用户（模拟5秒的密集写入操作）
     */
    public int batchInsertUsers(String processName, int processId) {
        long startTime = System.currentTimeMillis();
        long endTime = startTime + 5000; // 5秒
        int insertCount = 0;
        
        logger.debug("Process {} starting batch insert for 5 seconds", processName);
        
        try {
            while (System.currentTimeMillis() < endTime) {
                insertCount++;
                String name = processName + "-User-" + insertCount;
                String email = processName.toLowerCase() + insertCount + "@test.com";
                int age = 18 + random.nextInt(60);
                String data = processName + "-Data-" + System.currentTimeMillis();
                
                insertUser(name, email, age, data);
                
                // 极小延迟以最大化并发压力和竞态条件
                Thread.sleep(1 + random.nextInt(3));
            }
            
            logger.info("Process {} inserted {} users in 5 seconds", processName, insertCount);
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
    
    /**
     * 执行SQLite PRAGMA integrity_check
     */
    public boolean checkDatabaseIntegrity() {
        try {
            String result = jdbcTemplate.queryForObject("PRAGMA integrity_check", String.class);
            boolean isOk = "ok".equalsIgnoreCase(result);
            
            if (isOk) {
                logger.debug("Database integrity check: PASSED");
            } else {
                logger.error("Database integrity check FAILED: {}", result);
            }
            
            return isOk;
            
        } catch (Exception e) {
            logger.error("Failed to perform database integrity check", e);
            return false;
        }
    }
    
    /**
     * 检查数据一致性：验证是否存在重复的主键或异常数据
     */
    public boolean checkDataConsistency() {
        try {
            // 检查主键唯一性
            Integer duplicateIds = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM (SELECT id, COUNT(*) as cnt FROM users GROUP BY id HAVING cnt > 1)", 
                Integer.class);
            
            if (duplicateIds != null && duplicateIds > 0) {
                logger.error("Data consistency check FAILED: Found {} duplicate primary keys", duplicateIds);
                return false;
            }
            
            // 检查是否有NULL的必需字段
            Integer nullNames = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE name IS NULL", Integer.class);
            
            if (nullNames != null && nullNames > 0) {
                logger.error("Data consistency check FAILED: Found {} records with NULL names", nullNames);
                return false;
            }
            
            // 检查自增ID的连续性（允许少量缺失，但不应该有大的跳跃）
            Integer minId = jdbcTemplate.queryForObject("SELECT MIN(id) FROM users", Integer.class);
            Integer maxId = jdbcTemplate.queryForObject("SELECT MAX(id) FROM users", Integer.class);
            Integer totalCount = getUserCount();
            
            if (minId != null && maxId != null && totalCount > 0) {
                int expectedRange = maxId - minId + 1;
                double continuityRate = (double) totalCount / expectedRange;
                
                // 如果连续性低于70%，可能存在异常（考虑到并发删除等正常情况）
                if (continuityRate < 0.7) {
                    logger.warn("Data consistency warning: ID continuity rate is {:.2f}% (min={}, max={}, count={})", 
                               continuityRate * 100, minId, maxId, totalCount);
                }
            }
            
            logger.debug("Data consistency check: PASSED");
            return true;
            
        } catch (Exception e) {
            logger.error("Failed to perform data consistency check", e);
            return false;
        }
    }
    
    /**
     * 综合数据库健康检查
     */
    public boolean performComprehensiveHealthCheck() {
        logger.info("Performing comprehensive database health check...");
        
        boolean connectionOk = isConnectionHealthy();
        boolean integrityOk = checkDatabaseIntegrity();
        boolean consistencyOk = checkDataConsistency();
        
        boolean overallHealthy = connectionOk && integrityOk && consistencyOk;
        
        if (overallHealthy) {
            logger.info("Comprehensive health check: PASSED");
        } else {
            logger.error("Comprehensive health check: FAILED (connection: {}, integrity: {}, consistency: {})", 
                        connectionOk, integrityOk, consistencyOk);
        }
        
        return overallHealthy;
    }
}