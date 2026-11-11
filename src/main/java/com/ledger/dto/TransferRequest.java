package com.ledger.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;

public record TransferRequest (
  Long fromWalletId,
  Long toWalletId,
  @DecimalMin(value = "0.01") @Digits(integer = 10, fraction = 2) BigDecimal amount
){}
