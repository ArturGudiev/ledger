package com.ledger.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ledger.dto.ExchangeRateDto;
import com.ledger.dto.ExchangeRateUpdateRequest;
import com.ledger.dto.PaymentDto;
import com.ledger.dto.TransferDto;
import com.ledger.service.ExchangeRateService;
import com.ledger.service.PaymentSagaService;
import com.ledger.service.TransferService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

  private final ExchangeRateService exchangeRateService;
  private final TransferService transferService;
  private final PaymentSagaService paymentSagaService;

  public AdminController(
      ExchangeRateService exchangeRateService,
      TransferService transferService,
      PaymentSagaService paymentSagaService
  ) {
    this.exchangeRateService = exchangeRateService;
    this.transferService = transferService;
    this.paymentSagaService = paymentSagaService;
  }

  @PutMapping("/exchange-rates")
  public ResponseEntity<ExchangeRateDto> upsertRate(@Valid @RequestBody ExchangeRateUpdateRequest request) {
    return ResponseEntity.ok(exchangeRateService.upsert(request));
  }

  @DeleteMapping("/exchange-rates/{baseCurrency}/{quoteCurrency}")
  public ResponseEntity<Void> deleteRate(
      @PathVariable String baseCurrency,
      @PathVariable String quoteCurrency
  ) {
    exchangeRateService.delete(baseCurrency, quoteCurrency);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/transfers")
  public ResponseEntity<List<TransferDto>> allTransfers() {
    return ResponseEntity.ok(transferService.listAll());
  }

  @GetMapping("/payments")
  public ResponseEntity<List<PaymentDto>> allPayments() {
    return ResponseEntity.ok(paymentSagaService.listAll());
  }
}
