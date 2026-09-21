package com.ascory.paymentbatch.service;

import com.ascory.paymentbatch.csv.PaymentCsvRow;
import com.ascory.paymentbatch.domain.Payment;
import com.ascory.paymentbatch.domain.PaymentStatus;
import com.ascory.paymentbatch.domain.ProcessingType;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Outcome of processing one record.
 *
 * @param row            the original CSV data, kept so the result file can echo it unchanged
 * @param status         SUCCESS, FAILED or SKIPPED_DUPLICATE
 * @param amount         parsed amount, {@code null} when it could not be parsed
 * @param processingType resolved processing type, {@code null} unless the record succeeded
 * @param message        reason / additional information for the result file
 */
public record PaymentProcessingResult(
        PaymentCsvRow row,
        PaymentStatus status,
        BigDecimal amount,
        ProcessingType processingType,
        String message) {

    /**
     * The message of a successful record is the description of the matched amount range,
     * i.e. the database explains why the payment was classified this way.
     */
    public static PaymentProcessingResult success(PaymentCsvRow row, BigDecimal amount,
                                                   ProcessingType processingType, String message) {
        return new PaymentProcessingResult(row, PaymentStatus.SUCCESS, amount, processingType, message);
    }

    public static PaymentProcessingResult failed(PaymentCsvRow row, BigDecimal amount, String message) {
        return new PaymentProcessingResult(row, PaymentStatus.FAILED, amount, null, message);
    }

    /**
     * The record is not validated or classified at all - payment_id was already recorded in an
     * earlier (or the current) run, so reprocessing it would risk reporting the same payment twice.
     */
    public static PaymentProcessingResult skippedDuplicate(PaymentCsvRow row, String paymentId) {
        return new PaymentProcessingResult(row, PaymentStatus.SKIPPED_DUPLICATE, null, null,
                "Payment id '%s' was already recorded in an earlier run".formatted(paymentId));
    }

    public boolean isSuccess() {
        return status == PaymentStatus.SUCCESS;
    }

    public boolean isSkippedDuplicate() {
        return status == PaymentStatus.SKIPPED_DUPLICATE;
    }

    /**
     * The record's own outcome (status, reason) is unaffected - persistence is a separate fact
     * from whether the payment was valid, and an operator needs both, not one overwriting the other.
     */
    public PaymentProcessingResult withPersistenceWarning() {
        String warning = "NOT PERSISTED TO AUDIT DATABASE - retry this payment_id manually";
        String newMessage = (message == null || message.isBlank()) ? warning : message + " | " + warning;
        return new PaymentProcessingResult(row, status, amount, processingType, newMessage);
    }

    public Payment toEntity(Instant processedAt) {
        return new Payment(
                row.paymentId(),
                row.recipientName(),
                row.iban(),
                amount,
                row.currency(),
                row.reference(),
                status,
                processingType,
                message,
                row.sourceFile(),
                row.lineNumber(),
                processedAt);
    }
}
