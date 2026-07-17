package dev.eventmesh.error;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

  private final Clock clock;

  public ApiExceptionHandler(Clock clock) {
    this.clock = clock;
  }

  @ExceptionHandler(UnauthorizedException.class)
  ResponseEntity<ProblemDetail> handleUnauthorized(
      UnauthorizedException exception, HttpServletRequest request) {
    return problem(
        HttpStatus.UNAUTHORIZED,
        "UNAUTHORIZED",
        exception.getMessage(),
        request.getRequestURI(),
        null);
  }

  @ExceptionHandler(InvalidRequestException.class)
  ResponseEntity<ProblemDetail> handleInvalidRequest(
      InvalidRequestException exception, HttpServletRequest request) {
    return problem(
        HttpStatus.BAD_REQUEST,
        "INVALID_REQUEST",
        exception.getMessage(),
        request.getRequestURI(),
        null);
  }

  @ExceptionHandler(IdempotencyConflictException.class)
  ResponseEntity<ProblemDetail> handleIdempotencyConflict(
      IdempotencyConflictException exception, HttpServletRequest request) {
    return problem(
        HttpStatus.CONFLICT,
        "IDEMPOTENCY_CONFLICT",
        exception.getMessage(),
        request.getRequestURI(),
        null);
  }

  @ExceptionHandler(EventNotFoundException.class)
  ResponseEntity<ProblemDetail> handleNotFound(
      EventNotFoundException exception, HttpServletRequest request) {
    return problem(
        HttpStatus.NOT_FOUND,
        "EVENT_NOT_FOUND",
        exception.getMessage(),
        request.getRequestURI(),
        null);
  }

  @ExceptionHandler(SubscriptionNotFoundException.class)
  ResponseEntity<ProblemDetail> handleSubscriptionNotFound(
      SubscriptionNotFoundException exception, HttpServletRequest request) {
    return problem(
        HttpStatus.NOT_FOUND,
        "SUBSCRIPTION_NOT_FOUND",
        exception.getMessage(),
        request.getRequestURI(),
        null);
  }

  @ExceptionHandler(SubscriptionConflictException.class)
  ResponseEntity<ProblemDetail> handleSubscriptionConflict(
      SubscriptionConflictException exception, HttpServletRequest request) {
    return problem(
        HttpStatus.CONFLICT,
        "SUBSCRIPTION_CONFLICT",
        exception.getMessage(),
        request.getRequestURI(),
        null);
  }

  @ExceptionHandler(DeliveryNotFoundException.class)
  ResponseEntity<ProblemDetail> handleDeliveryNotFound(
      DeliveryNotFoundException exception, HttpServletRequest request) {
    return problem(
        HttpStatus.NOT_FOUND,
        "DELIVERY_NOT_FOUND",
        exception.getMessage(),
        request.getRequestURI(),
        null);
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<ProblemDetail> handleValidation(
      MethodArgumentNotValidException exception, HttpServletRequest request) {
    var violations = new LinkedHashMap<String, String>();
    exception
        .getBindingResult()
        .getFieldErrors()
        .forEach(error -> violations.putIfAbsent(error.getField(), error.getDefaultMessage()));

    return problem(
        HttpStatus.BAD_REQUEST,
        "VALIDATION_FAILED",
        "The request body failed validation",
        request.getRequestURI(),
        violations);
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  ResponseEntity<ProblemDetail> handleUnreadableBody(
      HttpMessageNotReadableException exception, HttpServletRequest request) {
    return problem(
        HttpStatus.BAD_REQUEST,
        "MALFORMED_JSON",
        "The request body must contain valid JSON",
        request.getRequestURI(),
        null);
  }

  private ResponseEntity<ProblemDetail> problem(
      HttpStatus status,
      String code,
      String detail,
      String path,
      Object validationErrors) {
    var problem = ProblemDetail.forStatusAndDetail(status, detail);
    problem.setType(URI.create("urn:eventmesh:problem:" + code.toLowerCase().replace('_', '-')));
    problem.setTitle(status.getReasonPhrase());
    problem.setProperty("code", code);
    problem.setProperty("path", path);
    problem.setProperty("timestamp", Instant.now(clock));
    if (validationErrors != null) {
      problem.setProperty("errors", validationErrors);
    }
    return ResponseEntity.status(status).body(problem);
  }
}
