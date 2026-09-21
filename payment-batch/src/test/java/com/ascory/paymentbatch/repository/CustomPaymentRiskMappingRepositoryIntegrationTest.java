package com.ascory.paymentbatch.repository;

import com.ascory.paymentbatch.domain.CustomPaymentRiskMapping;
import com.ascory.paymentbatch.domain.ProcessingType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the classification query against the real mapping data from
 * V1__create-custom-mapping.sql, including the boundaries of the configured ranges.
 */
@SpringBootTest
@ActiveProfiles("test")
class CustomPaymentRiskMappingRepositoryIntegrationTest {

    @Autowired
    private CustomPaymentRiskMappingRepository repository;

    @ParameterizedTest
    @CsvSource({
            "0.01,        NORMAL",
            "322.98,      NORMAL",
            "999.99,      NORMAL",
            "1000.00,     FORMAL_APPROVAL_REQUIRED",
            "9999.99,     FORMAL_APPROVAL_REQUIRED",
            "10000.00,    HIGH_RISK_REVIEW",
            "47138.19,    HIGH_RISK_REVIEW",
            "9999999.99,  HIGH_RISK_REVIEW"
    })
    @DisplayName("amount is classified by the range configured in the database")
    void resolvesProcessingType(BigDecimal amount, ProcessingType expected) {
        assertThat(findMapping(amount))
                .hasValueSatisfying(mapping -> {
                    assertThat(mapping.getProcessingType()).isEqualTo(expected);
                    // the explanation is enriched from the database as well
                    assertThat(mapping.getDescription()).isNotBlank();
                });
    }

    @Test
    @DisplayName("the description of the matched range is taken from the database")
    void enrichesWithDescription() {
        assertThat(findMapping(new BigDecimal("322.98")))
                .hasValueSatisfying(mapping -> assertThat(mapping.getDescription())
                        .isEqualTo("Payments below 1,000 are processed normally."));
    }

    @Test
    @DisplayName("an amount above the highest configured range has no processing type")
    void returnsEmptyForAmountOutsideEveryRange() {
        assertThat(findMapping(new BigDecimal("467999097.55"))).isEmpty();
    }

    private Optional<CustomPaymentRiskMapping> findMapping(BigDecimal amount) {
        return repository.findFirstByMinAmountLessThanEqualAndMaxAmountGreaterThanEqualOrderByMinAmountAsc(
                amount, amount);
    }
}
