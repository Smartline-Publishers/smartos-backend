package com.smartline.smartos.common.exception;

import java.util.Map;
import org.springframework.http.HttpStatus;

public class ApiException extends RuntimeException {

  private final HttpStatus status;
  private final String code;
  private final Map<String, String> fields;

  public ApiException(HttpStatus status, String code, String message) {
    super(message);
    this.status = status;
    this.code = code;
    this.fields = null;
  }

  public ApiException(HttpStatus status, String code, String message, Map<String, String> fields) {
    super(message);
    this.status = status;
    this.code = code;
    this.fields = fields;
  }

  public HttpStatus getStatus() {
    return status;
  }

  public String getCode() {
    return code;
  }

  public Map<String, String> getFields() {
    return fields;
  }

  public static ApiException notFound(String entity, Object id) {
    return new ApiException(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND.getCode(),
        entity + " not found: " + id);
  }

  public static ApiException conflict(String message) {
    return new ApiException(HttpStatus.CONFLICT, ErrorCode.CONFLICT.getCode(), message);
  }

  public static ApiException badRequest(String message) {
    return new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.BAD_REQUEST.getCode(), message);
  }

  public static ApiException forbidden(String message) {
    return new ApiException(HttpStatus.FORBIDDEN, ErrorCode.FORBIDDEN.getCode(), message);
  }

  public static ApiException planLimit(String message) {
    return new ApiException(HttpStatus.FORBIDDEN, ErrorCode.PLAN_LIMIT.getCode(), message);
  }
}
