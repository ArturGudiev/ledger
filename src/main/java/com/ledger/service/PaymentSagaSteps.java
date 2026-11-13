package com.ledger.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ledger.dto.PaymentDto;
import com.ledger.exception.BadRequestException;
import com.ledger.exception.ConflictException;
import com.ledger.exception.InsufficientFundsException;
import com.ledger.exception.ResourceNotFoundException;
import com.ledger.gateway.GatewayResponse;
import com.ledger.model.LedgerEntry.EntryType;
import com.ledger.model.PaymentTransaction;
import com.ledger.model.PaymentTransaction.PaymentStatus;
import com.ledger.model.PaymentTransaction.PaymentType;
import com.ledger.model.Wallet;
import com.ledger.model.Wallet.WalletStatus;
import com.ledger.repository.PaymentTransactionRepository;
import com.ledger.repository.WalletRepository;

@Service
public class PaymentSagaSteps {

  private static final int MONEY_SCALE = 8;
  private static final Set<String> SUCCESS = Set.of("SUCCESS", "SUCCEEDED", "COMPLETED", "APPROVED");

  private final PaymentTransactionRepository paymentRepository;
  private final WalletRepository walletRepository;
  private final LedgerService ledgerService;
  private final OutboxService outboxService;

  public PaymentSagaSteps(
      PaymentTransactionRepository paymentRepository,
      WalletRepository walletRepository,
      LedgerService ledgerService,
      OutboxService outboxService
  ) {
    this.paymentRepository = paymentRepository;
    this.walletRepository = walletRepository;
    this.ledgerService = ledgerService;
    this.outboxService = outboxService;
  }

  @Transactional
  public PaymentTransaction initiate(Wallet wallet, PaymentTransaction payment) {
    if (wallet == null || wallet.getId() == null || payment == null || payment.getUser() == null) {
      throw new BadRequestException("Payment is required");
    }
    if (payment.getAmount() == null || payment.getAmount().signum() <= 0) {
      throw new BadRequestException("Amount must be positive");
    }
    if (payment.getType() == null) {
      throw new BadRequestException("Payment type is required");
    }
    if (payment.getIdempotencyKey() == null || payment.getIdempotencyKey().isBlank()) {
      throw new BadRequestException("Idempotency key is required");
    }

    Wallet locked = walletRepository.findByIdForUpdate(wallet.getId())
        .orElseThrow(() -> new ResourceNotFoundException("Wallet not found"));
    if (locked.getUser() == null || !payment.getUser().getId().equals(locked.getUser().getId())) {
      throw new ResourceNotFoundException("Wallet not found");
    }
    if (locked.getStatus() != WalletStatus.ACTIVE) {
      throw new ConflictException("Wallet is not active");
    }

    payment.setWallet(locked);
    payment.setCurrency(locked.getCurrency());
    payment.setAmount(payment.getAmount().setScale(MONEY_SCALE, RoundingMode.HALF_EVEN));
    payment.setStatus(PaymentStatus.PENDING);
    if (payment.getType() == PaymentType.WITHDRAW
        && ledgerService.currentBalance(locked).compareTo(payment.getAmount()) < 0) {
      throw new InsufficientFundsException("Insufficient funds");
    }

    try {
      PaymentTransaction saved = paymentRepository.saveAndFlush(payment);
      outboxService.emit("PAYMENT", saved.getId().toString(), "PAYMENT_INITIATED", eventPayload(saved));
      return saved;
    } catch (DataIntegrityViolationException exception) {
      throw new ConflictException("A payment with this idempotency key already exists");
    }
  }

