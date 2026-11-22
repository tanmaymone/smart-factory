package com.factory.central.command;

public final class CommandErrorCode {

    public static final String VALIDATION_FAILED = "VALIDATION_FAILED";
    public static final String TAG_NOT_FOUND = "TAG_NOT_FOUND";
    public static final String TAG_NOT_WRITABLE = "TAG_NOT_WRITABLE";
    public static final String INVALID_VALUE = "INVALID_VALUE";
    public static final String TARGET_OUT_OF_RANGE = "TARGET_OUT_OF_RANGE";
    public static final String TAG_BOUNDS_MISCONFIGURED = "TAG_BOUNDS_MISCONFIGURED";
    public static final String INTERLOCK_REJECTED = "INTERLOCK_REJECTED";
    public static final String MQTT_PUBLISH_FAILED = "MQTT_PUBLISH_FAILED";
    public static final String INVALID_PARAMETER = "INVALID_PARAMETER";

    private CommandErrorCode() {}
}
