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
        String url = "jdbc:sqlite:" + dbFile.getAbsolutePath() + 
            "?busy_timeout=5000&journal_mode=WAL&synchronous=NORMAL&cache_size=10000";
        dataSource.setUrl(url);
        
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
}