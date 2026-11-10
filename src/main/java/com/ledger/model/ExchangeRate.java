package com.ledger.model;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
  name = "exchange_rates",
  uniqueConstraints = @UniqueConstraint(columnNames = {"base_currency", "quote_currency"})
)
public class ExchangeRate {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "base_currency", nullable = false, length = 3, updatable = false)
  private String baseCurrency;

  @Column(name = "quote_currency", nullable = false, length = 3, updatable = false)
  private String quoteCurrency;

  @Column(nullable = false, precision = 28, scale = 12)
  private BigDecimal rate;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;
  
  @PreUpdate
  @PrePersist
  private void onPersist() {
      Instant now = Instant.now();
      updatedAt = now;
  }

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getBaseCurrency() {
    return baseCurrency;
  }

  public void setBaseCurrency(String baseCurrency) {
    this.baseCurrency = baseCurrency;
  }

  public String getQuoteCurrency() {
    return quoteCurrency;
  }

  public void setQuoteCurrency(String quoteCurrency) {
    this.quoteCurrency = quoteCurrency;
  }

  public BigDecimal getRate() {
    return rate;
  }

  public void setRate(BigDecimal rate) {
    this.rate = rate;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(Instant updatedAt) {
    this.updatedAt = updatedAt;
  }


}
