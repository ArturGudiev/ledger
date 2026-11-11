package com.ledger.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;

public record WithdrawRequest (
  @DecimalMin(value = "0.01") @Digits(integer = 10, fraction = 2) BigDecimal amount,
  String bankAccount
){}

