package com.ledger.service;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import com.ledger.config.LedgerProperties;
import com.ledger.exception.BadRequestException;
import com.ledger.exception.ConflictException;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
public class IdempotencyService {

  private static final String KEY_PREFIX = "idem:";
  private static final String PENDING = "pending";
  private static final int PENDING_WAIT_MS = 3000;
  private static final int PENDING_POLL_MS = 100;

  private final RedisTemplate<String, Object> redisTemplate;
  private final ObjectMapper objectMapper;
  private final Duration ttl;
  private final boolean useRedis;
  private final ConcurrentHashMap<String, MemoryEntry> memory = new ConcurrentHashMap<>();

  public IdempotencyService(
      RedisTemplate<String, Object> redisTemplate,
      ObjectMapper objectMapper,
      LedgerProperties properties
  ) {
    this.redisTemplate = redisTemplate;
    this.objectMapper = objectMapper;
    this.ttl = properties.idempotency().ttl();
    this.useRedis = "redis".equalsIgnoreCase(properties.security().refreshTokenStore());
  }

  public ResponseEntity<?> execute(Long userId, String idempotencyKey, Supplier<ResponseEntity<?>> action) {
    if (idempotencyKey == null || idempotencyKey.isBlank()) {
      throw new BadRequestException("Idempotency key is required");
    }
    String cacheKey = key(userId, idempotencyKey);
    Optional<CachedResponse> cached = read(cacheKey);
    if (cached.isPresent()) {
      return reconstruct(cached.get());
    }
    if (!markPending(cacheKey)) {
      cached = waitForResult(cacheKey);
      if (cached.isPresent()) {
        return reconstruct(cached.get());
      }
      if (!markPending(cacheKey)) {
        return read(cacheKey)
            .map(this::reconstruct)
            .orElseThrow(() -> new ConflictException(
                "A request with this idempotency key is already in progress"
            ));
      }
    }

    try {
      ResponseEntity<?> response = action.get();
      store(cacheKey, new CachedResponse(response.getStatusCode().value(), bodyOf(response)));
      return response;
    } catch (RuntimeException exception) {
      clear(cacheKey);
      throw exception;
    }
  }

  private Optional<CachedResponse> read(String cacheKey) {
    String raw = load(cacheKey);
    if (raw == null || PENDING.equals(raw)) {
      return Optional.empty();
    }
    return Optional.of(deserialize(raw));
  }

  private Optional<CachedResponse> waitForResult(String cacheKey) {
    long deadline = System.currentTimeMillis() + PENDING_WAIT_MS;
    while (System.currentTimeMillis() < deadline) {
      String raw = load(cacheKey);
      if (raw == null) {
        return Optional.empty();
      }
      if (!PENDING.equals(raw)) {
        return Optional.of(deserialize(raw));
      }
      if (!sleep()) {
        break;
      }
    }
    return read(cacheKey);
  }

  private ResponseEntity<?> reconstruct(CachedResponse cached) {
    return ResponseEntity.status(cached.status()).body(deserializeBody(cached.body()));
  }

  private String bodyOf(ResponseEntity<?> response) {
    if (response.getBody() == null) {
      return null;
    }
    try {
      return objectMapper.writeValueAsString(response.getBody());
    } catch (JacksonException exception) {
      throw new IllegalStateException("Failed to serialize response body", exception);
    }
  }

  private void store(String cacheKey, CachedResponse cached) {
    String payload = serialize(cached);
    if (useRedis) {
      redisTemplate.opsForValue().set(cacheKey, payload, ttl);
      return;
    }
    memory.put(cacheKey, new MemoryEntry(payload, expiresAt()));
  }

  private String serialize(CachedResponse cached) {
    try {
      return objectMapper.writeValueAsString(cached);
    } catch (JacksonException exception) {
      throw new IllegalStateException("Failed to serialize cached response", exception);
    }
  }

  private CachedResponse deserialize(String json) {
    try {
      return objectMapper.readValue(json, CachedResponse.class);
    } catch (JacksonException exception) {
      throw new IllegalStateException("Failed to read cached response", exception);
    }
  }

  private Object deserializeBody(String body) {
    if (body == null) {
      return null;
    }
    try {
      return objectMapper.readValue(body, Object.class);
    } catch (JacksonException exception) {
      return body;
    }
  }

  private String key(Long userId, String idempotencyKey) {
    return KEY_PREFIX + userId + ":" + idempotencyKey;
  }

  private boolean markPending(String cacheKey) {
    if (useRedis) {
      Boolean created = redisTemplate.opsForValue().setIfAbsent(cacheKey, PENDING, ttl);
      return Boolean.TRUE.equals(created);
    }
    long now = System.currentTimeMillis();
    MemoryEntry pending = new MemoryEntry(PENDING, now + ttl.toMillis());
    while (true) {
      MemoryEntry existing = memory.putIfAbsent(cacheKey, pending);
      if (existing == null) {
        return true;
      }
      if (existing.expiresAtEpochMillis() > now) {
        return false;
      }
      if (memory.replace(cacheKey, existing, pending)) {
        return true;
      }
    }
  }

  private String load(String cacheKey) {
    if (useRedis) {
      return text(redisTemplate.opsForValue().get(cacheKey));
    }
    MemoryEntry entry = memory.get(cacheKey);
    if (entry == null) {
      return null;
    }
    if (entry.expiresAtEpochMillis() <= System.currentTimeMillis()) {
      memory.remove(cacheKey, entry);
      return null;
    }
    return entry.value();
  }

  private String text(Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof String raw) {
      return raw;
    }
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JacksonException exception) {
      throw new IllegalStateException("Failed to read idempotent response", exception);
    }
  }

  private void clear(String cacheKey) {
    if (useRedis) {
      redisTemplate.delete(cacheKey);
      return;
    }
    memory.remove(cacheKey);
  }

  private long expiresAt() {
    return System.currentTimeMillis() + ttl.toMillis();
  }

  private boolean sleep() {
    try {
      Thread.sleep(PENDING_POLL_MS);
      return true;
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      return false;
    }
  }

  private record CachedResponse(int status, String body) {}

  private record MemoryEntry(String value, long expiresAtEpochMillis) {}
}
