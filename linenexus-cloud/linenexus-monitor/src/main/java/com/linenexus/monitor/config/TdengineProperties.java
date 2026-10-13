package com.linenexus.monitor.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "linenexus.tdengine")
public class TdengineProperties {

    private String jdbcUrl = "jdbc:TAOS-RS://127.0.0.1:6041";
    private String username = "root";
    private String password = "taosdata";
    private String database = "linenexus";
    private int keepDays = 30;

    public String getJdbcUrl() {
        return jdbcUrl;
    }

    public void setJdbcUrl(String jdbcUrl) {
        this.jdbcUrl = jdbcUrl;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getDatabase() {
        return database;
    }

    public void setDatabase(String database) {
        this.database = database;
    }

    public int getKeepDays() {
        return keepDays;
    }

    public void setKeepDays(int keepDays) {
        this.keepDays = keepDays;
    }
}
