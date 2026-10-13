package com.linenexus.asset.auth;

public record LoginResponse(String token, String tokenType, AuthUser user) {}
