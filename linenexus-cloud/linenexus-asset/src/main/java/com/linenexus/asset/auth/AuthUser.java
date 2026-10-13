package com.linenexus.asset.auth;

import java.util.List;

public record AuthUser(
        String id, String username, String displayName, String avatar, String tenantId, List<String> roles) {}
