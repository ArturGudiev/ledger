package com.ledger.exception;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.ledger.dto.ErrorResponse;

@RestControllerAdvice
public class GlobalExceptionHandler {
  private static final Logger log = Logger.getLogger(GlobalExceptionHandler.class.getName());

  @ExceptionHandler(ApiException.class)
  public ResponseEntity<ErrorResponse> handleApi(ApiException apiException) {
    log.warning(apiException.getMessage());
    return response(apiException.getStatus(), apiException.getMessage());
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException exception) {
    Map<String, String> fieldErrors = exception.getBindingResult().getFieldErrors().stream()
        .collect(Collectors.toMap(
            FieldError::getField,
            fieldError -> fieldError.getDefaultMessage() == null ? "invalid" : fieldError.getDefaultMessage(),
            (existing, ignored) -> existing,
            LinkedHashMap::new
        ));
    log.warning("Validation failed: " + fieldErrors);
    return response(HttpStatus.BAD_REQUEST, "Validation failed", fieldErrors);
  }

  @ExceptionHandler(MissingRequestHeaderException.class)
  public ResponseEntity<ErrorResponse> handleMissingHeader(MissingRequestHeaderException exception) {
    log.warning(exception.getMessage());
    return response(HttpStatus.BAD_REQUEST, exception.getMessage());
  }

  @ExceptionHandler(BadCredentialsException.class)
  public ResponseEntity<ErrorResponse> handleBadCredentials(BadCredentialsException exception) {
    log.warning(exception.getMessage());
    return response(HttpStatus.UNAUTHORIZED, "Invalid email or password");
  }

  @ExceptionHandler(AuthenticationException.class)
  public ResponseEntity<ErrorResponse> handleAuthentication(AuthenticationException exception) {
    log.warning(exception.getMessage());
    return response(HttpStatus.UNAUTHORIZED, "Authentication failed");
  }

  @ExceptionHandler(AccessDeniedException.class)
  public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException exception) {
    log.warning(exception.getMessage());
    return response(HttpStatus.FORBIDDEN, "Access denied");
  }

  @ExceptionHandler(OptimisticLockingFailureException.class)
  public ResponseEntity<ErrorResponse> handleOptimisticLock(OptimisticLockingFailureException exception) {
    log.warning(exception.getMessage());
    return response(HttpStatus.CONFLICT, "The resource was modified by another request");
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  public ResponseEntity<ErrorResponse> handleDataIntegrity(DataIntegrityViolationException exception) {
    log.warning(exception.getMessage());
    return response(HttpStatus.CONFLICT, "Data integrity violation");
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorResponse> handleGeneric(Exception exception) {
    log.log(Level.SEVERE, "Unhandled exception", exception);
    return response(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error");
  }

  private ResponseEntity<ErrorResponse> response(HttpStatus status, String message) {
    return ResponseEntity.status(status).body(
        ErrorResponse.of(status.value(), status.getReasonPhrase(), message)
    );
  }

  private ResponseEntity<ErrorResponse> response(
      HttpStatus status,
      String message,
      Map<String, String> fieldErrors
  ) {
    return ResponseEntity.status(status).body(
        ErrorResponse.of(status.value(), status.getReasonPhrase(), message, fieldErrors)
    );
  }
}
