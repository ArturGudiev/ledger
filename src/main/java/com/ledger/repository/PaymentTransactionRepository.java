
package com.ledger.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import com.ledger.model.PaymentTransaction;

import jakarta.persistence.LockModeType;

public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, Long> {
    
    Optional<PaymentTransaction>findByIdAndUserId(Long id, Long userId);

    Optional<PaymentTransaction> findByIdempotencyKey(String idempotencyKey);

    List<PaymentTransaction> findByUserIdOrderByCreatedAtDesc(Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PaymentTransaction p where p.id = :id")
    Optional<PaymentTransaction> findByIdForUpdate(Long id);
}
