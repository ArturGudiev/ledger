package com.ledger.security;

import org.springframework.http.HttpStatus;

import com.ledger.exception.ApiException;

public class RefreshTokenReuseException extends ApiException {

    public RefreshTokenReuseException(String familyId, String tokenId, Long userId) {
        super(HttpStatus.BAD_REQUEST, "Refresh token reuse detected for family: " + familyId + ", token: " + tokenId + ", user: " + userId);
    }
    
}
