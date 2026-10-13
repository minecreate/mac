package com.linenexus.asset.tenant;

/** 开通租户；可同时创建首个租户管理员。 */
public record TenantCreateRequest(
        String id,
        String name,
        String remark,
        String adminUsername,
        String adminPassword,
        String adminDisplayName) {}
