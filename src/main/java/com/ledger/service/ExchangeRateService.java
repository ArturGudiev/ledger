package com.ledger.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ledger.dto.ExchangeRateDto;
import com.ledger.dto.ExchangeRateUpdateRequest;
import com.ledger.exception.BadRequestException;
import com.ledger.exception.ResourceNotFoundException;
import com.ledger.model.ExchangeRate;
import com.ledger.repository.ExchangeRateRepository;

@Service
public class ExchangeRateService {

  private static final int RATE_SCALE = 12;

  private final ExchangeRateRepository exchangeRateRepository;
  private final FxService fxService;

  public ExchangeRateService(ExchangeRateRepository exchangeRateRepository, FxService fxService) {
    this.exchangeRateRepository = exchangeRateRepository;
    this.fxService = fxService;
  }

  @Transactional(readOnly = true)
  public List<ExchangeRateDto> list(String baseCurrency) {
    List<ExchangeRate> rates = baseCurrency == null || baseCurrency.isBlank()
        ? exchangeRateRepository.findAll()
        : exchangeRateRepository.findByBaseCurrencyOrderByQuoteCurrency(currency(baseCurrency));
    return rates.stream()
        .sorted(Comparator.comparing(ExchangeRate::getBaseCurrency).thenComparing(ExchangeRate::getQuoteCurrency))
        .map(ExchangeRateDto::from)
        .toList();
  }

  @Transactional
  public ExchangeRateDto upsert(ExchangeRateUpdateRequest request) {
    if (request == null || request.rate() == null || request.rate().signum() <= 0) {
      throw new BadRequestException("Rate must be positive");
    }
    String base = currency(request.baseCurrency());
    String quote = currency(request.quoteCurrency());
    if (base.equals(quote)) {
      throw new BadRequestException("Base and quote currencies must differ");
    }

    ExchangeRate rate = exchangeRateRepository.findByBaseCurrencyAndQuoteCurrency(base, quote)
        .orElseGet(() -> {
          ExchangeRate created = new ExchangeRate();
          created.setBaseCurrency(base);
          created.setQuoteCurrency(quote);
          return created;
        });
    rate.setRate(request.rate().setScale(RATE_SCALE, RoundingMode.HALF_EVEN));
    ExchangeRate saved = exchangeRateRepository.save(rate);
    fxService.refresh();
    return ExchangeRateDto.from(saved);
  }

  @Transactional
  public void delete(String baseCurrency, String quoteCurrency) {
    String base = currency(baseCurrency);
    String quote = currency(quoteCurrency);
    ExchangeRate rate = exchangeRateRepository.findByBaseCurrencyAndQuoteCurrency(base, quote)
        .orElseThrow(() -> new ResourceNotFoundException("Exchange rate not found"));
    exchangeRateRepository.delete(rate);
    fxService.refresh();
  }

  private String currency(String value) {
    if (value == null || !value.trim().toUpperCase(Locale.ROOT).matches("[A-Z]{3}")) {
      throw new BadRequestException("Currency must be a 3-letter code");
    }
    return value.trim().toUpperCase(Locale.ROOT);
  }
}
