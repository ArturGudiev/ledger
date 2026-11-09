
package com.ledger.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import com.ledger.model.ExchangeRate;

public interface ExchangeRateRepository extends JpaRepository<ExchangeRate, Long> {

    Optional<ExchangeRate> findByBaseCurrencyAndQuoteCurrency(String baseCurrency, String quoteCurrency);

    List<ExchangeRate> findByBaseCurrencyOrderByQuoteCurrency(String baseCurrency);

}
