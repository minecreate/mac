package com.linenexus.monitor.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "linenexus.alarm")
public class AlarmProperties {

    /** 离线告警扫描周期（毫秒） */
    private long offlineScanMs = 30_000L;

    public long getOfflineScanMs() {
        return offlineScanMs;
    }

    public void setOfflineScanMs(long offlineScanMs) {
        this.offlineScanMs = offlineScanMs;
    }
}
