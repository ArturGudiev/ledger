package com.ledger.model;

import java.math.BigDecimal;
import java.time.Instant;

import com.ledger.model.enums.WalletStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

@Entity
@Table(
  name = "wallets",
  uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "currency"})
)
public class Wallet {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;
 
  @Column(nullable = false, length = 3, updatable = false)
  private String currency;

  @Column(nullable = false, precision = 28, scale = 12)
  private BigDecimal balance = BigDecimal.ZERO;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private WalletStatus status = WalletStatus.ACTIVE;

  @Version
  @Column(nullable = false)
  private long version;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @PrePersist
  private void onCreate() {
    Instant now = Instant.now();
    createdAt = now;
  }

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public User getUser() {
    return user;
  }

  public void setUser(User user) {
    this.user = user;
  }

  public String getCurrency() {
    return currency;
  }

  public void setCurrency(String currency) {
    this.currency = currency;
  }

  public BigDecimal getBalance() {
    return balance;
  }

  public void setBalance(BigDecimal balance) {
    this.balance = balance;
  }

  public WalletStatus getStatus() {
    return status;
  }

  public void setStatus(WalletStatus status) {
    this.status = status;
  }

  public long getVersion() {
    return version;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

}
