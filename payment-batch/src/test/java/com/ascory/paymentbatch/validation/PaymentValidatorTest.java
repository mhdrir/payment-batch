package com.ascory.paymentbatch.validation;

import com.ascory.paymentbatch.config.BatchProperties;
import com.ascory.paymentbatch.csv.PaymentCsvRow;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentValidatorTest {

    private final PaymentValidator validator = new PaymentValidator(
            new BatchProperties(Path.of("input"), Path.of("output"), Set.of("EUR", "USD")));

    private static PaymentCsvRow row(String iban, String amount, String currency) {
        return new PaymentCsvRow("100001", "Recipient", iban, amount, currency, "Invoice", "test.csv", 2);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.01", "1000.00", "467999097.55"})
    @DisplayName("a fully valid record has no violations")
    void acceptsValidRecord(String amount) {
        assertThat(validator.validate(row("DE123", amount, "EUR"))).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({
            "0.00,   Amount must be greater than 0",
            "-680.61,Amount must be greater than 0",
            "abc,    is not a valid number",
            "'',     Amount is missing"
    })
    @DisplayName("invalid amounts are rejected with a reason")
    void rejectsInvalidAmount(String amount, String expectedMessagePart) {
        assertThat(validator.validate(row("DE123", amount, "EUR")))
                .anySatisfy(message -> assertThat(message).contains(expectedMessagePart));
    }

    @Test
    @DisplayName("IBAN must be present")
    void rejectsMissingIban() {
        assertThat(validator.validate(row("", "100.00", "EUR")))
                .anySatisfy(message -> assertThat(message).contains("IBAN is missing or empty"));
        assertThat(validator.validate(row("   ", "100.00", "EUR")))
                .anySatisfy(message -> assertThat(message).contains("IBAN"));
        assertThat(validator.validate(row("DE00000000000000100291", "100.00", "EUR"))).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"EUR", "USD", "eur"})
    @DisplayName("EUR and USD are accepted")
    void acceptsAllowedCurrency(String currency) {
        assertThat(validator.validate(row("DE123", "100.00", currency))).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"GBP", "CHF", "XXX", ""})
    @DisplayName("every other currency is rejected")
    void rejectsOtherCurrency(String currency) {
        assertThat(validator.validate(row("DE123", "100.00", currency)))
                .anySatisfy(message -> assertThat(message).contains("EUR/USD"));
    }

    @Test
    @DisplayName("validator collects all violations of a record instead of failing fast")
    void collectsAllViolations() {
        List<String> errors = validator.validate(row("", "-1", "GBP"));

        assertThat(errors).hasSize(3);
        assertThat(errors)
                .anySatisfy(message -> assertThat(message).contains("Amount"))
                .anySatisfy(message -> assertThat(message).contains("IBAN"))
                .anySatisfy(message -> assertThat(message).contains("Currency"));
    }
}
