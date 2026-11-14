package com.ledger.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ledger.dto.ExchangeRateDto;
import com.ledger.service.ExchangeRateService;

@RestController
@RequestMapping("/api/exchange-rates")
public class ExchangeRateController {

  private final ExchangeRateService exchangeRateService;

  public ExchangeRateController(ExchangeRateService exchangeRateService) {
    this.exchangeRateService = exchangeRateService;
  }

  @GetMapping
  public ResponseEntity<List<ExchangeRateDto>> list(@RequestParam(required = false) String base) {
    return ResponseEntity.ok(exchangeRateService.list(base));
  }
}
