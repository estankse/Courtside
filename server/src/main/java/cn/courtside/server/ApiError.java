package cn.courtside.server;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.*;

public class ApiError extends RuntimeException {
  public final int status;
  public final String code;

  public ApiError(int status, String code, String message) {
    super(message);
    this.status = status;
    this.code = code;
  }

  public static void check(boolean ok, int status, String code, String message) {
    if (!ok) throw new ApiError(status, code, message);
  }
}

@RestControllerAdvice
class ErrorHandler {
  @ExceptionHandler(ApiError.class)
  ResponseEntity<?> business(ApiError e) {
    return ResponseEntity.status(e.status).body(Map.of("code", e.code, "message", e.getMessage()));
  }

  @ExceptionHandler(IllegalArgumentException.class)
  ResponseEntity<?> validation(IllegalArgumentException e) {
    return ResponseEntity.badRequest()
        .body(Map.of("code", "VALIDATION", "message", e.getMessage()));
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  ResponseEntity<?> unreadable() {
    return ResponseEntity.badRequest().body(Map.of("code", "INVALID_JSON", "message", "请求数据格式不正确"));
  }

  @ExceptionHandler(java.time.DateTimeException.class)
  ResponseEntity<?> invalidTime() {
    return ResponseEntity.badRequest()
        .body(Map.of("code", "INVALID_TIME", "message", "日期或时间格式不正确"));
  }
}
