package com.ledger.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.ledger.exception.BadRequestException;
import com.ledger.model.ExchangeRate;
import com.ledger.repository.ExchangeRateRepository;

@Service
public class FxService {

  private static final BigDecimal ONE = BigDecimal.ONE;
  private static final int RATE_SCALE = 12;

  private final ExchangeRateRepository exchangeRateRepository;
  private final String pivot;
  private volatile Map<String, BigDecimal> pivotRates = Map.of();

  public FxService(
      ExchangeRateRepository exchangeRateRepository,
      @Value("${ledger.fx.pivot:USD}") String pivot
  ) {
    this.exchangeRateRepository = exchangeRateRepository;
    this.pivot = pivot.toUpperCase(Locale.ROOT);
  }

  public void refresh() {
    Map<String, BigDecimal> loaded = new ConcurrentHashMap<>();
    loaded.put(pivot, ONE);
    for (ExchangeRate rate : exchangeRateRepository.findByBaseCurrencyOrderByQuoteCurrency(pivot)) {
      loaded.put(rate.getQuoteCurrency(), rate.getRate());
    }
    pivotRates = Map.copyOf(loaded);
  }

  public BigDecimal rate(String baseCurrency, String quoteCurrency) {
    String base = canonical(baseCurrency);
    String quote = canonical(quoteCurrency);
    normalize(base);
    normalize(quote);
    if (base.equals(quote)) {
      return ONE;
    }

    ensureLoaded();
    BigDecimal basePerPivot = pivotRates.get(base);
    BigDecimal quotePerPivot = pivotRates.get(quote);
    if (basePerPivot == null || quotePerPivot == null || basePerPivot.signum() == 0) {
      throw new BadRequestException("No exchange rate from " + base + " to " + quote);
    }
    return quotePerPivot.divide(basePerPivot, RATE_SCALE, RoundingMode.HALF_EVEN);
  }

  public BigDecimal convert(BigDecimal amount, String baseCurrency, String quoteCurrency) {
    if (amount == null) {
      throw new BadRequestException("Amount is required");
    }
    return amount.multiply(rate(baseCurrency, quoteCurrency)).setScale(RATE_SCALE, RoundingMode.HALF_EVEN);
  }

  public List<String> supportedCurrencies() {
    ensureLoaded();
    return pivotRates.keySet().stream().sorted().toList();
  }

  private void normalize(String currency) {
    if (!currency.matches("[A-Z]{3}")) {
      throw new BadRequestException("Currency must be a 3-letter code");
    }
  }

  private void ensureLoaded() {
    if (pivotRates.isEmpty()) {
      refresh();
    }
  }

  private String canonical(String currency) {
    if (currency == null) {
      throw new BadRequestException("Currency must be a 3-letter code");
    }
    return currency.trim().toUpperCase(Locale.ROOT);
  }
}
