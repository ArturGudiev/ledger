package com.ledger.security;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import com.ledger.config.LedgerProperties;
import com.ledger.exception.BadRequestException;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
public class RefreshTokenService {

  private static final String TOKEN_KEY_PREFIX = "rt:";
  private static final String FAMILY_KEY_PREFIX = "rtf:";

  private final RedisTemplate<String, Object> redisTemplate;
  private final ObjectMapper objectMapper;
  private final Duration ttl;
  private final boolean useRedis;
  private final SecureRandom secureRandom = new SecureRandom();
  private final ConcurrentHashMap<String, TokenEntry> memory = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, Set<String>> memoryFamilies = new ConcurrentHashMap<>();

  public RefreshTokenService(
      RedisTemplate<String, Object> redisTemplate,
      ObjectMapper objectMapper,
      LedgerProperties properties
  ) {
    this.redisTemplate = redisTemplate;
    this.objectMapper = objectMapper;
    this.ttl = properties.security().refreshTokenTtl();
    this.useRedis = "redis".equalsIgnoreCase(properties.security().refreshTokenStore());
  }

  public String issue(Long userId) {
    return issueInFamily(randomId(16), userId);
  }

  private String issueInFamily(String familyId, Long userId) {
    String tokenId = randomId(16);
    long expiresAt = Instant.now().plus(ttl).getEpochSecond();
    TokenEntry entry = new TokenEntry(familyId, tokenId, userId, expiresAt);
    save(entry);
    addToFamily(familyId, tokenId);
    return new RefreshTokenRecord(familyId, tokenId, userId, expiresAt).asString();
  }

  public String rotate(String token) {
    RefreshTokenRecord current = validate(token);
    revokeOne(current.tokenId(), current.familyId());
    return issueInFamily(current.familyId(), current.userId());
  }

  public void revoke(String token) {
    RefreshTokenRecord presented = read(token);
    revokeFamily(presented.familyId());
  }

  public RefreshTokenRecord validate(String token) {
    RefreshTokenRecord presented = read(token);
    TokenEntry entry = find(presented.tokenId());
    if (entry == null) {
      if (familyContains(presented.familyId(), presented.tokenId())) {
        revokeFamily(presented.familyId());
        throw new RefreshTokenReuseException(
            presented.familyId(),
            presented.tokenId(),
            presented.userId()
        );
      }
      throw new BadRequestException("Invalid refresh token");
    }
    if (!entry.familyId().equals(presented.familyId())) {
      throw new BadRequestException("Invalid refresh token");
    }
    if (entry.expiresAtEpochSeconds() <= Instant.now().getEpochSecond()) {
      forget(entry.tokenId(), entry.familyId());
      throw new BadRequestException("Refresh token expired");
    }
    return new RefreshTokenRecord(
        entry.familyId(),
        entry.tokenId(),
        entry.userId(),
        entry.expiresAtEpochSeconds()
    );
  }

  private void revokeOne(String tokenId, String familyId) {
    delete(tokenId);
    addToFamily(familyId, tokenId);
  }

  private void revokeFamily(String familyId) {
    if (useRedis) {
      String familyKey = familyKey(familyId);
      Set<Object> tokenIds = redisTemplate.opsForSet().members(familyKey);
      if (tokenIds != null) {
        for (Object tokenId : tokenIds) {
          redisTemplate.delete(tokenKey(String.valueOf(tokenId)));
        }
      }
      redisTemplate.delete(familyKey);
      return;
    }
    Set<String> tokenIds = memoryFamilies.remove(familyId);
    if (tokenIds != null) {
      tokenIds.forEach(memory::remove);
    }
  }

  private void forget(String tokenId, String familyId) {
    delete(tokenId);
    if (useRedis) {
      redisTemplate.opsForSet().remove(familyKey(familyId), tokenId);
      return;
    }
    Set<String> tokenIds = memoryFamilies.get(familyId);
    if (tokenIds != null) {
      tokenIds.remove(tokenId);
    }
  }

  private void save(TokenEntry entry) {
    if (useRedis) {
      redisTemplate.opsForValue().set(tokenKey(entry.tokenId()), toJson(entry), ttl);
      return;
    }
    memory.put(entry.tokenId(), entry);
  }

  private TokenEntry find(String tokenId) {
    if (useRedis) {
      Object value = redisTemplate.opsForValue().get(tokenKey(tokenId));
      if (value == null) {
        return null;
      }
      return fromJson(value instanceof String json ? json : objectMapper.writeValueAsString(value));
    }
    return memory.get(tokenId);
  }

  private void delete(String tokenId) {
    if (useRedis) {
      redisTemplate.delete(tokenKey(tokenId));
      return;
    }
    memory.remove(tokenId);
  }

  private void addToFamily(String familyId, String tokenId) {
    if (useRedis) {
      String familyKey = familyKey(familyId);
      redisTemplate.opsForSet().add(familyKey, tokenId);
      redisTemplate.expire(familyKey, ttl);
      return;
    }
    memoryFamilies.computeIfAbsent(familyId, ignored -> ConcurrentHashMap.newKeySet()).add(tokenId);
  }

  private boolean familyContains(String familyId, String tokenId) {
    if (useRedis) {
      Boolean member = redisTemplate.opsForSet().isMember(familyKey(familyId), tokenId);
      return Boolean.TRUE.equals(member);
    }
    Set<String> tokenIds = memoryFamilies.get(familyId);
    return tokenIds != null && tokenIds.contains(tokenId);
  }

  private RefreshTokenRecord read(String token) {
    String[] parts = token == null ? new String[0] : token.split(":", 4);
    if (parts.length != 4) {
      throw new BadRequestException("Invalid refresh token");
    }
    try {
      return new RefreshTokenRecord(parts[0], parts[1], Long.valueOf(parts[2]), Long.parseLong(parts[3]));
    } catch (NumberFormatException exception) {
      throw new BadRequestException("Invalid refresh token");
    }
  }

  private String toJson(TokenEntry entry) {
    try {
      return objectMapper.writeValueAsString(entry);
    } catch (JacksonException exception) {
      throw new IllegalStateException("Failed to serialize refresh token", exception);
    }
  }

  private TokenEntry fromJson(String json) {
    try {
      return objectMapper.readValue(json, TokenEntry.class);
    } catch (JacksonException exception) {
      throw new IllegalStateException("Failed to read refresh token", exception);
    }
  }

  private String randomId(int size) {
    byte[] bytes = new byte[size];
    secureRandom.nextBytes(bytes);
    return HexFormat.of().formatHex(bytes);
  }

  private static String tokenKey(String tokenId) {
    return TOKEN_KEY_PREFIX + tokenId;
  }

  private static String familyKey(String familyId) {
    return FAMILY_KEY_PREFIX + familyId;
  }

  private record TokenEntry(String familyId, String tokenId, Long userId, long expiresAtEpochSeconds) {}
}
