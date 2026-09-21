package com.ascory.paymentbatch.csv;

import com.ascory.paymentbatch.domain.ProcessingType;
import com.ascory.paymentbatch.service.PaymentProcessingResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentResultCsvWriterTest {

    @TempDir
    Path outputDir;

    private static PaymentCsvRow row(String recipientName, String reference) {
        return new PaymentCsvRow("100291", recipientName, "DE123", "322.98", "EUR", reference, "test.csv", 2);
    }

    private static PaymentProcessingResult success(PaymentCsvRow row) {
        return PaymentProcessingResult.success(row, new BigDecimal("322.98"),
                ProcessingType.NORMAL, "Processed normally.");
    }

    @Test
    @DisplayName("final file only appears on close(); no .tmp file is left behind afterwards")
    void writesAtomically() throws IOException {
        Path finalFile = outputDir.resolve("result.csv");
        Path tempFile = outputDir.resolve("result.csv.tmp");

        PaymentResultCsvWriter writer = new PaymentResultCsvWriter(finalFile);
        writer.write(success(row("Recipient", "Invoice 1")));

        assertThat(tempFile).exists();
        assertThat(finalFile).doesNotExist();

        writer.close();

        assertThat(tempFile).doesNotExist();
        assertThat(finalFile).exists();
        assertThat(finalFile).content(StandardCharsets.UTF_8).contains("Recipient");
    }

    @Test
    @DisplayName("a field starting with a formula-trigger character is quote-prefixed, not echoed as a bare formula")
    void neutralisesCsvFormulaInjection() throws IOException {
        Path finalFile = outputDir.resolve("result.csv");

        try (PaymentResultCsvWriter writer = new PaymentResultCsvWriter(finalFile)) {
            writer.write(success(row("=cmd|'/C calc'!A1", "+SUM(1+1)")));
        }

        String content = Files.readString(finalFile, StandardCharsets.UTF_8);
        assertThat(content).contains("'=cmd|'/C calc'!A1");
        assertThat(content).contains("'+SUM(1+1)");
        assertThat(content).doesNotContain(",=cmd|");
        assertThat(content).doesNotContain(",+SUM(");
    }

    @Test
    @DisplayName("a persistence warning appended to the message is written through unchanged")
    void writesPersistenceWarningMessage() throws IOException {
        Path finalFile = outputDir.resolve("result.csv");

        try (PaymentResultCsvWriter writer = new PaymentResultCsvWriter(finalFile)) {
            writer.write(success(row("Recipient", "Invoice 1")).withPersistenceWarning());
        }

        String content = Files.readString(finalFile, StandardCharsets.UTF_8);
        assertThat(content).contains("NOT PERSISTED TO AUDIT DATABASE");
    }
}
