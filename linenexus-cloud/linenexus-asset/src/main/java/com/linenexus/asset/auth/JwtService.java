package com.linenexus.asset.auth;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import javax.crypto.SecretKey;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import org.springframework.stereotype.Component;

@Component
public class JwtService {

    private final AuthProperties properties;
    private final SecretKey key;

    public JwtService(AuthProperties properties) {
        this.properties = properties;
        this.key = Keys.hmacShaKeyFor(properties.getJwtSecret().getBytes(StandardCharsets.UTF_8));
    }

    public String issue(IamUserEntity user) {
        Instant now = Instant.now();
        Instant exp = now.plus(properties.getTokenTtlHours(), ChronoUnit.HOURS);
        return Jwts.builder()
                .subject(user.getUsername())
                .claim("uid", user.getId())
                .claim("tenantId", user.getTenantId())
                .claim("displayName", user.getDisplayName() == null ? user.getUsername() : user.getDisplayName())
                .claim("roles", user.getRoles())
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .signWith(key)
                .compact();
    }

    public Claims parse(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }

    public static List<String> splitRoles(String roles) {
        if (roles == null || roles.isBlank()) {
            return List.of();
        }
        return Arrays.stream(roles.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
