package com.ledger.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ledger.dto.CreateWalletRequest;
import com.ledger.dto.WalletDto;
import com.ledger.exception.BadRequestException;
import com.ledger.exception.ConflictException;
import com.ledger.exception.ResourceNotFoundException;
import com.ledger.model.User;
import com.ledger.model.Wallet;
import com.ledger.model.Wallet.WalletStatus;
import com.ledger.repository.UserRepository;
import com.ledger.repository.WalletRepository;

@Service
public class WalletService {

  private final WalletRepository walletRepository;
  private final UserRepository userRepository;
  private final LedgerService ledgerService;
  private final FxService fxService;

  public WalletService(
      WalletRepository walletRepository,
      UserRepository userRepository,
      LedgerService ledgerService,
      FxService fxService
  ) {
    this.walletRepository = walletRepository;
    this.userRepository = userRepository;
    this.ledgerService = ledgerService;
    this.fxService = fxService;
  }

  @Transactional
  public WalletDto create(Long userId, CreateWalletRequest request) {
    if (request == null) {
      throw new BadRequestException("Currency is required");
    }
    String currency = currency(request.currency());
    if (!fxService.supportedCurrencies().contains(currency)) {
      throw new BadRequestException("Unsupported currency");
    }
    User user = userRepository.findById(userId)
        .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    if (walletRepository.existsByUserIdAndCurrency(userId, currency)) {
      throw new ConflictException("Wallet already exists for " + currency);
    }

    Wallet wallet = new Wallet();
    wallet.setUser(user);
    wallet.setCurrency(currency);
    wallet.setBalance(BigDecimal.ZERO);
    wallet.setStatus(WalletStatus.ACTIVE);
    walletRepository.save(wallet);
    return WalletDto.of(wallet, BigDecimal.ZERO);
  }

  @Transactional(readOnly = true)
  public List<WalletDto> list(Long userId) {
    return walletRepository.findByUserIdOrderByIdAsc(userId).stream()
        .map(wallet -> WalletDto.of(wallet, ledgerService.currentBalance(wallet)))
        .toList();
  }

  @Transactional(readOnly = true)
  public WalletDto get(Long userId, Long walletId) {
    Wallet wallet = findOwned(userId, walletId);
    return WalletDto.of(wallet, ledgerService.currentBalance(wallet));
  }

  @Transactional(readOnly = true)
  public Wallet requireOwned(Long userId, Long walletId) {
    Wallet wallet = findOwned(userId, walletId);
    if (wallet.getStatus() != WalletStatus.ACTIVE) {
      throw new ConflictException("Wallet is not active");
    }
    return wallet;
  }

  @Transactional(readOnly = true)
  public Wallet findOwned(Long userId, Long walletId) {
    if (userId == null || walletId == null) {
      throw new ResourceNotFoundException("Wallet not found");
    }
    return walletRepository.findByIdAndUserId(walletId, userId)
        .orElseThrow(() -> new ResourceNotFoundException("Wallet not found"));
  }

  private String currency(String value) {
    if (value == null || !value.trim().toUpperCase(Locale.ROOT).matches("[A-Z]{3}")) {
      throw new BadRequestException("Currency must be a 3-letter code");
    }
    return value.trim().toUpperCase(Locale.ROOT);
  }
}
