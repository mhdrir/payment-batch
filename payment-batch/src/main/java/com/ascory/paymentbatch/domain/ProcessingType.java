package com.ascory.paymentbatch.domain;

/**
 * Processing type of a payment. The concrete value for a payment is not hardcoded here,
 * it is derived from the {@code custom_payment_risk_mapping} table at runtime.
 */
public enum ProcessingType {

    NORMAL,
    FORMAL_APPROVAL_REQUIRED,
    HIGH_RISK_REVIEW
}

