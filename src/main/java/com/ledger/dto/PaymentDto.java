package com.ledger.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.ledger.model.PaymentTransaction;

public record PaymentDto (
  Long id,
  Long walletId,
  String type,
  BigDecimal amount,
  String currency,
  String status,
  String externalPaymentId,
  String failureReason,
  Instant createdAt,
  Instant updatedAt
){
  public static PaymentDto from(PaymentTransaction paymentTransaction) {
    return new PaymentDto(
      paymentTransaction.getId(),
      paymentTransaction.getWallet().getId(),
      paymentTransaction.getType().name(),
      paymentTransaction.getAmount(),
      paymentTransaction.getCurrency(),
      paymentTransaction.getStatus().name(),
      paymentTransaction.getExternalPaymentId(),
      paymentTransaction.getFailureReason(),
      paymentTransaction.getCreatedAt(),
      paymentTransaction.getUpdatedAt()
    );
  }
}
