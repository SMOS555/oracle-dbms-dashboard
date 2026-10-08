package com.dbms.dashboard.config;

import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;

@Configuration
public class DataSourceConfig {

    private static final Logger log = LoggerFactory.getLogger(DataSourceConfig.class);

    @Value("${spring.datasource.url:jdbc:oracle:thin:@localhost:1521/FREE}")
    private String dbUrl;

    @Value("${spring.datasource.username:system}")
    private String dbUser;

    @Value("${spring.datasource.password:safwaansmos@0510}")
    private String dbPass;

    @Value("${spring.datasource.driver-class-name:oracle.jdbc.OracleDriver}")
    private String driverClassName;

    @Bean
    @Primary
    public DataSource dataSource() {
        String effectiveUrl = System.getenv("DB_URL") != null ? System.getenv("DB_URL") : dbUrl;
        String effectiveUser = System.getenv("DB_USERNAME") != null ? System.getenv("DB_USERNAME") : dbUser;
        String effectivePass = System.getenv("DB_PASSWORD") != null ? System.getenv("DB_PASSWORD") : dbPass;

        log.info("Testing primary database connection to: {}", effectiveUrl);

        boolean oracleConnected = false;
        try {
            DriverManager.setLoginTimeout(2); // Fast 2-second timeout
            Class.forName(driverClassName);
            try (Connection conn = DriverManager.getConnection(effectiveUrl, effectiveUser, effectivePass)) {
                if (conn.isValid(2)) {
                    oracleConnected = true;
                    log.info("Successfully connected to primary Oracle database.");
                }
            }
        } catch (Exception e) {
            log.warn("Primary Oracle connection failed ({}: {}).", e.getClass().getSimpleName(), e.getMessage());
        }

        if (oracleConnected) {
            HikariDataSource ds = new HikariDataSource();
            ds.setJdbcUrl(effectiveUrl);
            ds.setUsername(effectiveUser);
            ds.setPassword(effectivePass);
            ds.setDriverClassName(driverClassName);
            ds.setMaximumPoolSize(10);
            ds.setPoolName("OraclePool");
            return ds;
        }

        log.warn("=========================================================================");
        log.warn("ORACLE DATABASE UNREACHABLE (Standard behavior on cloud free containers).");
        log.warn("ACTIVATING IN-MEMORY ORACLE-COMPATIBLE DATASOURCE WITH FULL 22-TABLE SEED.");
        log.warn("=========================================================================");

        HikariDataSource h2Ds = new HikariDataSource();
        h2Ds.setJdbcUrl("jdbc:h2:mem:dbms;MODE=Oracle;DEFAULT_NULL_ORDERINGS=HIGH;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=TRUE");
        h2Ds.setDriverClassName("org.h2.Driver");
        h2Ds.setUsername("sa");
        h2Ds.setPassword("");
        h2Ds.setPoolName("CloudResilientH2Pool");

        try {
            ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
            populator.addScript(new ClassPathResource("database/h2_schema_seed.sql"));
            populator.execute(h2Ds);
            log.info("In-memory cloud fallback schema and seed loaded successfully.");
        } catch (Exception e) {
            log.error("Failed to seed fallback schema: {}", e.getMessage(), e);
        }

        return h2Ds;
    }
}
