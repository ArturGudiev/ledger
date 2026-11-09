package com.ledger.exception;

import org.springframework.http.HttpStatus;

public class ApiException extends RuntimeException {
    protected HttpStatus status;

    protected ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }

}