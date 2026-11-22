package com.factory.central.stream;

public record StreamErrorResponse(String code, String message) {

    public static StreamErrorResponse of(String code, String message) {
        return new StreamErrorResponse(code, message);
    }
}
