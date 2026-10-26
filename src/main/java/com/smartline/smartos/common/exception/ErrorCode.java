package com.smartline.smartos.common.exception;

public enum ErrorCode {
  VALIDATION_ERROR("VALIDATION_ERROR"),
  INVALID_SORT("INVALID_SORT"),
  NOT_FOUND("NOT_FOUND"),
  CONFLICT("CONFLICT"),
  UNAUTHORIZED("UNAUTHORIZED"),
  FORBIDDEN("FORBIDDEN"),
  PLAN_LIMIT("PLAN_LIMIT"),
  LEGAL_ACCEPTANCE_REQUIRED("LEGAL_ACCEPTANCE_REQUIRED"),
  BAD_REQUEST("BAD_REQUEST"),
  NOT_CONFIGURED("NOT_CONFIGURED"),
  INTERNAL_ERROR("INTERNAL_ERROR");

  private final String code;

  ErrorCode(String code) {
    this.code = code;
  }

  public String getCode() {
    return code;
  }
}
