package com.ascory.paymentbatch.repository;

import com.ascory.paymentbatch.domain.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    /**
     * @return whether this payment_id was already recorded in a previous (or the current) run,
     * regardless of whether that attempt succeeded or failed
     */
    boolean existsByPaymentId(String paymentId);
}

