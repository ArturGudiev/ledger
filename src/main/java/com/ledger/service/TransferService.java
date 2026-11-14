package com.ledger.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ledger.config.LedgerProperties;
import com.ledger.dto.TransferDto;
import com.ledger.dto.TransferRequest;
import com.ledger.exception.BadRequestException;
import com.ledger.exception.ConflictException;
import com.ledger.exception.ResourceNotFoundException;
import com.ledger.model.LedgerEntry.EntryType;
import com.ledger.model.Transfer;
import com.ledger.model.Transfer.TransferStatus;
import com.ledger.model.User;
import com.ledger.model.User.UserStatus;
import com.ledger.model.Wallet;
import com.ledger.model.Wallet.WalletStatus;
import com.ledger.repository.TransferRepository;
import com.ledger.repository.WalletRepository;

@Service
public class TransferService {

  private static final int MONEY_SCALE = 8;
  private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

  private final TransferRepository transferRepository;
  private final WalletRepository walletRepository;
  private final LedgerService ledgerService;
  private final FxService fxService;
  private final OutboxService outboxService;
  private final LedgerProperties properties;

  public TransferService(
      TransferRepository transferRepository,
      WalletRepository walletRepository,
      LedgerService ledgerService,
      FxService fxService,
      OutboxService outboxService,
      LedgerProperties properties
  ) {
    this.transferRepository = transferRepository;
    this.walletRepository = walletRepository;
    this.ledgerService = ledgerService;
    this.fxService = fxService;
    this.outboxService = outboxService;
    this.properties = properties;
  }

  @Transactional
  public TransferDto transfer(User user, TransferRequest request) {
    if (user == null || user.getId() == null) {
      throw new BadRequestException("User is required");
    }
    if (user.getStatus() != UserStatus.ACTIVE) {
      throw new ConflictException("Account is not active");
    }
    if (request == null || request.fromWalletId() == null || request.toWalletId() == null) {
      throw new BadRequestException("Source and destination wallets are required");
    }
    if (request.fromWalletId().equals(request.toWalletId())) {
      throw new BadRequestException("Source and destination wallets must differ");
    }

    BigDecimal amount = money(request.amount());
    BigDecimal minimum = properties.transfer().minAmount();
    BigDecimal maximum = properties.transfer().maxAmount();
    if (minimum != null && amount.compareTo(minimum) < 0) {
      throw new BadRequestException("Amount is below the minimum");
    }
    if (maximum != null && amount.compareTo(maximum) > 0) {
      throw new BadRequestException("Amount exceeds the maximum");
    }
    BigDecimal fee = computeFee(amount);
    if (fee.compareTo(amount) >= 0) {
      throw new BadRequestException("Fee must be smaller than the amount");
    }

    List<Long> walletIds = List.of(request.fromWalletId(), request.toWalletId());
    Map<Long, Wallet> locked = new LinkedHashMap<>();
    walletIds.stream().distinct().sorted().forEach(id -> locked.put(id, lock(id)));
    Wallet from = locked.get(request.fromWalletId());
    Wallet to = locked.get(request.toWalletId());
    if (from.getUser() == null || !user.getId().equals(from.getUser().getId())) {
      throw new ResourceNotFoundException("Wallet not found");
    }
    requireActive(from);
    requireActive(to);

    BigDecimal net = amount.subtract(fee);
    BigDecimal fxRate = fxService.rate(from.getCurrency(), to.getCurrency());
    BigDecimal converted = fxService.convert(net, from.getCurrency(), to.getCurrency())
        .setScale(MONEY_SCALE, RoundingMode.HALF_EVEN);
    if (converted.signum() <= 0) {
      throw new BadRequestException("Converted amount must be positive");
    }

    Wallet feeWallet = null;
    if (fee.signum() > 0) {
      feeWallet = walletRepository.findSystemWallet(from.getCurrency(), systemEmail())
          .orElseThrow(() -> new BadRequestException("No fee wallet for " + from.getCurrency()));
      requireActive(feeWallet);
    }

    Transfer transfer = new Transfer();
    transfer.setUser(user);
    transfer.setFromWallet(from);
    transfer.setToWallet(to);
    transfer.setCurrency(from.getCurrency());
    transfer.setAmount(amount);
    transfer.setFee(fee);
    transfer.setFxRate(fxRate);
    transfer.setConvertedAmount(converted);
    transfer.setStatus(TransferStatus.PENDING);
    transfer.setIdempotencyKey(UUID.randomUUID().toString());
    transferRepository.save(transfer);

    String operationPrefix = "transfer:" + transfer.getId();
    ledgerService.post(
        from,
        EntryType.DEBIT,
        amount,
        "Transfer to wallet " + to.getId(),
        operationPrefix + ":debit"
    );
    ledgerService.post(
        to,
        EntryType.CREDIT,
        converted,
        "Transfer from wallet " + from.getId(),
        operationPrefix + ":credit"
    );
    if (feeWallet != null) {
      ledgerService.post(
          feeWallet,
          EntryType.CREDIT,
          fee,
          "Transfer fee",
          operationPrefix + ":fee"
      );
    }

    transfer.setStatus(TransferStatus.COMPLETED);
    transfer.setCompletedAt(Instant.now());
    outboxService.emit("TRANSFER", transfer.getId().toString(), "TRANSFER_COMPLETED", eventPayload(transfer));
    return TransferDto.from(transfer);
  }

