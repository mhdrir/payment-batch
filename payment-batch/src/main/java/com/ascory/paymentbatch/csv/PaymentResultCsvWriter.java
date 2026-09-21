package com.ascory.paymentbatch.csv;

import com.ascory.paymentbatch.service.PaymentProcessingResult;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Set;

/**
 * Writes the result file: the original payment data plus status, processing type and message.
 * Records are written while they are processed, so nothing is kept in memory unnecessarily.
 * <p>
 * Writes go to a sibling {@code .tmp} file first and are only moved to the final name on
 * {@link #close()}, so a crash mid-run leaves no half-written file under the real result name.
 */
public class PaymentResultCsvWriter implements AutoCloseable {

    /**
     * A cell starting with one of these characters is interpreted as a formula by Excel/Sheets.
     * The input CSV is untrusted, so any of these fields must not be echoed verbatim (CWE-1236).
     */
    private static final Set<Character> FORMULA_TRIGGER_CHARS = Set.of('=', '+', '-', '@');

    private final BufferedWriter writer;
    private final CSVPrinter printer;
    private final Path file;
    private final Path tempFile;

    public PaymentResultCsvWriter(Path file) {
        this.file = file;
        this.tempFile = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            this.writer = Files.newBufferedWriter(tempFile, StandardCharsets.UTF_8);
            this.printer = CSVFormat.DEFAULT.builder()
                    .setHeader(CsvColumns.RESULT_COLUMNS)
                    .build()
                    .print(writer);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not create result file " + tempFile.toAbsolutePath(), e);
        }
    }

    public void write(PaymentProcessingResult result) {
        PaymentCsvRow row = result.row();
        try {
            printer.printRecord(
                    sanitize(row.paymentId()),
                    sanitize(row.recipientName()),
                    sanitize(row.iban()),
                    sanitize(row.amount()),        // original raw value, quote-prefixed only if formula-like
                    sanitize(row.currency()),
                    sanitize(row.reference()),
                    result.status(),
                    result.processingType() == null ? "" : result.processingType(),
                    result.message());
        } catch (IOException e) {
            throw new UncheckedIOException("Could not write to result file " + file.toAbsolutePath(), e);
        }
    }

    /**
     * Neutralises CSV/Excel formula injection: a value starting with =, +, - or @ is prefixed
     * with a single quote, which Excel/Sheets treat as "force text" instead of executing it.
     */
    private static String sanitize(String value) {
        if (value == null || value.isEmpty() || !FORMULA_TRIGGER_CHARS.contains(value.charAt(0))) {
            return value;
        }
        return "'" + value;
    }

    public Path getFile() {
        return file;
    }

    @Override
    public void close() {
        try {
            printer.flush();
            printer.close();
            writer.close();
            moveToFinalName();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not close result file " + tempFile.toAbsolutePath(), e);
        }
    }

    private void moveToFinalName() throws IOException {
        try {
            Files.move(tempFile, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            // Same-directory renames are atomic on every filesystem we expect to run on;
            // this is a defensive fallback in case the runtime filesystem does not support it.
            Files.move(tempFile, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}

