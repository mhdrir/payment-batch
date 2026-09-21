package com.ascory.paymentbatch.validation;

import com.ascory.paymentbatch.config.BatchProperties;
import com.ascory.paymentbatch.csv.AmountParser;
import com.ascory.paymentbatch.csv.PaymentCsvRow;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.TreeSet;

/**
 * The 3 validation rules from the task description, all evaluated (no fail-fast) so the result
 * file can report every problem of a record in one go instead of only the first one.
 */
@Component
public class PaymentValidator {

    private final BatchProperties properties;

    public PaymentValidator(BatchProperties properties) {
        this.properties = properties;
    }

    /**
     * @return all violation messages, empty when the record is valid
     */
    public List<String> validate(PaymentCsvRow row) {
        List<String> errors = new ArrayList<>();
        validateAmount(row.amount()).ifPresent(errors::add);
        validateIban(row.iban()).ifPresent(errors::add);
        validateCurrency(row.currency()).ifPresent(errors::add);
        return errors;
    }

    /** Rule 1: amount must be greater than 0. Missing or non-numeric values fail too. */
    private Optional<String> validateAmount(String rawAmount) {
        if (rawAmount == null || rawAmount.isBlank()) {
            return Optional.of("Amount is missing");
        }
        Optional<BigDecimal> amount = AmountParser.parse(rawAmount);
        if (amount.isEmpty()) {
            return Optional.of("Amount '%s' is not a valid number".formatted(rawAmount));
        }
        if (amount.get().compareTo(BigDecimal.ZERO) <= 0) {
            return Optional.of("Amount must be greater than 0 but was %s".formatted(rawAmount));
        }
        return Optional.empty();
    }

    /**
     * Rule 2: IBAN must be present and not empty.
     * <p>
     * The task asks for presence only - no format or checksum (MOD-97) check is performed.
     */
    private Optional<String> validateIban(String iban) {
        if (iban == null || iban.isBlank()) {
            return Optional.of("IBAN is missing or empty");
        }
        return Optional.empty();
    }

    /**
     * Rule 3: currency must be EUR or USD.
     * The accepted set is configurable ({@code app.batch.allowed-currencies}) but defaults to EUR/USD.
     */
    private Optional<String> validateCurrency(String currency) {
        if (currency == null || currency.isBlank()) {
            return Optional.of("Currency is missing, allowed values are %s".formatted(sortedAllowedCurrencies()));
        }
        if (!properties.allowedCurrencies().contains(currency.trim().toUpperCase(Locale.ROOT))) {
            return Optional.of("Currency '%s' is not supported, allowed values are %s"
                    .formatted(currency, sortedAllowedCurrencies()));
        }
        return Optional.empty();
    }

    private String sortedAllowedCurrencies() {
        return String.join("/", new TreeSet<>(properties.allowedCurrencies()));
    }
}
