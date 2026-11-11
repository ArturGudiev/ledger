package com.ledger.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest (
  String firstName,
  String lastName,
  @NotBlank @Email @Size(max = 255) String email,
  @NotBlank @Size(min = 8, max = 72) String password
){}
