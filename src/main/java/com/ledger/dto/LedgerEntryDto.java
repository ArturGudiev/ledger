package com.ledger.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.ledger.model.LedgerEntry;

public record LedgerEntryDto (
  Long id,
  Long walletId,
  Long transferId,
  Long paymentId,
  String entryType,
  BigDecimal amount,
  BigDecimal balanceAfter,
  String currency,
  String description,
  String operationKey,
  Instant createdAt
){
  public static LedgerEntryDto from(LedgerEntry entry) {
    return new LedgerEntryDto(
      entry.getId(),
      entry.getWallet().getId(),
      entry.getTransfer() == null ? null : entry.getTransfer().getId(),
      entry.getPayment() == null ? null : entry.getPayment().getId(),
      entry.getEntryType().name(),
      entry.getAmount(),
      entry.getBalanceAfter(),
      entry.getCurrency(),
      entry.getDescription(),
      entry.getOperationKey(),
      entry.getCreatedAt()
    );
  }
}
