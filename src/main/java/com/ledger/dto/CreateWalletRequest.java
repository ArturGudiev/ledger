package com.ledger.dto;

import jakarta.validation.constraints.Pattern;

public record CreateWalletRequest (
  @Pattern(regexp = "^[A-Z]{3}$") String currency
){}
