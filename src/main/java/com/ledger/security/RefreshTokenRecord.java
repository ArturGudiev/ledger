package com.ledger.security;

public record RefreshTokenRecord (
    String familyId,
    String tokenId,
    Long userId,
    long expiresAtEpochSeconds
) {
    public String asString() {
        return familyId + ":" + tokenId + ":" + userId + ":" + expiresAtEpochSeconds;
    }

    public String[] split(String separator) {
        return asString().split(separator);
    }
}
