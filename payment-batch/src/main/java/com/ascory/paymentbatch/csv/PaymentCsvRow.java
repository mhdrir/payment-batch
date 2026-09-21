package com.ascory.paymentbatch.csv;

/**
 * One raw line of the input CSV.
 * <p>
 * All values are kept as {@link String} deliberately: converting the amount while reading
 * would turn a single malformed value into a parsing exception. Keeping it raw allows the
 * validation layer to report it as a FAILED record instead of aborting the file.
 *
 * @param paymentId  raw payment_id column
 * @param recipientName raw recipient_name column
 * @param iban       raw recipient_iban column
 * @param amount     raw amount column
 * @param currency   raw currency column
 * @param reference  raw payment_reference column
 * @param sourceFile file name the record originates from
 * @param lineNumber line number inside that file (1-based, header included)
 */
public record PaymentCsvRow(
        String paymentId,
        String recipientName,
        String iban,
        String amount,
        String currency,
        String reference,
        String sourceFile,
        long lineNumber) {
}

