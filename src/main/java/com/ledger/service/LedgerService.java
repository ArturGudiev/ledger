package com.ledger.service;

import java.math.BigDecimal;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ledger.exception.BadRequestException;
import com.ledger.exception.InsufficientFundsException;
import com.ledger.model.LedgerEntry;
import com.ledger.model.LedgerEntry.LedgerEntryType;
import com.ledger.model.Wallet;
import com.ledger.repository.LedgerEntryRepository;

@Service
public class LedgerService {

  private final LedgerEntryRepository ledgerEntryRepository;

  public LedgerService(LedgerEntryRepository ledgerEntryRepository) {
    this.ledgerEntryRepository = ledgerEntryRepository;
  }

  @Transactional(readOnly = true)
  public BigDecimal currentBalance(Wallet wallet) {
    return ledgerEntryRepository.findFirstByWalletIdOrderByIdDesc(wallet.getId())
        .map(LedgerEntry::getBalanceAfter)
        .orElse(BigDecimal.ZERO);
  }

  @Transactional
  public BigDecimal post(
      Wallet wallet,
      LedgerEntryType entryType,
      BigDecimal amount,
      String description,
      String operationKey
  ) {
    if (amount == null || amount.signum() <= 0) {
      throw new BadRequestException("Amount must be positive");
    }
    if (ledgerEntryRepository.existsByWalletIdAndOperationKey(wallet.getId(), operationKey)) {
      return currentBalance(wallet);
    }

    BigDecimal balance = currentBalance(wallet);
    BigDecimal balanceAfter = switch (entryType) {
      case CREDIT -> balance.add(amount);
      case DEBIT -> balance.subtract(amount);
    };
    if (balanceAfter.signum() < 0) {
      throw new InsufficientFundsException("Insufficient funds");
    }

    LedgerEntry entry = new LedgerEntry();
    entry.setWallet(wallet);
    entry.setEntryType(entryType);
    entry.setAmount(amount);
    entry.setBalanceAfter(balanceAfter);
    entry.setCurrency(wallet.getCurrency());
    entry.setDescription(description);
    entry.setOperationKey(operationKey);
    ledgerEntryRepository.save(entry);
    wallet.setBalance(balanceAfter);
    return balanceAfter;
  }
}