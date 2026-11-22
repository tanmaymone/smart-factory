package com.factory.central.command;

import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;

public class CommandException extends RuntimeException {

    private final String code;
    private final HttpStatus status;
    private final UUID commandId;
    private final List<CommandErrorResponse.FieldErrorDetail> fieldErrors;

    public CommandException(String code, String message, HttpStatus status) {
        this(code, message, status, null, List.of());
    }

    public CommandException(
            String code,
            String message,
            HttpStatus status,
            UUID commandId,
            List<CommandErrorResponse.FieldErrorDetail> fieldErrors) {
        super(message);
        this.code = code;
        this.status = status;
        this.commandId = commandId;
        this.fieldErrors = fieldErrors == null ? List.of() : fieldErrors;
    }

    public String getCode() {
        return code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public UUID getCommandId() {
        return commandId;
    }

    public List<CommandErrorResponse.FieldErrorDetail> getFieldErrors() {
        return fieldErrors;
    }
}
