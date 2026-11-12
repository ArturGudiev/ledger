package com.ledger.messaging;

import java.time.Duration;
import java.time.Instant;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.ledger.config.LedgerProperties;
import com.ledger.model.OutboxEvent;
import com.ledger.model.OutboxEvent.OutboxStatus;
import com.ledger.repository.OutboxRepository;

import io.micrometer.core.instrument.MeterRegistry;

@Component
public class OutboxPoller {
  private static final Logger logger = Logger.getLogger(OutboxPoller.class.getName());

  private final OutboxRepository outboxRepository;
  private final KafkaEventPublisher publisher;
  private final LedgerProperties properties;
  private final MeterRegistry meterRegistry;

  public OutboxPoller(
      OutboxRepository outboxRepository,
      KafkaEventPublisher publisher,
      LedgerProperties properties,
      MeterRegistry meterRegistry
  ) {
    this.outboxRepository = outboxRepository;
    this.publisher = publisher;
    this.properties = properties;
    this.meterRegistry = meterRegistry;
  }

  @Scheduled(fixedDelayString = "${ledger.outbox.poll-interval-ms:1000}")
  @Transactional
  public void publishPending() {
    for (OutboxEvent event : outboxRepository.lockNextBatch(
        Instant.now(),
        properties.outbox().batchSize()
    )) {
      try {
        publisher.publish(event);
        event.setStatus(OutboxStatus.PUBLISHED);
        event.setPublishedAt(Instant.now());
        meterRegistry.counter("ledger.outbox.poller.published").increment();
      } catch (RuntimeException exception) {
        logger.log(Level.WARNING, "Failed to publish outbox event " + event.getId(), exception);
        int attempts = event.getAttempts() + 1;
        event.setAttempts(attempts);
        if (attempts >= properties.outbox().maxAttempts()) {
          event.setStatus(OutboxStatus.FAILED);
        } else {
          event.setAvailableAt(Instant.now().plus(backoff(attempts)));
        }
        meterRegistry.counter("ledger.outbox.poller.failed").increment();
      }
    }
  }

  private Duration backoff(int attempts) {
    int shift = Math.min(Math.max(attempts - 1, 0), 16);
    long delay = properties.outbox().pollIntervalMs() * (1L << shift);
    return Duration.ofMillis(delay);
  }
}
