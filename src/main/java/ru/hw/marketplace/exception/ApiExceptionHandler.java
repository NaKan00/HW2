package ru.hw.marketplace.exception;

import jakarta.validation.ConstraintViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.slf4j.LoggerFactory;
import ru.hw.marketplace.generated.model.*;
import java.util.*;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiError> business(BusinessException e) {
        return ResponseEntity.status(Errors.status(e.code)).body(Errors.body(e.code, e.getMessage(), e.details));
    }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> validation(MethodArgumentNotValidException e) {
        var violations = e.getBindingResult().getFieldErrors().stream()
            .map(f -> Map.of("field", f.getField(), "violation", Objects.requireNonNullElse(f.getDefaultMessage(), "invalid"))).toList();
        return invalid(violations);
    }
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> constraints(ConstraintViolationException e) {
        return invalid(e.getConstraintViolations().stream().map(v -> Map.of("field", v.getPropertyPath().toString(), "violation", v.getMessage())).toList());
    }
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiError> methodValidation(HandlerMethodValidationException e) {
        return invalid(e.getParameterValidationResults().stream().flatMap(r -> r.getResolvableErrors().stream()
            .map(v -> Map.of("field", Objects.requireNonNullElse(r.getMethodParameter().getParameterName(), "request"), "violation", Objects.requireNonNullElse(v.getDefaultMessage(), "invalid")))).toList());
    }
    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
        NoResourceFoundException.class, HttpRequestMethodNotSupportedException.class, HttpMediaTypeNotSupportedException.class})
    public ResponseEntity<ApiError> malformed(Exception e) {
        return invalid(List.of(Map.of("field", "request", "violation", "Malformed request, unsupported field, value, route or content type")));
    }
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> conflict(DataIntegrityViolationException e) {
        return invalid(List.of(Map.of("field", "request", "violation", "Value already exists or violates a database constraint")));
    }
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> forbidden(AccessDeniedException e) {
        return business(new BusinessException(ErrorCode.ACCESS_DENIED, "Access denied"));
    }
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> unexpected(Exception e) {
        LoggerFactory.getLogger(getClass()).error("Unhandled server error: {}", e.getClass().getSimpleName());
        return ResponseEntity.internalServerError().body(Errors.body(ErrorCode.INTERNAL_ERROR, "Internal server error", Map.of()));
    }
    private ResponseEntity<ApiError> invalid(Object violations) {
        return ResponseEntity.badRequest().body(Errors.body(ErrorCode.VALIDATION_ERROR, "Request validation failed", Map.of("violations", violations)));
    }
}
