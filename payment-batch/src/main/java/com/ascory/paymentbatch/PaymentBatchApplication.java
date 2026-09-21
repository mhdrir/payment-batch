package com.ascory.paymentbatch;

import com.ascory.paymentbatch.config.BatchProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * Entry point of the batch application.
 * <p>
 * The application is intentionally a non-web Spring Boot application: it starts, runs
 * {@link com.ascory.paymentbatch.runner.PaymentBatchRunner} once and terminates.
 */
@SpringBootApplication
@EnableConfigurationProperties(BatchProperties.class)
public class PaymentBatchApplication {

    public static void main(String[] args) {
        SpringApplication.run(PaymentBatchApplication.class, args);
    }
}

