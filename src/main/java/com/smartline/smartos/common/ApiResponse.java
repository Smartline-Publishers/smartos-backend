package com.smartline.smartos.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

  private boolean success;
  private T data;
  private Object meta;
  private ApiError error;

  public ApiResponse() {
  }

  public ApiResponse(boolean success, T data, Object meta, ApiError error) {
    this.success = success;
    this.data = data;
    this.meta = meta;
    this.error = error;
  }

  public boolean isSuccess() {
    return success;
  }

  public void setSuccess(boolean success) {
    this.success = success;
  }

  public T getData() {
    return data;
  }

  public void setData(T data) {
    this.data = data;
  }

  public Object getMeta() {
    return meta;
  }

  public void setMeta(Object meta) {
    this.meta = meta;
  }

  public ApiError getError() {
    return error;
  }

  public void setError(ApiError error) {
    this.error = error;
  }

  public static <T> ApiResponse<T> ok(T data) {
    return new ApiResponse<>(true, data, null, null);
  }

  public static <T> ApiResponse<T> ok(T data, Object meta) {
    return new ApiResponse<>(true, data, meta, null);
  }

  public static <T> ApiResponse<T> error(String code, String message, Map<String, String> fields) {
    return new ApiResponse<>(false, null, null, new ApiError(code, message, fields));
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public static class ApiError {
    private String code;
    private String message;
    private Map<String, String> fields;

    public ApiError() {
    }

    public ApiError(String code, String message, Map<String, String> fields) {
      this.code = code;
      this.message = message;
      this.fields = fields;
    }

    public String getCode() {
      return code;
    }

    public void setCode(String code) {
      this.code = code;
    }

    public String getMessage() {
      return message;
    }

    public void setMessage(String message) {
      this.message = message;
    }

    public Map<String, String> getFields() {
      return fields;
    }

    public void setFields(Map<String, String> fields) {
      this.fields = fields;
    }
  }
}
