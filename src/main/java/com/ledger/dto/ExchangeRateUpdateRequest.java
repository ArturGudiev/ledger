package com.ledger.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

public record ExchangeRateUpdateRequest (
  @Positive BigDecimal rate,
  @Pattern(regexp = "^[A-Z]{3}$") String baseCurrency,
  @Pattern(regexp = "^[A-Z]{3}$") String quoteCurrency
){}
