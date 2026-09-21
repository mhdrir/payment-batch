package com.ascory.paymentbatch.runner;

import com.ascory.paymentbatch.service.PaymentBatchService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Triggers exactly one batch run on application start; the application terminates afterwards.
 * <p>
 * Deliberately free of logic (that lives in {@link PaymentBatchService}) and switchable via
 * {@code app.batch.auto-run=false}, which keeps integration tests in control of when the batch
 * is executed - Spring Boot runs {@link ApplicationRunner} beans in {@code @SpringBootTest} too.
 */
@Component
@ConditionalOnProperty(prefix = "app.batch", name = "auto-run", havingValue = "true", matchIfMissing = true)
public class PaymentBatchRunner implements ApplicationRunner {

    private final PaymentBatchService batchService;

    public PaymentBatchRunner(PaymentBatchService batchService) {
        this.batchService = batchService;
    }

    @Override
    public void run(ApplicationArguments args) {
        batchService.execute();
    }
}

