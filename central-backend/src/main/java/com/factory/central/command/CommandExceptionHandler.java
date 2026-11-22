package com.factory.central.command;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackageClasses = CommandController.class)
public class CommandExceptionHandler {

    @ExceptionHandler(CommandException.class)
    public ResponseEntity<CommandErrorResponse> handleCommand(CommandException ex) {
        return ResponseEntity.status(ex.getStatus())
                .body(CommandErrorResponse.of(ex.getCode(), ex.getMessage(), ex.getCommandId(), ex.getFieldErrors()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<CommandErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        List<CommandErrorResponse.FieldErrorDetail> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(this::toDetail)
                .toList();
        return ResponseEntity.badRequest()
                .body(CommandErrorResponse.of(
                        CommandErrorCode.VALIDATION_FAILED, "Request validation failed", null, fieldErrors));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<CommandErrorResponse> handleUnreadable(HttpMessageNotReadableException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(CommandErrorResponse.of(
                        CommandErrorCode.VALIDATION_FAILED, "Malformed or unreadable request body"));
    }

    private CommandErrorResponse.FieldErrorDetail toDetail(FieldError error) {
        return new CommandErrorResponse.FieldErrorDetail(error.getField(), error.getDefaultMessage());
    }
}
