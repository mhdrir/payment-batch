package com.ascory.paymentbatch.domain;

/**
 * Result of processing a single CSV record.
 */
public enum PaymentStatus {

    /** Record passed all validation rules and could be classified. */
    SUCCESS,

    /** Record violated at least one validation rule or could not be classified. */
    FAILED,

    /** payment_id was already recorded in an earlier run (or earlier in this run) and was not reprocessed. */
    SKIPPED_DUPLICATE
}

