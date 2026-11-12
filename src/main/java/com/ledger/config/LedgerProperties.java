package com.ledger.config;

import java.math.BigDecimal;
import java.time.Duration;

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

  public record Idempotency() {}

  public record RateLimit() {}

  public record Fx() {}

  public record App() {}
}
