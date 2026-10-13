package com.linenexus.asset.auth;

public record LoginRequest(String username, String password, String tenantId) {}