  @Transactional
  public PaymentTransaction settle(Long paymentId, GatewayResponse response) {
    PaymentTransaction payment = lock(paymentId);
    if (payment.getStatus() == PaymentStatus.COMPLETED || payment.getStatus() == PaymentStatus.ROLLED_BACK) {
      return payment;
    }
    if (response == null || response.status() == null || !SUCCESS.contains(response.status().trim().toUpperCase(Locale.ROOT))) {
      payment.setStatus(PaymentStatus.FAILED);
      payment.setFailureReason(response == null ? "Gateway response is missing" : "Gateway status " + response.status());
      payment.setExternalPaymentId(response == null ? null : response.id());
      outboxService.emit("PAYMENT", payment.getId().toString(), "PAYMENT_FAILED", eventPayload(payment));
      return payment;
    }

    EntryType entryType = payment.getType() == PaymentType.TOP_UP ? EntryType.CREDIT : EntryType.DEBIT;
    ledgerService.post(
        payment.getWallet(),
        entryType,
        payment.getAmount(),
        payment.getType().name() + " " + payment.getId(),
        "payment:" + payment.getId() + ":settle"
    );
    payment.setStatus(PaymentStatus.COMPLETED);
    payment.setExternalPaymentId(response.id());
    payment.setFailureReason(null);
    outboxService.emit("PAYMENT", payment.getId().toString(), "PAYMENT_SETTLED", eventPayload(payment));
    return payment;
  }

  @Transactional
  public PaymentTransaction compensate(Long paymentId, Throwable cause) {
    PaymentTransaction payment = lock(paymentId);
    if (payment.getStatus() == PaymentStatus.ROLLED_BACK) {
      return payment;
    }
    if (payment.getStatus() == PaymentStatus.COMPLETED) {
      EntryType reversal = payment.getType() == PaymentType.TOP_UP ? EntryType.DEBIT : EntryType.CREDIT;
      ledgerService.post(
          payment.getWallet(),
          reversal,
          payment.getAmount(),
          "Rollback " + payment.getType().name() + " " + payment.getId(),
          "payment:" + payment.getId() + ":compensate"
      );
    }
    payment.setStatus(PaymentStatus.ROLLED_BACK);
    payment.setFailureReason(cause == null || cause.getMessage() == null ? "Payment rolled back" : cause.getMessage());
    outboxService.emit("PAYMENT", payment.getId().toString(), "PAYMENT_COMPENSATED", eventPayload(payment));
    return payment;
  }

  @Transactional(readOnly = true)
  public PaymentDto getPayment(Long userId, Long paymentId) {
    if (userId == null || paymentId == null) {
      throw new ResourceNotFoundException("Payment not found");
    }
    return paymentRepository.findByIdAndUserId(paymentId, userId)
        .map(PaymentDto::from)
        .orElseThrow(() -> new ResourceNotFoundException("Payment not found"));
  }

  @Transactional(readOnly = true)
  public List<PaymentDto> listPayments(Long userId) {
    if (userId == null) {
      throw new BadRequestException("User is required");
    }
    return paymentRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
        .map(PaymentDto::from)
        .toList();
  }

  @Transactional(readOnly = true)
  public List<PaymentDto> listAllPayments() {
    return paymentRepository.findAll().stream()
        .sorted((left, right) -> {
          if (left.getCreatedAt() == null && right.getCreatedAt() == null) {
            return 0;
          }
          if (left.getCreatedAt() == null) {
            return 1;
          }
          if (right.getCreatedAt() == null) {
            return -1;
          }
          return right.getCreatedAt().compareTo(left.getCreatedAt());
        })
        .map(PaymentDto::from)
        .toList();
  }

  private Map<String, Object> eventPayload(PaymentTransaction payment) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("paymentId", payment.getId());
    payload.put("userId", payment.getUser() == null ? null : payment.getUser().getId());
    payload.put("walletId", payment.getWallet() == null ? null : payment.getWallet().getId());
    payload.put("type", payment.getType() == null ? null : payment.getType().name());
    payload.put("amount", payment.getAmount());
    payload.put("currency", payment.getCurrency());
    payload.put("status", payment.getStatus() == null ? null : payment.getStatus().name());
    payload.put("externalPaymentId", payment.getExternalPaymentId());
    return payload;
  }

  private PaymentTransaction lock(Long paymentId) {
    if (paymentId == null) {
      throw new ResourceNotFoundException("Payment not found");
    }
    return paymentRepository.findByIdForUpdate(paymentId)
        .orElseThrow(() -> new ResourceNotFoundException("Payment not found"));
  }
}