  @Transactional(readOnly = true)
  public TransferDto get(Long userId, Long transferId) {
    if (userId == null || transferId == null) {
      throw new ResourceNotFoundException("Transfer not found");
    }
    return transferRepository.findByIdAndUserId(transferId, userId)
        .map(TransferDto::from)
        .orElseThrow(() -> new ResourceNotFoundException("Transfer not found"));
  }

  @Transactional(readOnly = true)
  public List<TransferDto> list(Long userId) {
    if (userId == null) {
      throw new BadRequestException("User is required");
    }
    return transferRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
        .map(TransferDto::from)
        .toList();
  }

  @Transactional(readOnly = true)
  public List<TransferDto> listAll() {
    return transferRepository.findAll().stream()
        .sorted(Comparator.comparing(Transfer::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
        .map(TransferDto::from)
        .toList();
  }

  private BigDecimal computeFee(BigDecimal amount) {
    BigDecimal percent = properties.transfer().feePercent();
    if (percent == null || percent.signum() == 0) {
      return BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_EVEN);
    }
    if (percent.signum() < 0) {
      throw new IllegalStateException("Fee percent cannot be negative");
    }
    return amount.multiply(percent).divide(ONE_HUNDRED, MONEY_SCALE, RoundingMode.HALF_EVEN);
  }

  private void requireActive(Wallet wallet) {
    if (wallet.getStatus() != WalletStatus.ACTIVE) {
      throw new ConflictException("Wallet is not active");
    }
  }

  private Map<String, Object> eventPayload(Transfer transfer) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("transferId", transfer.getId());
    payload.put("userId", transfer.getUser().getId());
    payload.put("fromWalletId", transfer.getFromWallet() == null ? null : transfer.getFromWallet().getId());
    payload.put("toWalletId", transfer.getToWallet() == null ? null : transfer.getToWallet().getId());
    payload.put("currency", transfer.getCurrency());
    payload.put("amount", transfer.getAmount());
    payload.put("convertedAmount", transfer.getConvertedAmount());
    payload.put("fxRate", transfer.getFxRate());
    payload.put("fee", transfer.getFee());
    payload.put("status", transfer.getStatus().name());
    return payload;
  }

  private Wallet lock(Long walletId) {
    return walletRepository.findByIdForUpdate(walletId)
        .orElseThrow(() -> new ResourceNotFoundException("Wallet not found"));
  }

  private BigDecimal money(BigDecimal amount) {
    if (amount == null || amount.signum() <= 0) {
      throw new BadRequestException("Amount must be positive");
    }
    return amount.setScale(MONEY_SCALE, RoundingMode.HALF_EVEN);
  }

  private String systemEmail() {
    return properties.app().systemEmail().trim().toLowerCase(Locale.ROOT);
  }
}
