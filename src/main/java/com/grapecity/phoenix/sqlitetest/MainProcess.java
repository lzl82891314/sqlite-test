package com.grapecity.phoenix.sqlitetest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * 主进程类 - 协调和监控多个子进程
 * 负责初始化数据库、启动子进程、监控异常
 */
public class MainProcess {
    
    private static final Logger logger = LoggerFactory.getLogger(MainProcess.class);
    private static final int DEFAULT_CHILD_PROCESS_COUNT = 2;
    private static final int DEFAULT_MAX_CYCLES = 100;
    
    public static void main(String[] args) {
        int childProcessCount = DEFAULT_CHILD_PROCESS_COUNT;
        String databasePath = null;
        int maxCycles = DEFAULT_MAX_CYCLES;
        int runTimeSeconds = -1; // -1表示不按时间限制
        
        // 显示使用说明
        if (args.length > 0 && (args[0].equals("-h") || args[0].equals("--help"))) {
            printUsage();
            return;
        }
        
        // 解析命令行参数
        if (args.length > 0) {
            try {
                childProcessCount = Integer.parseInt(args[0]);
                if (childProcessCount < 1) {
                    logger.warn("Child process count must be at least 1, using default: {}", DEFAULT_CHILD_PROCESS_COUNT);
                    childProcessCount = DEFAULT_CHILD_PROCESS_COUNT;
                }
            } catch (NumberFormatException e) {
                logger.warn("Invalid child process count: {}, using default: {}", args[0], DEFAULT_CHILD_PROCESS_COUNT);
            }
        }
        
        if (args.length > 1) {
            databasePath = args[1];
        } else {
            databasePath = DatabaseConfig.getDefaultDatabasePath();
        }
        
        if (args.length > 2) {
            try {
                maxCycles = Integer.parseInt(args[2]);
                if (maxCycles == -1) {
                    logger.info("Continuous mode enabled (unlimited cycles)");
                } else if (maxCycles == 0 && args.length > 3) {
                    // 第3个参数为0表示按时间运行，第4个参数是运行时间（秒）
                    runTimeSeconds = Integer.parseInt(args[3]);
                    maxCycles = Integer.MAX_VALUE;
                    logger.info("Time-based mode enabled: {} seconds", runTimeSeconds);
                } else if (maxCycles < 1 && maxCycles != -1) {
                    logger.warn("Invalid max cycles: {}, using default: {}", args[2], DEFAULT_MAX_CYCLES);
                    maxCycles = DEFAULT_MAX_CYCLES;
                }
            } catch (NumberFormatException e) {
                logger.warn("Invalid max cycles: {}, using default: {}", args[2], DEFAULT_MAX_CYCLES);
            }
        }
        
        MainProcess mainProcess = new MainProcess();
        mainProcess.runTest(childProcessCount, databasePath, maxCycles, runTimeSeconds);
    }
    
    private static void printUsage() {
        System.out.println("SQLite NFS Multi-Process Test Tool");
        System.out.println();
        System.out.println("Usage:");
        System.out.println("  java -jar sqlite-nfs-test.jar [processes] [database] [cycles] [time]");
        System.out.println();
        System.out.println("Parameters:");
        System.out.println("  processes  - Number of child processes (default: 4)");
        System.out.println("  database   - Database file path (default: nfs-test.db)");
        System.out.println("  cycles     - Max cycles per process (default: 100, -1 for unlimited)");
        System.out.println("  time       - Run time in seconds (only when cycles=0)");
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  java -jar sqlite-nfs-test.jar");
        System.out.println("  java -jar sqlite-nfs-test.jar 8");
        System.out.println("  java -jar sqlite-nfs-test.jar 4 /mnt/nfs/test.db");
        System.out.println("  java -jar sqlite-nfs-test.jar 4 /mnt/nfs/test.db -1    # Unlimited");
        System.out.println("  java -jar sqlite-nfs-test.jar 4 /mnt/nfs/test.db 0 600 # Run 10 minutes");
    }
    
