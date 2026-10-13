package com.linenexus.monitor.config;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.linenexus.monitor.tdengine.TdengineClient;

@Configuration
public class TdengineConfig {

    private static final Logger log = LoggerFactory.getLogger(TdengineConfig.class);

    @Bean(destroyMethod = "close")
    public TdengineClient tdengineClient(TdengineProperties props) {
        TdengineClient client = new TdengineClient(props);
        try {
            client.initSchema();
            log.info("TDengine ready: db={}, keepDays={}", props.getDatabase(), props.getKeepDays());
        } catch (Exception e) {
            log.warn("TDengine schema init deferred (service will retry on write): {}", e.getMessage());
        }
        return client;
    }

    /** 仅作连通性探测；业务走 TdengineClient */
    static Connection open(TdengineProperties props) throws SQLException {
        return DriverManager.getConnection(props.getJdbcUrl(), props.getUsername(), props.getPassword());
    }

    static void exec(Connection conn, String sql) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.execute(sql);
        }
    }
}
