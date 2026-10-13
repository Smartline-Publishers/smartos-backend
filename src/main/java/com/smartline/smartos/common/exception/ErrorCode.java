package com.smartline.smartos.common.exception;

public enum ErrorCode {
  VALIDATION_ERROR("VALIDATION_ERROR"),
  INVALID_SORT("INVALID_SORT"),
  NOT_FOUND("NOT_FOUND"),
  CONFLICT("CONFLICT"),
  UNAUTHORIZED("UNAUTHORIZED"),
  FORBIDDEN("FORBIDDEN"),
  BAD_REQUEST("BAD_REQUEST"),
  INTERNAL_ERROR("INTERNAL_ERROR");

  private final String code;

  ErrorCode(String code) {
    this.code = code;
  }

  public String getCode() {
    return code;
  }
}
