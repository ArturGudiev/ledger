package com.ledger.config;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ledger")
public record LedgerProperties(
    Security security,
    Idempotency idempotency,
    Transfer transfer,
    RateLimit rateLimit,
    Outbox outbox,
    Gateway gateway,
    Fx fx,
    App app
) {
  public record Gateway(String baseUrl, int connectTimeoutMs, int readTimeoutMs, boolean settleAsync) {}

  public record Security(
      String jwtSecret,
      Duration accessTokenTtl,
      Duration refreshTokenTtl,
      String refreshTokenStore
  ) {}

  public record Transfer(BigDecimal feePercent, BigDecimal minAmount, BigDecimal maxAmount) {}

  public record Outbox(long pollIntervalMs, int batchSize, int maxAttempts) {}

  public record Idempotency(Duration ttl) {
    public Idempotency {
      if (ttl == null) {
        ttl = Duration.ofHours(24);
      }
    }
  }

  public record RateLimit(
      int loginAttempts,
      Duration loginWindow,
      int registerAttempts,
      Duration registerWindow,
      int refreshAttempts,
      Duration refreshWindow
  ) {
    public RateLimit {
      if (loginAttempts <= 0) {
        loginAttempts = 10;
      }
      if (loginWindow == null) {
        loginWindow = Duration.ofMinutes(1);
      }
      if (registerAttempts <= 0) {
        registerAttempts = 5;
      }
      if (registerWindow == null) {
        registerWindow = Duration.ofHours(1);
      }
      if (refreshAttempts <= 0) {
        refreshAttempts = 30;
      }
      if (refreshWindow == null) {
        refreshWindow = Duration.ofMinutes(1);
      }
    }
  }

  public record Fx() {}

  public record App(
      String adminEmail,
      String adminPassword,
      String systemEmail,
      List<String> feeCurrencies
  ) {
    public App {
      if (adminEmail == null || adminEmail.isBlank()) {
        adminEmail = "admin@ledger.local";
      }
      if (adminPassword == null || adminPassword.isBlank()) {
        adminPassword = "ChangeMe123!";
      }
      if (systemEmail == null || systemEmail.isBlank()) {
        systemEmail = "system@ledger.local";
      }
      feeCurrencies = feeCurrencies == null ? List.of("USD", "EUR", "GBP") : List.copyOf(feeCurrencies);
    }
  }
}
