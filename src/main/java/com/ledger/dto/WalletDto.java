package com.ledger.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.ledger.model.Wallet;

public record WalletDto (
  Long id,
  String currency,
  String status,
  BigDecimal balance,
  Instant createdAt
) {
  public static WalletDto of(Wallet wallet, BigDecimal balance) {
    return new WalletDto(
      wallet.getId(),
      wallet.getCurrency(),
      wallet.getStatus().name(),
      balance,
      wallet.getCreatedAt()
    );
  }
}
