package com.factory.central.registry;

import java.util.List;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(basePackageClasses = TagRegistryController.class)
public class RegistryExceptionHandler {

    private static final String UNIQUE_TAG_CONSTRAINT = "uq_tag_mappings_site_line_code";

    @ExceptionHandler(RegistryException.class)
    public ResponseEntity<RegistryErrorResponse> handleRegistry(RegistryException ex) {
        return ResponseEntity.status(ex.getStatus())
                .body(RegistryErrorResponse.of(ex.getCode(), ex.getMessage(), ex.getFieldErrors()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<RegistryErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        List<RegistryErrorResponse.FieldErrorDetail> fields = ex.getBindingResult().getFieldErrors().stream()
                .map(err -> new RegistryErrorResponse.FieldErrorDetail(err.getField(), err.getDefaultMessage()))
                .toList();
        return ResponseEntity.badRequest()
                .body(RegistryErrorResponse.of(
                        RegistryErrorCode.VALIDATION_FAILED, "Request validation failed", fields));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<RegistryErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String name = ex.getName() == null ? "parameter" : ex.getName();
        return ResponseEntity.badRequest()
                .body(RegistryErrorResponse.of(
                        RegistryErrorCode.INVALID_PARAMETER,
                        "Invalid value for parameter '" + name + "'",
                        List.of(new RegistryErrorResponse.FieldErrorDetail(
                                name, "must be a valid " + expectedType(ex)))));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<RegistryErrorResponse> handleUnreadable(HttpMessageNotReadableException ex) {
        return ResponseEntity.badRequest()
                .body(RegistryErrorResponse.of(
                        RegistryErrorCode.VALIDATION_FAILED, "Malformed or unreadable request body"));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<RegistryErrorResponse> handleIntegrity(DataIntegrityViolationException ex) {
        if (isTagCodeUniqueViolation(ex)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(RegistryErrorResponse.of(
                            RegistryErrorCode.TAG_CODE_EXISTS, "Tag code already exists for site/line"));
        }
        throw ex;
    }

    private static boolean isTagCodeUniqueViolation(DataIntegrityViolationException ex) {
        Throwable root = NestedExceptionUtils.getMostSpecificCause(ex);
        if (root instanceof ConstraintViolationException hibernate) {
            String name = hibernate.getConstraintName();
            if (name != null && name.toLowerCase().contains(UNIQUE_TAG_CONSTRAINT)) {
                return true;
            }
        }
        String message = root.getMessage();
        return message != null && message.toLowerCase().contains(UNIQUE_TAG_CONSTRAINT);
    }

    private static String expectedType(MethodArgumentTypeMismatchException ex) {
        Class<?> required = ex.getRequiredType();
        if (required == null) {
            return "value";
        }
        if (required.isEnum()) {
            return required.getSimpleName() + " enum";
        }
        return required.getSimpleName();
    }
}
