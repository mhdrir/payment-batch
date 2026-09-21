package com.ascory.paymentbatch.repository;

import com.ascory.paymentbatch.domain.CustomPaymentRiskMapping;
import org.springframework.data.jpa.repository.JpaRepository;

import java.math.BigDecimal;
import java.util.Optional;

// TODO: nothing enforces that mapping ranges don't overlap; add a startup check or DB constraint
public interface CustomPaymentRiskMappingRepository extends JpaRepository<CustomPaymentRiskMapping, Long> {

    /**
     * Finds the mapping whose amount range contains the given amount (both bounds inclusive).
     * <p>
     * Returns an {@link Optional} on purpose: an amount outside every configured range
     * (e.g. above the highest {@code max_amount}) must not silently fall back to a default
     * processing type - that would be a real financial risk.
     */
    Optional<CustomPaymentRiskMapping> findFirstByMinAmountLessThanEqualAndMaxAmountGreaterThanEqualOrderByMinAmountAsc(
            BigDecimal lowerBound, BigDecimal upperBound);
}

