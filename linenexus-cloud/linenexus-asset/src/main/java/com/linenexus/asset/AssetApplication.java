package com.linenexus.asset;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.linenexus.asset.auth.AuthProperties;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(AuthProperties.class)
public class AssetApplication {

    public static void main(String[] args) {
        SpringApplication.run(AssetApplication.class, args);
    }
}
