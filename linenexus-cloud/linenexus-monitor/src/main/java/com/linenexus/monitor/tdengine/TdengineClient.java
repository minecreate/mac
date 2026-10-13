package com.linenexus.monitor.tdengine;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.linenexus.monitor.config.TdengineProperties;

/**
 * TDengine REST JDBC 客户端：超级表 telemetry + 自动建子表。
 */
public class TdengineClient implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(TdengineClient.class);

    private final TdengineProperties props;
    private final ConcurrentHashMap<String, Boolean> knownTables = new ConcurrentHashMap<>();
    private volatile boolean schemaReady;

    public TdengineClient(TdengineProperties props) {
        this.props = props;
    }

    public synchronized void initSchema() throws SQLException {
        try (Connection conn = connectRoot()) {
            try (Statement st = conn.createStatement()) {
                st.execute("CREATE DATABASE IF NOT EXISTS " + props.getDatabase()
                        + " KEEP " + Math.max(1, props.getKeepDays())
                        + " PRECISION 'ms'");
            }
        }
        try (Connection conn = connectDb(); Statement st = conn.createStatement()) {
            st.execute("""
                    CREATE STABLE IF NOT EXISTS telemetry (
                      ts TIMESTAMP,
                      v_double DOUBLE,
                      v_str NCHAR(128),
                      quality NCHAR(16)
                    ) TAGS (
                      tenant_id NCHAR(64),
                      gateway_id NCHAR(64),
                      device_id NCHAR(64),
                      point_id NCHAR(64)
                    )
                    """);
        }
        schemaReady = true;
    }

    public void ensureSchema() {
        if (schemaReady) {
            return;
        }
        try {
            initSchema();
        } catch (SQLException e) {
            throw new IllegalStateException("TDengine unavailable: " + e.getMessage(), e);
        }
    }

    public void writePoint(
            String tenantId,
            String gatewayId,
            String deviceId,
            String pointId,
            Instant ts,
            Double vDouble,
            String vStr,
            String quality) {
        ensureSchema();
        String table = subTableName(tenantId, gatewayId, deviceId, pointId);
        ensureSubTable(table, tenantId, gatewayId, deviceId, pointId);
        String sql = "INSERT INTO " + table + " USING telemetry TAGS (?, ?, ?, ?) VALUES (?, ?, ?, ?)";
        try (Connection conn = connectDb(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, nullToEmpty(tenantId));
            ps.setString(2, nullToEmpty(gatewayId));
            ps.setString(3, nullToEmpty(deviceId));
            ps.setString(4, nullToEmpty(pointId));
            ps.setTimestamp(5, Timestamp.from(ts == null ? Instant.now() : ts));
            if (vDouble == null) {
                ps.setObject(6, null);
            } else {
                ps.setDouble(6, vDouble);
            }
            ps.setString(7, truncate(vStr, 128));
            ps.setString(8, truncate(quality == null || quality.isBlank() ? "good" : quality, 16));
            ps.executeUpdate();
        } catch (SQLException e) {
            knownTables.remove(table);
            throw new IllegalStateException("TDengine write failed: " + e.getMessage(), e);
        }
    }

    public List<HistorySample> queryHistory(
            String tenantId,
            String gatewayId,
            String deviceId,
            String pointId,
            Instant from,
            Instant to,
            Integer intervalSeconds) {
        ensureSchema();
        String table = subTableName(tenantId, gatewayId, deviceId, pointId);
        if (!tableExists(table)) {
            return List.of();
        }
        boolean downsample = intervalSeconds != null && intervalSeconds > 0;
        String sql;
        if (downsample) {
            sql = "SELECT _wstart AS ts, AVG(v_double) AS v_double, LAST(v_str) AS v_str, LAST(quality) AS quality "
                    + "FROM " + table + " WHERE ts >= ? AND ts <= ? "
                    + "INTERVAL(" + intervalSeconds + "s) FILL(NONE)";
        } else {
            sql = "SELECT ts, v_double, v_str, quality FROM " + table
                    + " WHERE ts >= ? AND ts <= ? ORDER BY ts ASC";
        }
        List<HistorySample> out = new ArrayList<>();
        try (Connection conn = connectDb(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setTimestamp(1, Timestamp.from(from));
            ps.setTimestamp(2, Timestamp.from(to));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Timestamp t = rs.getTimestamp("ts");
                    Double vd = (Double) rs.getObject("v_double");
                    String vs = rs.getString("v_str");
                    String q = rs.getString("quality");
                    out.add(new HistorySample(
                            t == null ? null : t.toInstant().toString(),
                            vd,
                            vs,
                            q));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("TDengine query failed: " + e.getMessage(), e);
        }
        return out;
    }

    private void ensureSubTable(String table, String tenantId, String gatewayId, String deviceId, String pointId) {
        if (knownTables.containsKey(table)) {
            return;
        }
        String sql = "CREATE TABLE IF NOT EXISTS " + table
                + " USING telemetry TAGS ('" + esc(tenantId) + "','" + esc(gatewayId)
                + "','" + esc(deviceId) + "','" + esc(pointId) + "')";
        try (Connection conn = connectDb(); Statement st = conn.createStatement()) {
            st.execute(sql);
            knownTables.put(table, Boolean.TRUE);
        } catch (SQLException e) {
            log.warn("create subtable {}: {}", table, e.getMessage());
            throw new IllegalStateException("create subtable failed: " + e.getMessage(), e);
        }
    }

    private boolean tableExists(String table) {
        if (knownTables.containsKey(table)) {
            return true;
        }
        try (Connection conn = connectDb(); Statement st = conn.createStatement();
                ResultSet rs = st.executeQuery("SHOW TABLES LIKE '" + esc(table) + "'")) {
            if (rs.next()) {
                knownTables.put(table, Boolean.TRUE);
                return true;
            }
        } catch (SQLException e) {
            log.debug("show tables {}: {}", table, e.getMessage());
        }
        return false;
    }

    static String subTableName(String tenantId, String gatewayId, String deviceId, String pointId) {
        String raw = "t_" + sanitize(tenantId) + "_" + sanitize(gatewayId)
                + "_" + sanitize(deviceId) + "_" + sanitize(pointId);
        if (raw.length() > 190) {
            raw = raw.substring(0, 190);
        }
        return raw;
    }

    private static String sanitize(String s) {
        if (s == null || s.isBlank()) {
            return "x";
        }
        return s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "_");
    }

    private static String esc(String s) {
        return nullToEmpty(s).replace("'", "''");
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }

    private Connection connectRoot() throws SQLException {
        return DriverManager.getConnection(props.getJdbcUrl(), props.getUsername(), props.getPassword());
    }

    private Connection connectDb() throws SQLException {
        String url = props.getJdbcUrl();
        if (!url.contains("/" + props.getDatabase())) {
            // jdbc:TAOS-RS://host:6041 → jdbc:TAOS-RS://host:6041/linenexus
            if (url.endsWith("/")) {
                url = url + props.getDatabase();
            } else {
                url = url + "/" + props.getDatabase();
            }
        }
        return DriverManager.getConnection(url, props.getUsername(), props.getPassword());
    }

    @Override
    public void close() {
        // DriverManager connections are short-lived; nothing pooled here for MVP.
    }

    public record HistorySample(String ts, Double vDouble, String vStr, String quality) {}
}
