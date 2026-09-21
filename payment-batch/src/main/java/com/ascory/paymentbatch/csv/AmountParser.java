package com.ascory.paymentbatch.csv;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Converts the raw amount column into a {@link BigDecimal}.
 * <p>
 * {@link BigDecimal} is used instead of a floating point type because monetary values must
 * not lose precision.
 */
public final class AmountParser {

    private AmountParser() {
    }

    /**
     * @return the parsed amount, or {@link Optional#empty()} if the value is missing or not a number
     */
    public static Optional<BigDecimal> parse(String rawAmount) {
        if (rawAmount == null || rawAmount.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(new BigDecimal(rawAmount.trim()));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
}

