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

/**
 * Read-only view on the provided {@code custom_payment_risk_mapping} table.
 * The amount ranges are owned by the database, not by the application, so that the
 * classification can be changed without a redeployment.
 */
@Entity
@Table(name = "custom_payment_risk_mapping")
public class CustomPaymentRiskMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "min_amount", nullable = false)
    private BigDecimal minAmount;

    @Column(name = "max_amount", nullable = false)
    private BigDecimal maxAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "processing_type", nullable = false, length = 50)
    private ProcessingType processingType;

    @Column(name = "description", nullable = false)
    private String description;

    protected CustomPaymentRiskMapping() {
        // required by JPA
    }

    public Long getId() {
        return id;
    }

    public BigDecimal getMinAmount() {
        return minAmount;
    }

    public BigDecimal getMaxAmount() {
        return maxAmount;
    }

    public ProcessingType getProcessingType() {
        return processingType;
    }

    public String getDescription() {
        return description;
    }
}

