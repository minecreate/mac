package com.linenexus.asset.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "linenexus.auth")
public class AuthProperties {

    private String jwtSecret = "linenexus-dev-jwt-secret-change-me-32b";
    private int tokenTtlHours = 12;

    public String getJwtSecret() {
        return jwtSecret;
    }

    public void setJwtSecret(String jwtSecret) {
        this.jwtSecret = jwtSecret;
    }

    public int getTokenTtlHours() {
        return tokenTtlHours;
    }

    public void setTokenTtlHours(int tokenTtlHours) {
        this.tokenTtlHours = tokenTtlHours;
    }
}
