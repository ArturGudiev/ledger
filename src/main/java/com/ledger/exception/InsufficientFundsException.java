package com.ledger.exception;

import org.springframework.http.HttpStatus;

public class InsufficientFundsException extends ApiException {
  
  InsufficientFundsException(String message) {
    super(HttpStatus.UNPROCESSABLE_CONTENT, message);
  }
  
}
