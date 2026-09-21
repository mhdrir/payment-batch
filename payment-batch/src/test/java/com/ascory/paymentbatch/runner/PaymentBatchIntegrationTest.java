package com.ascory.paymentbatch.runner;

import com.ascory.paymentbatch.domain.Payment;
import com.ascory.paymentbatch.domain.PaymentStatus;
import com.ascory.paymentbatch.domain.ProcessingType;
import com.ascory.paymentbatch.repository.PaymentRepository;
import com.ascory.paymentbatch.service.BatchSummary;
import com.ascory.paymentbatch.service.PaymentBatchService;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
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
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end test of the batch: CSV in, result file and database rows out.
 * The sample contains one record per outcome, including the record whose amount is not
 * covered by any configured range.
 */
@SpringBootTest
@ActiveProfiles("test")
class PaymentBatchIntegrationTest {

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
                "100291,Recipient 100291,DE00000000000000100291,322.98,EUR,Invoice 100291",
                "100754,Recipient 100754,DE00000000000000100754,1675.37,USD,Invoice 100754",
                "100926,Recipient 100926,DE00000000000000100926,47138.19,USD,Invoice 100926",
                "100979,Recipient 100979,,776.45,EUR,Missing iban 100979",
                "100955,Recipient 100955,DE00000000000000100955,-680.61,EUR,Invalid amount 100955",
                "100995,Recipient 100995,DE00000000000000100995,1509.52,GBP,Invalid currency 100995",
                "100094,Recipient 100094,DE00000000000000100094,467999097.55,EUR,Amount out of range"
        ), StandardCharsets.UTF_8);
    }

    @DynamicPropertySource
    static void batchProperties(DynamicPropertyRegistry registry) {
        registry.add("app.batch.input-directory", () -> inputDir.toString());
        registry.add("app.batch.output-directory", () -> outputDir.toString());
    }

    @Test
    @DisplayName("processes every record, writes a result file and persists the outcome")
    void runsBatchEndToEnd() throws IOException {
        BatchSummary totals = batchService.execute();

        assertThat(totals.getTotal()).isEqualTo(7);
        assertThat(totals.getSuccess()).isEqualTo(3);
        assertThat(totals.getFailed()).isEqualTo(4);

        Path resultFile = findFile("payments-result-");
        Map<String, CSVRecord> resultsByPaymentId = readResults(resultFile);

        assertThat(resultsByPaymentId).hasSize(7);

        assertSuccess(resultsByPaymentId.get("100291"), ProcessingType.NORMAL);
        assertSuccess(resultsByPaymentId.get("100754"), ProcessingType.FORMAL_APPROVAL_REQUIRED);
        assertSuccess(resultsByPaymentId.get("100926"), ProcessingType.HIGH_RISK_REVIEW);

        // the message of a successful record is enriched with the description from the database
        assertThat(resultsByPaymentId.get("100291").get("message"))
                .isEqualTo("Payments below 1,000 are processed normally.");

        assertFailure(resultsByPaymentId.get("100979"), "IBAN is missing or empty");
        assertFailure(resultsByPaymentId.get("100955"), "Amount must be greater than 0");
        assertFailure(resultsByPaymentId.get("100995"), "Currency 'GBP' is not supported");
        assertFailure(resultsByPaymentId.get("100094"), "No processing type configured for amount");

        // original payment data is echoed unchanged
        assertThat(resultsByPaymentId.get("100291").get("recipient_iban")).isEqualTo("DE00000000000000100291");
        assertThat(resultsByPaymentId.get("100291").get("amount")).isEqualTo("322.98");

        // every record - valid and invalid - is persisted
        List<Payment> persisted = paymentRepository.findAll();
        assertThat(persisted).hasSize(7);
        assertThat(persisted).filteredOn(payment -> payment.getStatus() == PaymentStatus.SUCCESS).hasSize(3);
        assertThat(persisted).filteredOn(payment -> payment.getStatus() == PaymentStatus.FAILED).hasSize(4);

        // a summary file is written
        assertThat(findFile("batch-summary-")).content().contains("records processed : 7");
    }

    private void assertSuccess(CSVRecord record, ProcessingType expectedType) {
        assertThat(record).isNotNull();
        assertThat(record.get("status")).isEqualTo(PaymentStatus.SUCCESS.name());
        assertThat(record.get("processing_type")).isEqualTo(expectedType.name());
    }

    private void assertFailure(CSVRecord record, String expectedMessagePart) {
        assertThat(record).isNotNull();
        assertThat(record.get("status")).isEqualTo(PaymentStatus.FAILED.name());
        assertThat(record.get("processing_type")).isEmpty();
        assertThat(record.get("message")).contains(expectedMessagePart);
    }

    private Path findFile(String prefix) throws IOException {
        try (Stream<Path> files = Files.list(outputDir)) {
            return files.filter(path -> path.getFileName().toString().startsWith(prefix))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("No file starting with " + prefix + " in " + outputDir));
        }
    }

    private Map<String, CSVRecord> readResults(Path resultFile) throws IOException {
        try (Reader reader = Files.newBufferedReader(resultFile, StandardCharsets.UTF_8);
             CSVParser parser = CSVFormat.DEFAULT.builder()
                     .setHeader()
                     .setSkipHeaderRecord(true)
                     .build()
                     .parse(reader)) {
            return parser.stream().collect(
                    java.util.stream.Collectors.toMap(record -> record.get("payment_id"), record -> record));
        }
    }
}




