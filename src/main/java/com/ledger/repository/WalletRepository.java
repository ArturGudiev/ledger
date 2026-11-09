    package com.ledger.repository;

    import java.util.List;
    import java.util.Optional;

    import org.springframework.data.jpa.repository.JpaRepository;
    import org.springframework.data.jpa.repository.Lock;
    import org.springframework.data.jpa.repository.Query;

    import com.ledger.model.Wallet;

    import jakarta.persistence.LockModeType;

    public interface WalletRepository extends JpaRepository<Wallet, Long> {
    
        List<Wallet> findByUserIdOrderByIdAsc(Long userId);
        
        Optional<Wallet> findByIdAndUserId(Long id, Long userId);

        boolean existsByUserIdAndCurrency(Long userId, String currency);

        @Lock(LockModeType.PESSIMISTIC_WRITE)
        @Query("select w from Wallet w where w.id = :id")
        Optional<Wallet> findByIdForUpdate(Long id);

        @Lock(LockModeType.PESSIMISTIC_WRITE)
        @Query("""
        select w from Wallet w
        where w.currency = :currency
        and w.user.email = :email
        and w.user.role = com.ledger.model.enums.Role.SYSTEM
        """)
        Optional<Wallet> findSystemWallet(String currency, String email);

    }