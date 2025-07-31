package com.grapecity.phoenix.sqlitetest;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.io.File;

@Configuration
public class DatabaseConfig {

    @Bean
    public DataSource dataSource() {
        return createDataSource(getDefaultDatabasePath());
    }
    
    public static DataSource createDataSource(String databasePath) {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.sqlite.JDBC");
        
        File dbFile = new File(databasePath);
        // 激进的SQLite配置以最大化NFS并发问题的触发概率
        // 使用URI格式确保跨平台兼容性
        String dbUrl = buildSqliteUrl(dbFile.getAbsolutePath());
        dataSource.setUrl(dbUrl);
        
        return dataSource;
    }
    
    public static String getDefaultDatabasePath() {
        return "nfs-test.db";
    }

    @Bean
    public JdbcTemplate jdbcTemplate(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }
    
    public static JdbcTemplate createJdbcTemplate(String databasePath) {
        return new JdbcTemplate(createDataSource(databasePath));
    }
    
    /**
     * 构建跨平台兼容的SQLite JDBC URL
     * SQLite JDBC驱动不支持URL参数，所以只返回基础URL
     * 所有配置将通过PRAGMA语句在连接后设置
     */
    private static String buildSqliteUrl(String absolutePath) {
        try {
            // 使用URI来处理跨平台路径
            java.net.URI uri = new File(absolutePath).toURI();
            String fileUrl = uri.toString();
            
            // 构建基础SQLite JDBC URL（不含参数）
            if (fileUrl.startsWith("file:")) {
                // 移除file:前缀，但保持正确的路径格式
                String path = fileUrl.substring(5);
                return "jdbc:sqlite:" + path;
            } else {
                return "jdbc:sqlite:" + absolutePath;
            }
            
        } catch (Exception e) {
            // 回退到简单的路径处理
            return "jdbc:sqlite:" + absolutePath;
        }
    }
}