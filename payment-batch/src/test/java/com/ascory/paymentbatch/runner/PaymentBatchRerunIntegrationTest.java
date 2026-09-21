package com.ascory.paymentbatch.runner;

import com.ascory.paymentbatch.domain.Payment;
import com.ascory.paymentbatch.domain.PaymentStatus;
import com.ascory.paymentbatch.repository.PaymentRepository;
import com.ascory.paymentbatch.service.BatchSummary;
import com.ascory.paymentbatch.service.PaymentBatchService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reproduces "the same input file is processed twice" - either a genuine rerun after a crash,
 * or the same file being re-delivered - and asserts nothing is reported or persisted twice.
 * Uses its own Spring context (distinct temp directories) so it never shares database state
 * with {@link PaymentBatchIntegrationTest}.
 */
@SpringBootTest
@ActiveProfiles("test")
class PaymentBatchRerunIntegrationTest {

    @TempDir
    static Path workDir;

    static Path inputDir;
    static Path outputDir;

    @Autowired
    private PaymentBatchService batchService;

    @Autowired
    private PaymentRepository paymentRepository;

    @BeforeAll
    static void prepareDirectories() throws IOException {
        inputDir = Files.createDirectories(workDir.resolve("input"));
        outputDir = Files.createDirectories(workDir.resolve("output"));
        Files.write(inputDir.resolve("payments.csv"), List.of(
                "payment_id,recipient_name,recipient_iban,amount,currency,payment_reference",
                "200001,Recipient 200001,DE00000000000000200001,322.98,EUR,Invoice 200001",
                "200002,Recipient 200002,DE00000000000000200002,1509.52,GBP,Invalid currency 200002",
                "200003,Recipient 200003,DE00000000000000200003,,EUR,Missing amount 200003"
        ), StandardCharsets.UTF_8);
    }

    @DynamicPropertySource
    static void batchProperties(DynamicPropertyRegistry registry) {
        registry.add("app.batch.input-directory", () -> inputDir.toString());
        registry.add("app.batch.output-directory", () -> outputDir.toString());
    }

    @Test
    @DisplayName("rerunning the same file reports every record as skipped-duplicate instead of double-processing it")
    void rerunSkipsEveryPreviouslyRecordedPaymentId() {
        BatchSummary firstRun = batchService.execute();
        assertThat(firstRun.getTotal()).isEqualTo(3);
        assertThat(firstRun.getSuccess()).isEqualTo(1);
        assertThat(firstRun.getFailed()).isEqualTo(2);
        assertThat(firstRun.getSkippedDuplicate()).isEqualTo(0);

        BatchSummary secondRun = batchService.execute();
        assertThat(secondRun.getTotal()).isEqualTo(3);
        assertThat(secondRun.getSuccess()).isEqualTo(0);
        assertThat(secondRun.getFailed()).isEqualTo(0);
        assertThat(secondRun.getSkippedDuplicate()).isEqualTo(3);

        // the payments table still has exactly one row per payment_id: the second run's
        // skipped-duplicate outcome is reported in the result file but never persisted again,
        // since the first run's row already is that payment_id's permanent record
        List<Payment> persisted = paymentRepository.findAll();
        assertThat(persisted).hasSize(3);
        assertThat(persisted).filteredOn(p -> p.getStatus() == PaymentStatus.SKIPPED_DUPLICATE).isEmpty();
        assertThat(persisted).filteredOn(p -> "200001".equals(p.getPaymentId()))
                .extracting(Payment::getStatus)
                .containsExactly(PaymentStatus.SUCCESS);
    }
}
