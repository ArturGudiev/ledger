package com.ledger.exception;

import org.springframework.http.HttpStatus;

public class ConflictException extends ApiException {

  ConflictException(String message) {
    super(HttpStatus.CONFLICT, message);
  }
  
}
