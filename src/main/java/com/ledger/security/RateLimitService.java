package com.ledger.security;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import com.ledger.config.LedgerProperties;
import com.ledger.exception.RateLimitExceededException;

@Service
public class RateLimitService {

  private static final String KEY_PREFIX = "rl:";

  private final RedisTemplate<String, Object> redisTemplate;
  private final boolean useRedis;
  private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

  public RateLimitService(RedisTemplate<String, Object> redisTemplate, LedgerProperties properties) {
    this.redisTemplate = redisTemplate;
    this.useRedis = "redis".equalsIgnoreCase(properties.security().refreshTokenStore());
  }

  public void check(String key, int limit, Duration window) {
    if (useRedis) {
      checkRedis(key, limit, window);
      return;
    }
    checkMemory(key, limit, window);
  }

  private void checkRedis(String key, int limit, Duration window) {
    String redisKey = KEY_PREFIX + key;
    Long count = redisTemplate.opsForValue().increment(redisKey);
    Long ttl = redisTemplate.getExpire(redisKey);
    if (count != null && (count == 1L || ttl == null || ttl < 0)) {
      redisTemplate.expire(redisKey, window);
    }
    if (count != null && count > limit) {
      throw new RateLimitExceededException("Too many requests");
    }
  }

  private void checkMemory(String key, int limit, Duration window) {
    long now = System.currentTimeMillis();
    windows.compute(key, (ignored, current) -> {
      if (current == null || current.resetAtEpochMillis() <= now) {
        return new Window(1, now + window.toMillis());
      }
      if (current.count() >= limit) {
        throw new RateLimitExceededException("Too many requests");
      }
      return new Window(current.count() + 1, current.resetAtEpochMillis());
    });
  }

  private record Window(long count, long resetAtEpochMillis) {}
}
