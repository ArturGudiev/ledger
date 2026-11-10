package com.ledger.model;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
  name = "ledger_entries",
  uniqueConstraints = @UniqueConstraint(
    columnNames = {"wallet_id", "operation_key"}
  )
)



public class LedgerEntry {
	
	public enum LedgerEntryType {DEBIT, CREDIT}
	
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(optional = false)
  @JoinColumn(name = "wallet_id", nullable = false)
  private Wallet wallet;

  @ManyToOne(optional = true)
  @JoinColumn(name = "transfer_id", nullable = true)
  private Transfer transfer;

  @ManyToOne(optional = true)
  @JoinColumn(name = "payment_id", nullable = true)
  private PaymentTransaction payment;

  @Enumerated(EnumType.STRING)
  @Column(name = "entry_type", nullable = false, length = 8)
  private LedgerEntryType entryType;

  @Column(nullable = false, precision = 28, scale = 12)
  private BigDecimal amount;

  @Column(name = "balance_after", nullable = false, precision = 28, scale = 12)
  private BigDecimal balanceAfter;

  @Column(nullable = false, length = 3)
  private String currency;

  @Column(length = 500)
  private String description;

  @Column(name = "operation_key", nullable = false, length = 120)
  private String operationKey;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

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

  public Wallet getWallet() {
    return wallet;
  }

  public void setWallet(Wallet wallet) {
    this.wallet = wallet;
  }

  public Transfer getTransfer() {
    return transfer;
  }

  public void setTransfer(Transfer transfer) {
    this.transfer = transfer;
  }

  public PaymentTransaction getPayment() {
    return payment;
  }

  public void setPayment(PaymentTransaction paymentTransaction) {
    this.payment = paymentTransaction;
  }

  public LedgerEntryType getEntryType() {
    return entryType;
  }

  public void setEntryType(LedgerEntryType entryType) {
    this.entryType = entryType;
  }

  public BigDecimal getAmount() {
    return amount;
  }

  public void setAmount(BigDecimal amount) {
    this.amount = amount;
  }

  public BigDecimal getBalanceAfter() {
    return balanceAfter;
  }

  public void setBalanceAfter(BigDecimal balanceAfter) {
    this.balanceAfter = balanceAfter;
  }

  public String getCurrency() {
    return currency;
  }

  public void setCurrency(String currency) {
    this.currency = currency;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public String getOperationKey() {
    return operationKey;
  }

  public void setOperationKey(String operationKey) {
    this.operationKey = operationKey;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }
}
