package com.factory.central.access;

public final class AccessErrorCode {

    public static final String UNAUTHENTICATED = "UNAUTHENTICATED";
    public static final String FORBIDDEN = "FORBIDDEN";
    public static final String ACCESS_CHECK_FAILED = "ACCESS_CHECK_FAILED";
    public static final String VALIDATION_FAILED = "VALIDATION_FAILED";
    public static final String ROLE_NOT_ASSIGNABLE = "ROLE_NOT_ASSIGNABLE";
    public static final String INVALID_CREDENTIALS = "INVALID_CREDENTIALS";
    public static final String ACCOUNT_DISABLED = "ACCOUNT_DISABLED";
    public static final String EDGE_SITE_FORBIDDEN = "EDGE_SITE_FORBIDDEN";
    public static final String PASSWORD_REQUIRED = "PASSWORD_REQUIRED";
    public static final String SITE_NOT_FOUND = "SITE_NOT_FOUND";
    public static final String CREDENTIAL_NOT_FOUND = "CREDENTIAL_NOT_FOUND";

    private AccessErrorCode() {}
}
