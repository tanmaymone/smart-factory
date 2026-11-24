package com.factory.central.access;

public final class AccessErrorCode {

    public static final String UNAUTHENTICATED = "UNAUTHENTICATED";
    public static final String FORBIDDEN = "FORBIDDEN";
    public static final String ACCESS_CHECK_FAILED = "ACCESS_CHECK_FAILED";
    public static final String VALIDATION_FAILED = "VALIDATION_FAILED";
    public static final String ROLE_NOT_ASSIGNABLE = "ROLE_NOT_ASSIGNABLE";

    private AccessErrorCode() {}
}
