package com.ledger.exception;

import org.springframework.http.HttpStatus;

public class ResourceNotFoundException extends ApiException {

  ResourceNotFoundException(String message) {
    super(HttpStatus.NOT_FOUND, message);
  }
}
