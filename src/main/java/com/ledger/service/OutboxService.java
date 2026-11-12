package com.ledger.service;

import java.time.Instant;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.ledger.model.OutboxEvent;
import com.ledger.repository.OutboxRepository;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
public class OutboxService {
  private final OutboxRepository outboxRepository;
  private final ObjectMapper objectMapper;

  public OutboxService(OutboxRepository outboxRepository, ObjectMapper objectMapper) {
    this.outboxRepository = outboxRepository;
    this.objectMapper = objectMapper;
  }

  public void emit(String aggregateType, String aggregateId, String eventType, Map<String, Object> payload) {
    OutboxEvent event = new OutboxEvent();
    event.setAggregateType(aggregateType);
    event.setAggregateId(aggregateId);
    event.setEventType(eventType);
    event.setPayload(writeJson(payload));
    event.setAvailableAt(Instant.now());
    outboxRepository.save(event);
  }

  private String writeJson(Map<String, Object> payload) {
    try {
      return objectMapper.writeValueAsString(payload);
    } catch (JacksonException exception) {
      throw new IllegalStateException("Failed to serialize outbox payload", exception);
    }
  }
}
