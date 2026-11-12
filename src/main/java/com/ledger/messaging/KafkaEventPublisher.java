package com.ledger.messaging;

import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import com.ledger.model.OutboxEvent;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
public class KafkaEventPublisher {
  private static final Duration SEND_TIMEOUT = Duration.ofSeconds(5);

  private final KafkaTemplate<String, Object> kafkaTemplate;
  private final ObjectMapper objectMapper;
  private final Counter publishedCounter;
  private final Counter publishErrors;

  public KafkaEventPublisher(
      KafkaTemplate<String, Object> kafkaTemplate,
      ObjectMapper objectMapper,
      ObjectProvider<MeterRegistry> meterRegistry
  ) {
    this.kafkaTemplate = kafkaTemplate;
    this.objectMapper = objectMapper;
    MeterRegistry registry = meterRegistry.getIfAvailable(SimpleMeterRegistry::new);
    this.publishedCounter = registry.counter("ledger.outbox.published");
    this.publishErrors = registry.counter("ledger.outbox.publish.errors");
  }

  public void publish(OutboxEvent event) {
    try {
      OutboxMessage message = new OutboxMessage(
          event.getId() == null ? null : event.getId().toString(),
          event.getAggregateType(),
          event.getAggregateId(),
          event.getEventType(),
          parsePayload(event.getPayload()),
          event.getCreatedAt()
      );
      kafkaTemplate.send(KafkaTopics.topicFor(event.getAggregateType()), event.getAggregateId(), message)
          .get(SEND_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
      publishedCounter.increment();
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      publishErrors.increment();
      throw new IllegalStateException("Interrupted while publishing outbox event " + event.getId(), exception);
    } catch (RuntimeException | TimeoutException | ExecutionException exception) {
      publishErrors.increment();
      throw new IllegalStateException("Failed to publish outbox event " + event.getId(), exception);
    }
  }

  private Object parsePayload(String payload) {
    if (payload == null || payload.isBlank()) {
      return null;
    }
    try {
      return objectMapper.readValue(payload, Object.class);
    } catch (JacksonException exception) {
      throw new IllegalStateException("Failed to parse outbox payload", exception);
    }
  }
}
