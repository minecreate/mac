package com.linenexus.asset.audit;

public final class AuditActions {

    public static final String CAT_AUTH = "auth";
    public static final String CAT_CONTROL = "control";
    public static final String CAT_ALARM = "alarm";
    public static final String CAT_CONFIG = "config";

    public static final String LOGIN = "login";
    public static final String LOGIN_FAIL = "login_fail";
    public static final String PASSWORD_CHANGE = "password_change";
    public static final String PROFILE_UPDATE = "profile_update";
    public static final String POINT_WRITE = "point_write";
    public static final String ALARM_ACK = "alarm_ack";
    public static final String HMI_PUBLISH = "hmi_publish";
    public static final String GATEWAY_REGISTER = "gateway_register";
    public static final String GATEWAY_DELETE = "gateway_delete";

    public static final String SUCCESS = "success";
    public static final String FAILURE = "failure";

    private AuditActions() {}
}
