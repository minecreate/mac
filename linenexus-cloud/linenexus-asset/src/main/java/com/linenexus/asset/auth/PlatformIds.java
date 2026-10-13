package com.linenexus.asset.auth;

/** 平台运营面常量：与租户面隔离。 */
public final class PlatformIds {

    /** 平台超管所在虚拟租户，禁止出现在业务数据中。 */
    public static final String PLATFORM_TENANT_ID = "__platform__";

    public static final String ROLE_PLATFORM_ADMIN = "platform_admin";

    private PlatformIds() {}

    public static boolean isPlatformTenant(String tenantId) {
        return PLATFORM_TENANT_ID.equals(tenantId);
    }

    public static boolean isPlatformAdmin(Iterable<String> roles) {
        if (roles == null) {
            return false;
        }
        for (String r : roles) {
            if (ROLE_PLATFORM_ADMIN.equals(r)) {
                return true;
            }
        }
        return false;
    }
}
