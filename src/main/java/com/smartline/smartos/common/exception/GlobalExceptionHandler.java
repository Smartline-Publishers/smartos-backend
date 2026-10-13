package com.smartline.smartos.common.exception;

import com.smartline.smartos.common.ApiResponse;
import jakarta.persistence.EntityNotFoundException;
import java.util.HashMap;
import java.util.Map;
import org.springframework.data.core.PropertyReferenceException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiResponse<?>> handleValidation(MethodArgumentNotValidException ex) {
    Map<String, String> fields = new HashMap<>();
    ex.getBindingResult().getFieldErrors()
        .forEach(fe -> fields.put(fe.getField(), fe.getDefaultMessage()));
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(ApiResponse.error(ErrorCode.VALIDATION_ERROR.getCode(), "Validation failed", fields));
  }

  @ExceptionHandler(ApiException.class)
  public ResponseEntity<ApiResponse<?>> handleApi(ApiException ex) {
    return ResponseEntity.status(ex.getStatus())
        .body(ApiResponse.error(ex.getCode(), ex.getMessage(), ex.getFields()));
  }

  @ExceptionHandler(PropertyReferenceException.class)
  public ResponseEntity<ApiResponse<?>> handleBadSort(PropertyReferenceException ex) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(ApiResponse.error(ErrorCode.INVALID_SORT.getCode(),
            "Unknown sort property: " + ex.getPropertyName(), null));
  }

  @ExceptionHandler(AccessDeniedException.class)
  public ResponseEntity<ApiResponse<?>> handleAccessDenied(AccessDeniedException ex) {
    return ResponseEntity.status(HttpStatus.FORBIDDEN)
        .body(ApiResponse.error(ErrorCode.FORBIDDEN.getCode(), ex.getMessage(), null));
  }

  @ExceptionHandler(EntityNotFoundException.class)
  public ResponseEntity<ApiResponse<?>> handleEntityNotFound(EntityNotFoundException ex) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(ApiResponse.error(ErrorCode.NOT_FOUND.getCode(), ex.getMessage(), null));
  }

  @ExceptionHandler(NoResourceFoundException.class)
  public ResponseEntity<ApiResponse<?>> handleNoResource(NoResourceFoundException ex) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(ApiResponse.error(ErrorCode.NOT_FOUND.getCode(), "Not found: " + ex.getResourcePath(), null));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiResponse<?>> handleFallback(Exception ex) {
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(ApiResponse.error(ErrorCode.INTERNAL_ERROR.getCode(), "Unexpected error", null));
  }
}
