package com.ledger.service;

import java.util.Locale;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ledger.config.LedgerProperties;
import com.ledger.dto.AuthResponse;
import com.ledger.dto.LoginRequest;
import com.ledger.dto.RegisterRequest;
import com.ledger.dto.UserDto;
import com.ledger.exception.BadRequestException;
import com.ledger.exception.ConflictException;
import com.ledger.exception.ResourceNotFoundException;
import com.ledger.model.User;
import com.ledger.model.User.UserRole;
import com.ledger.model.User.UserStatus;
import com.ledger.repository.UserRepository;
import com.ledger.security.JwtService;
import com.ledger.security.RateLimitService;
import com.ledger.security.RefreshTokenRecord;
import com.ledger.security.RefreshTokenService;

@Service
public class AuthService {

  private static final int MAX_FAILED_LOGINS = 5;

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtService jwtService;
  private final RefreshTokenService refreshTokenService;
  private final RateLimitService rateLimitService;
  private final LedgerProperties properties;
  private final String dummyPasswordHash;

  public AuthService(
      UserRepository userRepository,
      PasswordEncoder passwordEncoder,
      JwtService jwtService,
      RefreshTokenService refreshTokenService,
      RateLimitService rateLimitService,
      LedgerProperties properties
  ) {
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
    this.jwtService = jwtService;
    this.refreshTokenService = refreshTokenService;
    this.rateLimitService = rateLimitService;
    this.properties = properties;
    this.dummyPasswordHash = passwordEncoder.encode("dummy-password");
  }

  @Transactional
  public AuthResponse register(RegisterRequest request) {
    if (request == null) {
      throw new BadRequestException("Registration is required");
    }
    String email = email(request.email());
    String password = password(request.password());
    rateLimitService.check("register:" + email, properties.rateLimit().registerAttempts(), properties.rateLimit().registerWindow());
    if (userRepository.existsByEmailIgnoreCase(email)) {
      throw new ConflictException("Email is already registered");
    }

    User user = new User();
    user.setEmail(email);
    user.setPasswordHash(passwordEncoder.encode(password));
    user.setFirstName(name(request.firstName()));
    user.setLastName(name(request.lastName()));
    user.setRole(UserRole.USER);
    user.setStatus(UserStatus.ACTIVE);
    user.setFailedLoginAttempts(0);
    try {
      userRepository.saveAndFlush(user);
    } catch (DataIntegrityViolationException exception) {
      throw new ConflictException("Email is already registered");
    }
    return issueTokens(user);
  }

  @Transactional
  public AuthResponse login(LoginRequest request) {
    if (request == null) {
      throw new BadRequestException("Login is required");
    }
    String email = email(request.email());
    String password = request.password() == null ? "" : request.password();
    rateLimitService.check("login:" + email, properties.rateLimit().loginAttempts(), properties.rateLimit().loginWindow());

    User user = userRepository.findByEmailIgnoreCase(email).orElse(null);
    String hash = user == null ? dummyPasswordHash : user.getPasswordHash();
    boolean matches = passwordEncoder.matches(password, hash);
    if (user == null || !matches || user.getRole() == UserRole.SYSTEM) {
      if (user != null && user.getRole() != UserRole.SYSTEM && user.getStatus() != UserStatus.LOCKED) {
        handleFailedLogin(user);
      }
      throw new BadCredentialsException("Invalid email or password");
    }
    if (user.getStatus() == UserStatus.LOCKED) {
      throw new BadRequestException("Account is locked");
    }
    resetFailedLogins(user);
    return issueTokens(user);
  }

  @Transactional(readOnly = true)
  public AuthResponse refresh(String refreshToken) {
    RefreshTokenRecord current = refreshTokenService.validate(refreshToken);
    rateLimitService.check(
        "refresh:" + current.userId(),
        properties.rateLimit().refreshAttempts(),
        properties.rateLimit().refreshWindow()
    );
    User user = userRepository.findById(current.userId())
        .orElseThrow(() -> new BadRequestException("Invalid refresh token"));
    if (user.getStatus() != UserStatus.ACTIVE || user.getRole() == UserRole.SYSTEM) {
      refreshTokenService.revoke(refreshToken);
      throw new BadRequestException("Invalid refresh token");
    }
    return issueTokens(user, refreshTokenService.rotate(refreshToken));
  }

  public void logout(String refreshToken) {
    refreshTokenService.revoke(refreshToken);
  }

  @Transactional(readOnly = true)
  public UserDto me(Long userId) {
    if (userId == null) {
      throw new ResourceNotFoundException("User not found");
    }
    return userRepository.findById(userId)
        .map(UserDto::from)
        .orElseThrow(() -> new ResourceNotFoundException("User not found"));
  }

  private void handleFailedLogin(User user) {
    int attempts = user.getFailedLoginAttempts() + 1;
    user.setFailedLoginAttempts(attempts);
    if (attempts >= MAX_FAILED_LOGINS) {
      user.setStatus(UserStatus.LOCKED);
    }
    userRepository.save(user);
  }

  private void resetFailedLogins(User user) {
    if (user.getFailedLoginAttempts() == 0) {
      return;
    }
    user.setFailedLoginAttempts(0);
    userRepository.save(user);
  }

  private AuthResponse issueTokens(User user) {
    return issueTokens(user, refreshTokenService.issue(user.getId()));
  }

  private AuthResponse issueTokens(User user, String refreshToken) {
    return AuthResponse.of(
        jwtService.generateAccessToken(user),
        jwtService.getAccessTokenTtlSeconds(),
        refreshToken,
        UserDto.from(user)
    );
  }

  private String email(String value) {
    if (value == null || value.isBlank() || !value.trim().contains("@")) {
      throw new BadRequestException("Email is required");
    }
    String email = value.trim().toLowerCase(Locale.ROOT);
    if (email.length() > 255) {
      throw new BadRequestException("Email is required");
    }
    return email;
  }

  private String password(String value) {
    if (value == null || value.length() < 8 || value.length() > 72) {
      throw new BadRequestException("Password must be 8 to 72 characters");
    }
    return value;
  }

  private String name(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    String name = value.trim();
    if (name.length() > 120) {
      throw new BadRequestException("Name is too long");
    }
    return name;
  }
}
