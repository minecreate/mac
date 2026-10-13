package com.linenexus.monitor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.linenexus.monitor.config.AlarmProperties;
import com.linenexus.monitor.config.MqttProperties;
import com.linenexus.monitor.config.TdengineProperties;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties({MqttProperties.class, TdengineProperties.class, AlarmProperties.class})
public class MonitorApplication {

    public static final String HDR_TENANT = "X-Tenant-Id";
    public static final String HDR_USER = "X-User-Id";
    public static final String HDR_USER_NAME = "X-User-Name";
    public static final String HDR_ROLES = "X-Roles";

    public static void main(String[] args) {
        SpringApplication.run(MonitorApplication.class, args);
    }
}
