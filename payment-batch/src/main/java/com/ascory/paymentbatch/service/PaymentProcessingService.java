package com.ascory.paymentbatch.service;

import com.ascory.paymentbatch.csv.AmountParser;
import com.ascory.paymentbatch.csv.PaymentCsvRow;
import com.ascory.paymentbatch.domain.CustomPaymentRiskMapping;
import com.ascory.paymentbatch.repository.CustomPaymentRiskMappingRepository;
import com.ascory.paymentbatch.repository.PaymentRepository;
import com.ascory.paymentbatch.validation.PaymentValidator;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Processes a single record: check for a duplicate payment_id first, then validate, then
 * classify via the database.
 * <p>
 * The method never throws for bad input data - a problem is expressed as a FAILED result,
 * which is what keeps the batch running (non-functional requirement "do not stop processing").
 */
@Service
public class PaymentProcessingService {

    private final PaymentValidator validator;
    private final CustomPaymentRiskMappingRepository riskMappingRepository;
    private final PaymentRepository paymentRepository;

    public PaymentProcessingService(PaymentValidator validator,
                                     CustomPaymentRiskMappingRepository riskMappingRepository,
                                     PaymentRepository paymentRepository) {
        this.validator = validator;
        this.riskMappingRepository = riskMappingRepository;
        this.paymentRepository = paymentRepository;
    }

    public PaymentProcessingResult process(PaymentCsvRow row) {
        String paymentId = row.paymentId();
        if (paymentId != null && !paymentId.isBlank() && paymentRepository.existsByPaymentId(paymentId)) {
            return PaymentProcessingResult.skippedDuplicate(row, paymentId);
        }

        BigDecimal amount = AmountParser.parse(row.amount()).orElse(null);

        List<String> errors = validator.validate(row);
        if (!errors.isEmpty()) {
            return PaymentProcessingResult.failed(row, amount, String.join("; ", errors));
        }

        // amount is guaranteed to be present and > 0 here, otherwise the amount rule would have failed
        Optional<CustomPaymentRiskMapping> mapping = riskMappingRepository
                .findFirstByMinAmountLessThanEqualAndMaxAmountGreaterThanEqualOrderByMinAmountAsc(amount, amount);
        if (mapping.isEmpty()) {
            return PaymentProcessingResult.failed(row,
                    amount, "No processing type configured for amount %s".formatted(amount.toPlainString()));
        }

        CustomPaymentRiskMapping match = mapping.get();
        return PaymentProcessingResult.success(row, amount, match.getProcessingType(), match.getDescription());
    }
}
