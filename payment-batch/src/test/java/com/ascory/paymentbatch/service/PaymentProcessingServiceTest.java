package com.ascory.paymentbatch.service;

import com.ascory.paymentbatch.csv.PaymentCsvRow;
import com.ascory.paymentbatch.domain.CustomPaymentRiskMapping;
import com.ascory.paymentbatch.domain.PaymentStatus;
import com.ascory.paymentbatch.domain.ProcessingType;
import com.ascory.paymentbatch.repository.CustomPaymentRiskMappingRepository;
import com.ascory.paymentbatch.repository.PaymentRepository;
import com.ascory.paymentbatch.validation.PaymentValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PaymentProcessingServiceTest {

    private final PaymentValidator validator = mock(PaymentValidator.class);
    private final CustomPaymentRiskMappingRepository riskMappingRepository = mock(CustomPaymentRiskMappingRepository.class);
    private final PaymentRepository paymentRepository = mock(PaymentRepository.class);
    private final PaymentProcessingService service =
            new PaymentProcessingService(validator, riskMappingRepository, paymentRepository);

    private static PaymentCsvRow row(String amount) {
        return row("100291", amount);
    }

    private static PaymentCsvRow row(String paymentId, String amount) {
        return new PaymentCsvRow(paymentId, "Recipient", "DE123", amount, "EUR", "Invoice", "test.csv", 2);
    }

    private static CustomPaymentRiskMapping mapping(ProcessingType type, String description) {
        CustomPaymentRiskMapping mapping = BeanUtils.instantiateClass(CustomPaymentRiskMapping.class);
        ReflectionTestUtils.setField(mapping, "processingType", type);
        ReflectionTestUtils.setField(mapping, "description", description);
        return mapping;
    }

    @Test
    @DisplayName("valid record is classified with the type and description from the database")
    void classifiesValidRecord() {
        when(validator.validate(any())).thenReturn(List.of());
        when(riskMappingRepository.findFirstByMinAmountLessThanEqualAndMaxAmountGreaterThanEqualOrderByMinAmountAsc(
                new BigDecimal("322.98"), new BigDecimal("322.98")))
                .thenReturn(Optional.of(mapping(ProcessingType.NORMAL, "Processed normally.")));

        PaymentProcessingResult result = service.process(row("322.98"));

        assertThat(result.status()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(result.processingType()).isEqualTo(ProcessingType.NORMAL);
        assertThat(result.amount()).isEqualByComparingTo("322.98");
        assertThat(result.message()).isEqualTo("Processed normally.");
    }

    @Test
    @DisplayName("invalid record is failed with all reasons and is never classified")
    void failsInvalidRecordWithoutDatabaseLookup() {
        when(validator.validate(any())).thenReturn(List.of(
                "Amount must be greater than 0 but was -1",
                "Currency 'GBP' is not supported"));

        PaymentProcessingResult result = service.process(row("-1"));

        assertThat(result.status()).isEqualTo(PaymentStatus.FAILED);
        assertThat(result.processingType()).isNull();
        assertThat(result.message())
                .contains("Amount must be greater than 0")
                .contains("Currency 'GBP' is not supported");
        verifyNoInteractions(riskMappingRepository);
    }

    @Test
    @DisplayName("amount without a configured range fails instead of falling back to a default type")
    void failsWhenNoMappingExists() {
        when(validator.validate(any())).thenReturn(List.of());
        when(riskMappingRepository.findFirstByMinAmountLessThanEqualAndMaxAmountGreaterThanEqualOrderByMinAmountAsc(
                any(), any())).thenReturn(Optional.empty());

        PaymentProcessingResult result = service.process(row("467999097.55"));

        assertThat(result.status()).isEqualTo(PaymentStatus.FAILED);
        assertThat(result.processingType()).isNull();
        assertThat(result.message()).isEqualTo("No processing type configured for amount 467999097.55");
    }

    @Test
    @DisplayName("payment_id already recorded in the database is skipped without validation or classification")
    void skipsPaymentIdAlreadyRecordedInDatabase() {
        when(paymentRepository.existsByPaymentId("100291")).thenReturn(true);

        PaymentProcessingResult result = service.process(row("322.98"));

        assertThat(result.status()).isEqualTo(PaymentStatus.SKIPPED_DUPLICATE);
        assertThat(result.message()).contains("100291").contains("already recorded");
        verifyNoInteractions(validator, riskMappingRepository);
    }

    @Test
    @DisplayName("a blank payment_id is never treated as a duplicate")
    void blankPaymentIdIsNeverDeduplicated() {
        when(validator.validate(any())).thenReturn(List.of());
        when(riskMappingRepository.findFirstByMinAmountLessThanEqualAndMaxAmountGreaterThanEqualOrderByMinAmountAsc(
                any(), any())).thenReturn(Optional.of(mapping(ProcessingType.NORMAL, "Processed normally.")));

        PaymentProcessingResult result = service.process(row("", "322.98"));

        assertThat(result.status()).isEqualTo(PaymentStatus.SUCCESS);
        verify(paymentRepository, never()).existsByPaymentId(any());
    }
}
