package com.ledger.controller;

import java.util.List;

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
import com.ledger.dto.TransferDto;
import com.ledger.dto.TransferRequest;
import com.ledger.exception.ResourceNotFoundException;
import com.ledger.model.User;
import com.ledger.security.RateLimitService;
import com.ledger.service.IdempotencyService;
import com.ledger.service.TransferService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/transfers")
public class TransferController {

  private final TransferService transferService;
  private final IdempotencyService idempotencyService;
  private final RateLimitService rateLimitService;
  private final LedgerProperties properties;

  public TransferController(
      TransferService transferService,
      IdempotencyService idempotencyService,
      RateLimitService rateLimitService,
      LedgerProperties properties
  ) {
    this.transferService = transferService;
    this.idempotencyService = idempotencyService;
    this.rateLimitService = rateLimitService;
    this.properties = properties;
  }

  @PostMapping
  public ResponseEntity<?> transfer(
      @AuthenticationPrincipal User user,
      @Valid @RequestBody TransferRequest request,
      @RequestHeader("Idempotency-Key") String idempotencyKey
  ) {
    User current = requireUser(user);
    return idempotencyService.execute(current.getId(), idempotencyKey, () -> {
      rateLimitService.check(
          "transfer:" + current.getId(),
          properties.rateLimit().loginAttempts(),
          properties.rateLimit().loginWindow()
      );
      return ResponseEntity.ok(transferService.transfer(current, request));
    });
  }

  @GetMapping
  public ResponseEntity<List<TransferDto>> list(@AuthenticationPrincipal User user) {
    return ResponseEntity.ok(transferService.list(requireUser(user).getId()));
  }

  @GetMapping("/{transferId}")
  public ResponseEntity<TransferDto> get(@AuthenticationPrincipal User user, @PathVariable Long transferId) {
    return ResponseEntity.ok(transferService.get(requireUser(user).getId(), transferId));
  }

  private User requireUser(User user) {
    if (user == null || user.getId() == null) {
      throw new ResourceNotFoundException("User not found");
    }
    return user;
  }
}
