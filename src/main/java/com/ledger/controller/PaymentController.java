package com.ledger.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ledger.config.LedgerProperties;
import com.ledger.dto.PaymentDto;
import com.ledger.dto.TopUpRequest;
import com.ledger.dto.WithdrawRequest;
import com.ledger.exception.ResourceNotFoundException;
import com.ledger.model.User;
import com.ledger.security.RateLimitService;
import com.ledger.service.IdempotencyService;
import com.ledger.service.PaymentSagaService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

  private final PaymentSagaService sagaService;
  private final IdempotencyService idempotencyService;
  private final RateLimitService rateLimitService;
  private final LedgerProperties properties;

  public PaymentController(
      PaymentSagaService sagaService,
      IdempotencyService idempotencyService,
      RateLimitService rateLimitService,
      LedgerProperties properties
  ) {
    this.sagaService = sagaService;
    this.idempotencyService = idempotencyService;
    this.rateLimitService = rateLimitService;
    this.properties = properties;
  }

  @PostMapping("/wallets/{walletId}/top-ups")
  public ResponseEntity<?> topUp(
      @AuthenticationPrincipal User user,
      @PathVariable Long walletId,
      @Valid @RequestBody TopUpRequest request,
      @RequestHeader("Idempotency-Key") String idempotencyKey
  ) {
    User current = requireUser(user);
    return idempotencyService.execute(current.getId(), idempotencyKey, () -> {
      checkRateLimit(current);
      return accepted(sagaService.topUp(current, walletId, request, idempotencyKey));
    });
  }

  @PostMapping("/wallets/{walletId}/withdrawals")
  public ResponseEntity<?> withdraw(
      @AuthenticationPrincipal User user,
      @PathVariable Long walletId,
      @Valid @RequestBody WithdrawRequest request,
      @RequestHeader("Idempotency-Key") String idempotencyKey
  ) {
    User current = requireUser(user);
    return idempotencyService.execute(current.getId(), idempotencyKey, () -> {
      checkRateLimit(current);
      return accepted(sagaService.withdraw(current, walletId, request, idempotencyKey));
    });
  }

  @GetMapping
  public ResponseEntity<List<PaymentDto>> list(@AuthenticationPrincipal User user) {
    return ResponseEntity.ok(sagaService.list(requireUser(user).getId()));
  }

  @GetMapping("/{paymentId}")
  public ResponseEntity<PaymentDto> get(@AuthenticationPrincipal User user, @PathVariable Long paymentId) {
    return ResponseEntity.ok(sagaService.get(requireUser(user).getId(), paymentId));
  }

  private void checkRateLimit(User user) {
    rateLimitService.check(
        "payment:" + user.getId(),
        properties.rateLimit().loginAttempts(),
        properties.rateLimit().loginWindow()
    );
  }

  private ResponseEntity<PaymentDto> accepted(PaymentDto payment) {
    HttpStatus status = properties.gateway().settleAsync() ? HttpStatus.ACCEPTED : HttpStatus.OK;
    return ResponseEntity.status(status).body(payment);
  }

  private User requireUser(User user) {
    if (user == null || user.getId() == null) {
      throw new ResourceNotFoundException("User not found");
    }
    return user;
  }
}