    public void runTest(int childProcessCount, String databasePath, int maxCycles, int runTimeSeconds) {
        logger.info("Starting SQLite NFS multi-process test");
        if (runTimeSeconds > 0) {
            logger.info("Child processes: {}, Database: {}, Run time: {} seconds", 
                        childProcessCount, databasePath, runTimeSeconds);
        } else if (maxCycles == Integer.MAX_VALUE) {
            logger.info("Child processes: {}, Database: {}, Mode: Unlimited cycles", 
                        childProcessCount, databasePath);
        } else {
            logger.info("Child processes: {}, Database: {}, Max cycles: {}", 
                        childProcessCount, databasePath, maxCycles);
        }
        
        try {
            // 1. 初始化数据库
            initializeDatabase(databasePath);
            
            // 2. 启动子进程
            List<CompletableFuture<Integer>> childProcesses = startChildProcesses(
                childProcessCount, databasePath, maxCycles, runTimeSeconds);
            
            // 3. 监控子进程
            monitorChildProcesses(childProcesses, databasePath, runTimeSeconds);
            
            // 4. 输出最终结果
            printFinalResults(databasePath);
            
            logger.info("SQLite NFS multi-process test completed successfully");
            
        } catch (Exception e) {
            logger.error("Multi-process test failed", e);
            System.exit(1);
        }
    }
    
    /**
     * 初始化数据库：创建文件、创建表、插入初始数据
     */
    private void initializeDatabase(String databasePath) {
        logger.info("Initializing database: {}", databasePath);
        
        try {
            // 删除已存在的数据库文件
            File dbFile = new File(databasePath);
            if (dbFile.exists()) {
                logger.info("Removing existing database file: {}", databasePath);
                if (!dbFile.delete()) {
                    logger.warn("Failed to delete existing database file");
                }
            }
            
            // 创建数据库连接
            JdbcTemplate jdbcTemplate = DatabaseConfig.createJdbcTemplate(databasePath);
            UserRepository userRepository = new UserRepository(jdbcTemplate);
            
            // 创建表
            userRepository.createUsersTable();
            
            // 插入初始数据
            userRepository.insertInitialData(20);
            
            int userCount = userRepository.getUserCount();
            logger.info("Database initialized successfully with {} users", userCount);
            
        } catch (Exception e) {
            logger.error("Failed to initialize database", e);
            throw new NfsTestException("Database initialization failed", e);
        }
    }
    
    /**
     * 启动多个子进程
     */
    private List<CompletableFuture<Integer>> startChildProcesses(
            int childProcessCount, String databasePath, int maxCycles, int runTimeSeconds) {
        
        logger.info("Starting {} child processes", childProcessCount);
        List<CompletableFuture<Integer>> futures = new ArrayList<>();
        
        for (int i = 1; i <= childProcessCount; i++) {
            String processName = "ChildProcess-" + i;
            CompletableFuture<Integer> future = startChildProcess(processName, databasePath, maxCycles, runTimeSeconds);
            futures.add(future);
            logger.info("Started child process: {}", processName);
        }
        
        return futures;
    }
    
    /**
     * 启动单个子进程
     */
    private CompletableFuture<Integer> startChildProcess(String processName, String databasePath, int maxCycles, int runTimeSeconds) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // 构建Java命令
                String javaHome = System.getProperty("java.home");
                String classpath = System.getProperty("java.class.path");
                String javaExecutable = javaHome + "/bin/java";
                
                List<String> command = new ArrayList<>();
                command.add(javaExecutable);
                command.add("-cp");
                command.add(classpath);
                command.add("com.grapecity.phoenix.sqlitetest.ChildProcess");
                command.add(processName);
                command.add(databasePath);
                command.add(String.valueOf(maxCycles));
                if (runTimeSeconds > 0) {
                    command.add(String.valueOf(runTimeSeconds));
                }
                
                // 启动进程
                ProcessBuilder processBuilder = new ProcessBuilder(command);
                processBuilder.redirectErrorStream(true);
                Process process = processBuilder.start();
                
