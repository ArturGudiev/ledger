package com.ledger.messaging;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

@Component
public class NotificationConsumer {
  private static final Logger logger = Logger.getLogger(NotificationConsumer.class.getName());

  private static final String TOPIC = KafkaTopics.NOTIFICATIONS;
  private static final String RETRY_TOPIC = KafkaTopics.NOTIFICATIONS + "." + KafkaTopics.RETRY_SUFFIX;
  private static final String DLT_TOPIC = KafkaTopics.NOTIFICATIONS + "." + KafkaTopics.DLT_SUFFIX;

  private final KafkaTemplate<String, Object> kafkaTemplate;
  private final Counter consumed;
  private final Counter failed;

  public NotificationConsumer(
      KafkaTemplate<String, Object> kafkaTemplate,
      ObjectProvider<MeterRegistry> meterRegistry
  ) {
    this.kafkaTemplate = kafkaTemplate;
    MeterRegistry registry = meterRegistry.getIfAvailable(SimpleMeterRegistry::new);
    this.consumed = registry.counter("ledger.notifications.consumed");
    this.failed = registry.counter("ledger.notifications.failed");
  }

  @KafkaListener(topics = TOPIC)
  public void onEvent(OutboxMessage message, Acknowledgment acknowledgment) {
    try {
      handle(message);
      consumed.increment();
      acknowledgment.acknowledge();
    } catch (RuntimeException exception) {
      logger.log(Level.WARNING, "Notification handling failed for " + message.eventId(), exception);
      forward(message, RETRY_TOPIC);
      acknowledgment.acknowledge();
    }
  }

  @KafkaListener(topics = RETRY_TOPIC)
  public void onRetry(OutboxMessage message, Acknowledgment acknowledgment) {
    try {
      handle(message);
      consumed.increment();
      acknowledgment.acknowledge();
    } catch (RuntimeException exception) {
      logger.log(Level.WARNING, "Notification retry failed for " + message.eventId(), exception);
      forward(message, DLT_TOPIC);
      acknowledgment.acknowledge();
    }
  }

  @KafkaListener(topics = DLT_TOPIC)
  public void onDlt(OutboxMessage message, Acknowledgment acknowledgment) {
    failed.increment();
    logger.warning("Notification moved to dead letter: " + message.eventId());
    acknowledgment.acknowledge();
  }

  private void handle(OutboxMessage message) {
    String topic = KafkaTopics.topicFor(message.aggregateType());
    if (!TOPIC.equals(topic)) {
      forward(message, topic);
    }
  }

  private void forward(OutboxMessage message, String topic) {
    kafkaTemplate.send(topic, message.aggregateId(), message).join();
  }
}
