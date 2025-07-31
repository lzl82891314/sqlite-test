package com.grapecity.phoenix.sqlitetest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Random;
import java.io.File;

/**
 * 子进程类 - 独立的Java进程入口
 * 负责执行并发的SQLite读写操作
 */
public class ChildProcess {
    
    private static final Logger logger = LoggerFactory.getLogger(ChildProcess.class);
    private final Random random = new Random();
    
    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: ChildProcess <processName> <databasePath> [maxCycles] [runTimeSeconds] [syncFilePath]");
            System.exit(1);
        }
        
        String processName = args[0];
        String databasePath = args[1];
        int maxCycles = args.length > 2 ? Integer.parseInt(args[2]) : Integer.MAX_VALUE;
        int runTimeSeconds = args.length > 3 ? Integer.parseInt(args[3]) : -1;
        String syncFilePath = args.length > 4 ? args[4] : null;
        
        ChildProcess childProcess = new ChildProcess();
        childProcess.runProcess(processName, databasePath, maxCycles, runTimeSeconds, syncFilePath);
    }
    
    public void runProcess(String processName, String databasePath, int maxCycles, int runTimeSeconds, String syncFilePath) {
        if (runTimeSeconds > 0) {
            logger.info("Child process {} starting with database: {}, run time: {} seconds", 
                       processName, databasePath, runTimeSeconds);
        } else if (maxCycles == Integer.MAX_VALUE) {
            logger.info("Child process {} starting with database: {}, unlimited cycles", 
                       processName, databasePath);
        } else {
            logger.info("Child process {} starting with database: {}, max cycles: {}", 
                       processName, databasePath, maxCycles);
        }
        
        try {
            // 创建数据库连接
            JdbcTemplate jdbcTemplate = DatabaseConfig.createJdbcTemplate(databasePath);
            UserRepository userRepository = new UserRepository(jdbcTemplate);
            
            // 检查数据库连接
            if (!userRepository.isConnectionHealthy()) {
                throw new NfsTestException("Database connection is not healthy");
            }
            
            logger.info("Process {} connected to database successfully", processName);
            
            // 等待同步信号
            if (syncFilePath != null) {
                waitForStartSignal(processName, syncFilePath);
            }
            
            // 执行工作循环
            int cycleCount = 0;
            long startTime = System.currentTimeMillis();
            long endTime = runTimeSeconds > 0 ? startTime + (runTimeSeconds * 1000L) : Long.MAX_VALUE;
            
            while (cycleCount < maxCycles && System.currentTimeMillis() < endTime) {
                cycleCount++;
                
                try {
                    performWorkCycle(userRepository, processName, cycleCount);
                } catch (Exception e) {
                    logger.error("Process {} failed in cycle {}", processName, cycleCount, e);
                    
                    // 检查是否为数据库损坏
                    if (NfsTestException.isDatabaseCorruption(e)) {
                        logger.error("Process {} detected database corruption, exiting", processName);
                        System.exit(2); // 特殊退出码表示数据库损坏
                    }
                    
                    // 其他异常也导致退出
                    logger.error("Process {} encountered fatal error, exiting", processName);
                    System.exit(1);
                }
                
                // 如果还有下一个循环且未超时，等待一定时间
                if ((cycleCount < maxCycles || maxCycles == Integer.MAX_VALUE) && 
                    System.currentTimeMillis() < endTime) {
                    logger.debug("Process {} completed cycle {}, waiting before next cycle", processName, cycleCount);
                    Thread.sleep(50 + new Random().nextInt(50)); // 减少等待时间到50-100ms，大幅增加并发压力
                }
            }
            
            logger.info("Process {} completed all {} cycles successfully", processName, cycleCount);
            
        } catch (Exception e) {
            logger.error("Process {} failed during initialization or execution", processName, e);
            System.exit(1);
        }
        
        logger.info("Process {} exiting normally", processName);
    }
    
    /**
     * 执行一个工作周期：查询 -> 密集插入5秒 -> 提交
     */
    private void performWorkCycle(UserRepository userRepository, String processName, int cycleCount) 
            throws InterruptedException {
        
        logger.debug("Process {} starting work cycle {}", processName, cycleCount);
        
        // 1. 随机条件查询users表
        logger.debug("Process {} performing random query", processName);
        userRepository.queryRandomUsers(processName);
        
        // 2. 循环插入数据5秒钟
        logger.debug("Process {} starting batch insert", processName);
        int insertedCount = userRepository.batchInsertUsers(processName, cycleCount);
        
        // 3. 事务会自动提交（JdbcTemplate默认自动提交）
        logger.debug("Process {} completed cycle {}, inserted {} records", 
                     processName, cycleCount, insertedCount);
        
        // 4. 验证数据库状态
        if (!userRepository.isConnectionHealthy()) {
            throw new NfsTestException("Database connection became unhealthy after cycle " + cycleCount);
        }
        
        // 5. 每10个周期执行一次完整性检查
        if (cycleCount % 10 == 0) {
            if (!userRepository.checkDatabaseIntegrity()) {
                throw new NfsTestException("Database integrity check failed after cycle " + cycleCount);
            }
        }
    }
    
    /**
     * 等待主进程的同步启动信号
     */
    private void waitForStartSignal(String processName, String syncFilePath) {
        try {
            logger.info("Process {} waiting for synchronization signal...", processName);
            
            File syncFile = new File(syncFilePath);
            long timeout = System.currentTimeMillis() + 30000; // 30秒超时
            
            while (System.currentTimeMillis() < timeout) {
                if (syncFile.exists() && syncFile.length() > 0) {
                    try (java.io.BufferedReader reader = new java.io.BufferedReader(
                            new java.io.FileReader(syncFile))) {
                        String signal = reader.readLine();
                        if (signal != null && signal.startsWith("START:")) {
                            logger.info("Process {} received START signal, beginning synchronized work", processName);
                            return;
                        }
                    }
                }
                
                // 短暂等待后重试
                Thread.sleep(100);
            }
            
            logger.warn("Process {} timed out waiting for start signal, proceeding anyway", processName);
            
        } catch (Exception e) {
            logger.error("Process {} failed to wait for start signal", processName, e);
            // 继续执行，不让同步失败阻止测试
        }
    }
}