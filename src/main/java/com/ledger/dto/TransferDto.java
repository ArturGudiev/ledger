package com.ledger.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.ledger.model.Transfer;

public record TransferDto(
  Long id,
  Long fromWalletId,
  Long toWalletId,
  String currency,
  BigDecimal amount,
  BigDecimal convertedAmount,
  BigDecimal fxRate,
  BigDecimal fee,
  String status,
  String errorMessage,
  Instant createdAt,
  Instant completedAt
){
  public static TransferDto from(Transfer transfer) {
    return new TransferDto(
      transfer.getId(),
      transfer.getFromWallet().getId(),
      transfer.getToWallet().getId(),
      transfer.getCurrency(),
      transfer.getAmount(),
      transfer.getConvertedAmount(),
      transfer.getFxRate(),
      transfer.getFee(),
      transfer.getStatus().name(),
      transfer.getErrorMessage(),
      transfer.getCreatedAt(),
      transfer.getCompletedAt()
    );
  }
}
