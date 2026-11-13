package com.ledger.gateway;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.ledger.config.LedgerProperties;
import com.ledger.exception.GatewayUnavailableException;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

@Component
public class MockGatewayClient {

  private final RestClient restClient;
  private final Timer gatewayTimer;

  public MockGatewayClient(LedgerProperties properties, MeterRegistry meterRegistry) {
    Duration connectTimeout = Duration.ofMillis(properties.gateway().connectTimeoutMs());
    Duration readTimeout = Duration.ofMillis(properties.gateway().readTimeoutMs());
    this.restClient = RestClient.builder()
        .baseUrl(properties.gateway().baseUrl())
        .requestFactory(RestClientFactory.withTimeouts(connectTimeout, readTimeout))
        .build();
    this.gatewayTimer = Timer.builder("ledger.gateway.requests").register(meterRegistry);
  }

  public GatewayResponse charge(String cardToken, String currency, BigDecimal amount) {
    return call("/charges", new GatewayChargeRequest(amount, currency, cardToken));
  }

  public GatewayResponse payout(String bankAccount, String currency, BigDecimal amount) {
    return call("/payouts", new GatewayChargeRequest(amount, currency, bankAccount));
  }

  private GatewayResponse call(String path, GatewayChargeRequest request) {
    long started = System.nanoTime();
    try {
      GatewayResponse response = restClient.post()
          .uri(path)
          .body(request)
          .retrieve()
          .body(GatewayResponse.class);
      if (response == null || response.id() == null || response.status() == null) {
        throw new GatewayUnavailableException("Gateway returned an empty response");
      }
      return response;
    } catch (GatewayUnavailableException exception) {
      throw exception;
    } catch (RestClientException exception) {
      GatewayUnavailableException failure = new GatewayUnavailableException("Gateway is unavailable");
      failure.initCause(exception);
      throw failure;
    } finally {
      recordDuration(System.nanoTime() - started);
    }
  }

  private void recordDuration(long nanos) {
    gatewayTimer.record(nanos, TimeUnit.NANOSECONDS);
  }
}
