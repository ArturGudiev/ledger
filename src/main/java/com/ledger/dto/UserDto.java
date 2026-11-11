package com.ledger.dto;

import java.time.Instant;

import com.ledger.model.User;

public record UserDto (
  Long id,
  String email,
  String firstName,
  String lastName,
  String role,
  String status,
  Instant createdAt
) {
  public static UserDto from(User user) {
    return new UserDto(
      user.getId(),
      user.getEmail(),
      user.getFirstName(),
      user.getLastName(),
      user.getRole().name(),
      user.getStatus().name(),
      user.getCreatedAt()
    );
  }
}
