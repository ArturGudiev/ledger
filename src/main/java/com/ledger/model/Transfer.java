package com.ledger.model;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "transfers")
public class Transfer {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "idempotency_key", nullable = false, length = 64, unique = true)
  private String idempotencyKey;

  @ManyToOne(optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @ManyToOne(optional = true)
  @JoinColumn(name = "from_wallet_id", nullable = true)
  private Wallet fromWallet;

  @ManyToOne(optional = true)
  @JoinColumn(name = "to_wallet_id", nullable = true)
  private Wallet toWallet;

  @Column(nullable = false, length = 3)
  private String currency;

  @Column(nullable = false, precision = 28, scale = 12)
  private BigDecimal amount;

  @Column(name = "converted_amount", nullable = true, precision = 28, scale = 12)
  private BigDecimal convertedAmount;

  @Column(name = "fx_rate", nullable = true, precision = 28, scale = 12)
  private BigDecimal fxRate;

  @Column(nullable = false, precision = 28, scale = 12)
  private BigDecimal fee;

  @Column(nullable = false, length = 16)
  private String status;

  @Column(length = 1000)
  private String errorMessage;

  @Column(nullable = false)
  private Long version;

  @Column(nullable = false, name = "created_at")
  private Instant createdAt;

  @Column(name = "completed_at")
  private Instant completedAt;

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getIdempotencyKey() {
    return idempotencyKey;
  }

  public void setIdempotencyKey(String idempotencyKey) {
    this.idempotencyKey = idempotencyKey;
  }

  public User getUser() {
    return user;
  }

  public void setUser(User user) {
    this.user = user;
  }

  public Wallet getFromWallet() {
    return fromWallet;
  }

  public void setFromWallet(Wallet fromWallet) {
    this.fromWallet = fromWallet;
  }

  public Wallet getToWallet() {
    return toWallet;
  }

  public void setToWallet(Wallet toWallet) {
    this.toWallet = toWallet;
  }

  public String getCurrency() {
    return currency;
  }

  public void setCurrency(String currency) {
    this.currency = currency;
  }

  public BigDecimal getAmount() {
    return amount;
  }

  public void setAmount(BigDecimal amount) {
    this.amount = amount;
  }

  public BigDecimal getConvertedAmount() {
    return convertedAmount;
  }

  public void setConvertedAmount(BigDecimal convertedAmount) {
    this.convertedAmount = convertedAmount;
  }

  public BigDecimal getFxRate() {
    return fxRate;
  }

  public void setFxRate(BigDecimal fxRate) {
    this.fxRate = fxRate;
  }

  public BigDecimal getFee() {
    return fee;
  }

  public void setFee(BigDecimal fee) {
    this.fee = fee;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public String getErrorMessage() {
    return errorMessage;
  }

  public void setErrorMessage(String errorMessage) {
    this.errorMessage = errorMessage;
  }

  public Long getVersion() {
    return version;
  }

  public void setVersion(Long version) {
    this.version = version;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }

  public Instant getCompletedAt() {
    return completedAt;
  }

  public void setCompletedAt(Instant completedAt) {
    this.completedAt = completedAt;
  }
}
