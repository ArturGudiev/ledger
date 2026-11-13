package com.ledger.service;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.stereotype.Service;

import com.ledger.dto.PaymentDto;
import com.ledger.dto.TopUpRequest;
import com.ledger.dto.WithdrawRequest;
import com.ledger.exception.BadRequestException;
import com.ledger.exception.ResourceNotFoundException;
import com.ledger.gateway.GatewayResponse;
import com.ledger.gateway.MockGatewayClient;
import com.ledger.model.PaymentTransaction;
import com.ledger.model.PaymentTransaction.PaymentType;
import com.ledger.model.User;
import com.ledger.model.Wallet;
import com.ledger.repository.WalletRepository;

@Service
public class PaymentSagaService {

  private final WalletRepository walletRepository;
  private final MockGatewayClient gatewayClient;
  private final PaymentSagaSteps steps;

  public PaymentSagaService(
      WalletRepository walletRepository,
      MockGatewayClient gatewayClient,
      PaymentSagaSteps steps
  ) {
    this.walletRepository = walletRepository;
    this.gatewayClient = gatewayClient;
    this.steps = steps;
  }

  public PaymentDto topUp(User user, Long walletId, TopUpRequest request, String idempotencyKey) {
    if (request == null || request.cardToken() == null || request.cardToken().isBlank()) {
      throw new BadRequestException("Card token is required");
    }
    PaymentDto pending = runTopUp(user, walletId, request.amount(), idempotencyKey);
    try {
      GatewayResponse response = gatewayClient.charge(request.cardToken(), pending.currency(), pending.amount());
      return PaymentDto.from(steps.settle(pending.id(), response));
    } catch (RuntimeException exception) {
      rollBack(pending.id(), exception);
      throw exception;
    }
  }

  public PaymentDto withdraw(User user, Long walletId, WithdrawRequest request, String idempotencyKey) {
    if (request == null || request.bankAccount() == null || request.bankAccount().isBlank()) {
      throw new BadRequestException("Bank account is required");
    }
    PaymentDto pending = runWithdraw(user, walletId, request.amount(), idempotencyKey);
    try {
      GatewayResponse response = gatewayClient.payout(request.bankAccount(), pending.currency(), pending.amount());
      return PaymentDto.from(steps.settle(pending.id(), response));
    } catch (RuntimeException exception) {
      rollBack(pending.id(), exception);
      throw exception;
    }
  }

  private PaymentDto runTopUp(User user, Long walletId, BigDecimal amount, String idempotencyKey) {
    return PaymentDto.from(steps.initiate(ownedWallet(user, walletId), payment(user, PaymentType.TOP_UP, amount, idempotencyKey)));
  }

  private PaymentDto runWithdraw(User user, Long walletId, BigDecimal amount, String idempotencyKey) {
    return PaymentDto.from(steps.initiate(ownedWallet(user, walletId), payment(user, PaymentType.WITHDRAW, amount, idempotencyKey)));
  }

  public PaymentDto get(Long userId, Long paymentId) {
    return steps.getPayment(userId, paymentId);
  }

  public List<PaymentDto> list(Long userId) {
    return steps.listPayments(userId);
  }

  public List<PaymentDto> listAll() {
    return steps.listAllPayments();
  }

  private Wallet ownedWallet(User user, Long walletId) {
    if (user == null || user.getId() == null || walletId == null) {
      throw new ResourceNotFoundException("Wallet not found");
    }
    return walletRepository.findByIdAndUserId(walletId, user.getId())
        .orElseThrow(() -> new ResourceNotFoundException("Wallet not found"));
  }

  private PaymentTransaction payment(User user, PaymentType type, BigDecimal amount, String idempotencyKey) {
    PaymentTransaction payment = new PaymentTransaction();
    payment.setUser(user);
    payment.setType(type);
    payment.setAmount(amount);
    payment.setIdempotencyKey(idempotencyKey);
    return payment;
  }

  private void rollBack(Long paymentId, RuntimeException exception) {
    try {
      steps.compensate(paymentId, exception);
    } catch (RuntimeException rollbackFailure) {
      exception.addSuppressed(rollbackFailure);
    }
  }
}