                // 读取进程输出
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        logger.info("[{}] {}", processName, line);
                    }
                }
                
                // 等待进程结束
                int exitCode = process.waitFor();
                
                if (exitCode == 0) {
                    logger.info("Child process {} completed successfully", processName);
                } else if (exitCode == 2) {
                    logger.error("Child process {} detected database corruption (exit code: {})", 
                                processName, exitCode);
                } else {
                    logger.error("Child process {} failed with exit code: {}", processName, exitCode);
                }
                
                return exitCode;
                
            } catch (IOException e) {
                logger.error("Failed to start child process: {}", processName, e);
                return -1;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.error("Child process {} was interrupted", processName, e);
                return -1;
            }
        });
    }
    
    /**
     * 监控所有子进程
     */
    private void monitorChildProcesses(List<CompletableFuture<Integer>> childProcesses, String databasePath, int runTimeSeconds) {
        logger.info("Monitoring {} child processes", childProcesses.size());
        
        try {
            // 启动实时统计输出线程
            CompletableFuture<Void> statisticsThread = startStatisticsThread(databasePath, runTimeSeconds);
            
            // 等待所有子进程完成
            CompletableFuture<Void> allOf = CompletableFuture.allOf(
                childProcesses.toArray(new CompletableFuture[0]));
            
            int timeoutMinutes = runTimeSeconds > 0 ? (runTimeSeconds / 60) + 5 : 30;
            allOf.get(timeoutMinutes, TimeUnit.MINUTES);
            
            // 检查所有进程的退出码
            boolean allSuccessful = true;
            boolean databaseCorruption = false;
            
            for (int i = 0; i < childProcesses.size(); i++) {
                int exitCode = childProcesses.get(i).get();
                String processName = "ChildProcess-" + (i + 1);
                
                if (exitCode == 2) {
                    logger.error("Database corruption detected by {}", processName);
                    databaseCorruption = true;
                    allSuccessful = false;
                } else if (exitCode != 0) {
                    logger.error("Process {} failed with exit code: {}", processName, exitCode);
                    allSuccessful = false;
                }
            }
            
            if (databaseCorruption) {
                throw new NfsTestException("Database corruption detected by child processes");
            } else if (!allSuccessful) {
                throw new NfsTestException("One or more child processes failed");
            }
            
            logger.info("All child processes completed successfully");
            
        } catch (Exception e) {
            logger.error("Error monitoring child processes", e);
            
            // 强制终止所有子进程
            logger.warn("Attempting to terminate all child processes");
            for (CompletableFuture<Integer> future : childProcesses) {
                future.cancel(true);
            }
            
            if (e instanceof NfsTestException) {
                throw (NfsTestException) e;
            } else {
                throw new NfsTestException("Child process monitoring failed", e);
            }
        }
    }
    
    /**
     * 输出最终测试结果
     */
    private void printFinalResults(String databasePath) {
        try {
            JdbcTemplate jdbcTemplate = DatabaseConfig.createJdbcTemplate(databasePath);
            UserRepository userRepository = new UserRepository(jdbcTemplate);
            
            int finalUserCount = userRepository.getUserCount();
            
            logger.info("=== Test Results ===");
            logger.info("Database file: {}", databasePath);
            logger.info("Final user count: {}", finalUserCount);
            logger.info("Database file size: {} bytes", new File(databasePath).length());
            
            // 检查数据库完整性
            if (userRepository.isConnectionHealthy()) {
                logger.info("Database integrity check: PASSED");
            } else {
                logger.warn("Database integrity check: FAILED");
            }
            
            logger.info("=== Test Completed ===");
            
        } catch (Exception e) {
            logger.error("Failed to generate final results", e);
            logger.warn("This may indicate database corruption");
        }
    }
    
    /**
     * 启动实时统计输出线程
     */
    private CompletableFuture<Void> startStatisticsThread(String databasePath, int runTimeSeconds) {
        return CompletableFuture.runAsync(() -> {
            try {
                JdbcTemplate jdbcTemplate = DatabaseConfig.createJdbcTemplate(databasePath);
                UserRepository userRepository = new UserRepository(jdbcTemplate);
                
                long startTime = System.currentTimeMillis();
                int lastUserCount = userRepository.getUserCount();
                
                while (!Thread.currentThread().isInterrupted()) {
                    Thread.sleep(30000); // 每30秒输出一次统计
                    
                    try {
                        int currentUserCount = userRepository.getUserCount();
                        long elapsedSeconds = (System.currentTimeMillis() - startTime) / 1000;
                        int insertedCount = currentUserCount - lastUserCount;
                        
                        logger.info("=== Progress Report ===");
                        logger.info("Elapsed time: {} seconds", elapsedSeconds);
                        logger.info("Total users: {} (+{} in last 30s)", currentUserCount, insertedCount);
                        logger.info("Average rate: {:.1f} inserts/second", (double) insertedCount / 30);
                        
                        if (runTimeSeconds > 0) {
                            int remainingSeconds = runTimeSeconds - (int) elapsedSeconds;
                            if (remainingSeconds > 0) {
                                logger.info("Remaining time: {} seconds", remainingSeconds);
                            }
                        }
                        
                        lastUserCount = currentUserCount;
                        
                    } catch (Exception e) {
                        logger.warn("Failed to get statistics", e);
                    }
                }
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.debug("Statistics thread interrupted");
            } catch (Exception e) {
                logger.error("Statistics thread failed", e);
            }
        });
    }
}