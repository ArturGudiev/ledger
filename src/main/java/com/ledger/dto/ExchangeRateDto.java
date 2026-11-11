package com.ledger.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.ledger.model.ExchangeRate;

public record ExchangeRateDto (
  String baseCurrency,
  String quoteCurrency,
  BigDecimal rate,
  Instant updatedAt
) {
  public static ExchangeRateDto from(ExchangeRate exchangeRate) {
    return new ExchangeRateDto(
      exchangeRate.getBaseCurrency(),
      exchangeRate.getQuoteCurrency(),
      exchangeRate.getRate(),
      exchangeRate.getUpdatedAt()
    );
  }
}
