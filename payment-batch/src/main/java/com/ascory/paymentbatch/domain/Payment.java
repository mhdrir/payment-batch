package com.ascory.paymentbatch.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Persisted outcome of a single CSV record. Failed records are stored as well, so the run
 * stays auditable and can be analysed later without re-reading the input file.
 */
@Entity
@Table(name = "payments")
public class Payment {

    private static final int MAX_MESSAGE_LENGTH = 1000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "payment_id", length = 50)
    private String paymentId;

    @Column(name = "recipient_name")
    private String recipientName;

    @Column(name = "recipient_iban", length = 34)
    private String recipientIban;

    /** {@code null} when the amount in the CSV could not be parsed. */
    @Column(name = "amount", precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", length = 10)
    private String currency;

    @Column(name = "payment_reference")
    private String paymentReference;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PaymentStatus status;

    /** {@code null} for failed records. */
    @Enumerated(EnumType.STRING)
    @Column(name = "processing_type", length = 50)
    private ProcessingType processingType;

    @Column(name = "message", length = MAX_MESSAGE_LENGTH)
    private String message;

    @Column(name = "source_file", nullable = false)
    private String sourceFile;

    @Column(name = "source_line", nullable = false)
    private long sourceLine;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    protected Payment() {
        // required by JPA
    }

    @SuppressWarnings("checkstyle:ParameterNumber")
    public Payment(String paymentId,
                   String recipientName,
                   String recipientIban,
                   BigDecimal amount,
                   String currency,
                   String paymentReference,
                   PaymentStatus status,
                   ProcessingType processingType,
                   String message,
                   String sourceFile,
                   long sourceLine,
                   Instant processedAt) {
        this.paymentId = blankToNull(truncate(paymentId, 50));
        this.recipientName = truncate(recipientName, 255);
        this.recipientIban = truncate(recipientIban, 34);
        this.amount = amount;
        this.currency = truncate(currency, 10);
        this.paymentReference = truncate(paymentReference, 255);
        this.status = status;
        this.processingType = processingType;
        this.message = truncate(message, MAX_MESSAGE_LENGTH);
        this.sourceFile = sourceFile;
        this.sourceLine = sourceLine;
        this.processedAt = processedAt;
    }

    /**
     * Defensive truncation: the input file is untrusted, an oversized field must not
     * abort the batch with a database constraint violation.
     */
    private static String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    /**
     * A blank payment_id must be stored as SQL NULL, never as an empty string: multiple NULLs
     * never conflict under the unique index, but multiple empty strings would.
     */
    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }

    public Long getId() {
        return id;
    }

    public String getPaymentId() {
        return paymentId;
    }

    public String getRecipientName() {
        return recipientName;
    }

    public String getRecipientIban() {
        return recipientIban;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public String getPaymentReference() {
        return paymentReference;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public ProcessingType getProcessingType() {
        return processingType;
    }

    public String getMessage() {
        return message;
    }

    public String getSourceFile() {
        return sourceFile;
    }

    public long getSourceLine() {
        return sourceLine;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }
}

