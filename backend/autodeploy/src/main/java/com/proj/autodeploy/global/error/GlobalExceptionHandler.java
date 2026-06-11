package com.proj.autodeploy.global.error;

import java.util.HashMap;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApiException(ApiException e) {
        ErrorCode code = e.getErrorCode();
        return ResponseEntity
                .status(code.httpStatus())
                .body(ErrorResponse.of(code.name(), e.getMessage(), e.getDetails()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
        Map<String, Object> details = new HashMap<>();
        for (FieldError fieldError : e.getBindingResult().getFieldErrors()) {
            details.put(fieldError.getField(), fieldError.getDefaultMessage());
        }
        ErrorCode code = ErrorCode.VALIDATION_ERROR;
        return ResponseEntity
                .status(code.httpStatus())
                .body(ErrorResponse.of(code.name(), code.defaultMessage(), details));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException e) {
        ErrorCode code = ErrorCode.PROJECT_ACCESS_DENIED;
        return ResponseEntity
                .status(code.httpStatus())
                .body(ErrorResponse.of(code.name(), code.defaultMessage(), null));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthentication(AuthenticationException e) {
        ErrorCode code = ErrorCode.AUTH_TOKEN_INVALID;
        return ResponseEntity
                .status(code.httpStatus())
                .body(ErrorResponse.of(code.name(), code.defaultMessage(), null));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception e) {
        ErrorCode code = ErrorCode.INTERNAL_ERROR;
        return ResponseEntity
                .status(code.httpStatus())
                .body(ErrorResponse.of(code.name(), code.defaultMessage(), null));
    }
}
