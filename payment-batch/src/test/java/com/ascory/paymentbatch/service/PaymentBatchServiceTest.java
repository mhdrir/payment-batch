package com.ascory.paymentbatch.service;

import com.ascory.paymentbatch.config.BatchProperties;
import com.ascory.paymentbatch.csv.PaymentCsvReader;
import com.ascory.paymentbatch.csv.PaymentCsvRow;
import com.ascory.paymentbatch.domain.Payment;
import com.ascory.paymentbatch.domain.ProcessingType;
import com.ascory.paymentbatch.repository.PaymentRepository;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.Reader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Plain unit test (no Spring context) proving that a record which fails to persist is reflected
 * in the result file, instead of the CSV silently disagreeing with the payments table.
 */
class PaymentBatchServiceTest {

    @TempDir
    Path workDir;

    @Test
    @DisplayName("only the record that failed to persist carries a warning in the result file")
    void reflectsAFailedPersistInTheResultFile() throws IOException {
        Path inputDir = Files.createDirectories(workDir.resolve("input"));
        Path outputDir = Files.createDirectories(workDir.resolve("output"));
        Files.write(inputDir.resolve("payments.csv"), List.of(
                "payment_id,recipient_name,recipient_iban,amount,currency,payment_reference",
                "300001,Recipient 300001,DE001,100.00,EUR,Invoice 300001",
                "300002,Recipient 300002,DE002,100.00,EUR,Invoice 300002"
        ), StandardCharsets.UTF_8);

        BatchProperties properties = new BatchProperties(inputDir, outputDir, Set.of("EUR", "USD"));

        PaymentProcessingService processingService = mock(PaymentProcessingService.class);
        when(processingService.process(any())).thenAnswer(invocation -> {
            PaymentCsvRow row = invocation.getArgument(0);
            return PaymentProcessingResult.success(row, new BigDecimal("100.00"),
                    ProcessingType.NORMAL, "Processed normally.");
        });

        // 300001 fails to persist, 300002 succeeds
        PaymentRepository paymentRepository = mock(PaymentRepository.class);
        doThrow(new RuntimeException("database unavailable"))
                .doAnswer(invocation -> invocation.getArgument(0))
                .when(paymentRepository).save(any(Payment.class));

        PaymentBatchService batchService = new PaymentBatchService(
                properties, new PaymentCsvReader(), processingService, paymentRepository);

        batchService.execute();

        Map<String, CSVRecord> results = readResults(findResultFile(outputDir));

        assertThat(results.get("300001").get("message")).contains("NOT PERSISTED TO AUDIT DATABASE");
        assertThat(results.get("300002").get("message")).doesNotContain("NOT PERSISTED");
        // the underlying validation/classification outcome itself is unaffected by the persistence failure
        assertThat(results.get("300001").get("status")).isEqualTo("SUCCESS");
        assertThat(results.get("300001").get("processing_type")).isEqualTo("NORMAL");
    }

    private Path findResultFile(Path outputDir) throws IOException {
        try (Stream<Path> files = Files.list(outputDir)) {
            return files.filter(path -> path.getFileName().toString().startsWith("payments-result-"))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("No result file in " + outputDir));
        }
    }

    private Map<String, CSVRecord> readResults(Path resultFile) throws IOException {
        try (Reader reader = Files.newBufferedReader(resultFile, StandardCharsets.UTF_8);
             CSVParser parser = CSVFormat.DEFAULT.builder()
                     .setHeader()
                     .setSkipHeaderRecord(true)
                     .build()
                     .parse(reader)) {
            return parser.stream().collect(Collectors.toMap(record -> record.get("payment_id"), record -> record));
        }
    }
}
