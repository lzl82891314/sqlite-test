package com.grapecity.phoenix.sqlitetest;

/**
 * 自定义异常类，用于标识NFS测试过程中遇到的SQLite相关错误
 */
public class NfsTestException extends RuntimeException {
    
    public NfsTestException(String message) {
        super(message);
    }
    
    public NfsTestException(String message, Throwable cause) {
        super(message, cause);
    }
    
    /**
     * 检查异常是否为SQLite文件损坏相关
     */
    public static boolean isDatabaseCorruption(Throwable throwable) {
        if (throwable == null) {
            return false;
        }
        
        String message = throwable.getMessage();
        if (message == null) {
            return false;
        }
        
        message = message.toLowerCase();
        return message.contains("database disk image is malformed") ||
               message.contains("file is not a database") ||
               message.contains("database corruption") ||
               message.contains("sqlite_corrupt") ||
               message.contains("database is locked") ||
               message.contains("sqlite_busy");
    }
}