package com.factory.central.command;

import java.util.List;
import java.util.UUID;

public record CommandErrorResponse(
        String code, String message, UUID commandId, List<FieldErrorDetail> fieldErrors) {

    public record FieldErrorDetail(String field, String message) {}

    public static CommandErrorResponse of(String code, String message) {
        return new CommandErrorResponse(code, message, null, List.of());
    }

    public static CommandErrorResponse of(
            String code, String message, UUID commandId, List<FieldErrorDetail> fieldErrors) {
        return new CommandErrorResponse(code, message, commandId, fieldErrors == null ? List.of() : fieldErrors);
    }
}
