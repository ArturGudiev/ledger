package com.ledger.config;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.ledger.model.User;
import com.ledger.model.User.UserRole;
import com.ledger.model.User.UserStatus;
import com.ledger.model.Wallet;
import com.ledger.model.Wallet.WalletStatus;
import com.ledger.repository.UserRepository;
import com.ledger.repository.WalletRepository;
import com.ledger.service.FxService;

@Component
public class DataSeeder implements CommandLineRunner {

  private static final Logger log = Logger.getLogger(DataSeeder.class.getName());

  private final UserRepository userRepository;
  private final WalletRepository walletRepository;
  private final FxService fxService;
  private final PasswordEncoder passwordEncoder;
  private final LedgerProperties properties;

  public DataSeeder(
      UserRepository userRepository,
      WalletRepository walletRepository,
      FxService fxService,
      PasswordEncoder passwordEncoder,
      LedgerProperties properties
  ) {
    this.userRepository = userRepository;
    this.walletRepository = walletRepository;
    this.fxService = fxService;
    this.passwordEncoder = passwordEncoder;
    this.properties = properties;
  }

  @Override
  public void run(String... args) {
    seedAdmin();
    seedFeeWallets();
  }

  private void seedAdmin() {
    String email = email(properties.app().adminEmail());
    if (userRepository.existsByEmailIgnoreCase(email)) {
      return;
    }
    User admin = new User();
    admin.setEmail(email);
    admin.setPasswordHash(passwordEncoder.encode(properties.app().adminPassword()));
    admin.setFirstName("Ledger");
    admin.setLastName("Admin");
    admin.setRole(UserRole.ADMIN);
    admin.setStatus(UserStatus.ACTIVE);
    userRepository.save(admin);
    log.info("Seeded admin user " + email);
  }

  private void seedFeeWallets() {
    String email = email(properties.app().systemEmail());
    User system = userRepository.findByEmailIgnoreCase(email).orElseGet(() -> {
      User created = new User();
      created.setEmail(email);
      created.setPasswordHash(passwordEncoder.encode(UUID.randomUUID().toString()));
      created.setFirstName("Fee");
      created.setLastName("Account");
      created.setRole(UserRole.SYSTEM);
      created.setStatus(UserStatus.ACTIVE);
      return userRepository.save(created);
    });
    if (system.getRole() != UserRole.SYSTEM) {
      throw new IllegalStateException("Fee wallet owner " + email + " must have the SYSTEM role");
    }

    fxService.refresh();
    for (String currency : feeCurrencies()) {
      if (walletRepository.existsByUserIdAndCurrency(system.getId(), currency)) {
        continue;
      }
      Wallet wallet = new Wallet();
      wallet.setUser(system);
      wallet.setCurrency(currency);
      wallet.setBalance(BigDecimal.ZERO);
      wallet.setStatus(WalletStatus.ACTIVE);
      walletRepository.save(wallet);
      log.info("Seeded fee wallet " + currency + " for " + email);
    }
  }

  private Set<String> feeCurrencies() {
    Set<String> currencies = new LinkedHashSet<>(fxService.supportedCurrencies());
    for (String currency : properties.app().feeCurrencies()) {
      if (currency == null || currency.isBlank()) {
        continue;
      }
      String canonical = currency.trim().toUpperCase(Locale.ROOT);
      if (canonical.matches("[A-Z]{3}")) {
        currencies.add(canonical);
      }
    }
    return currencies;
  }

  private String email(String value) {
    return value.trim().toLowerCase(Locale.ROOT);
  }
}
