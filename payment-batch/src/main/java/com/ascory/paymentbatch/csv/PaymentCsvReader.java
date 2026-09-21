package com.ascory.paymentbatch.csv;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Reads payment CSV files.
 * <p>
 * The file is streamed record by record instead of being loaded into memory as a whole,
 * so the batch also works for large files.
 */
@Component
public class PaymentCsvReader {

    private static final CSVFormat FORMAT = CSVFormat.DEFAULT.builder()
            .setHeader()
            .setSkipHeaderRecord(true)
            .setIgnoreEmptyLines(true)
            .setIgnoreSurroundingSpaces(true)
            .setTrim(true)
            .build();

    /**
     * @return all *.csv files of the given directory, sorted by name for a deterministic run
     */
    public List<Path> findCsvFiles(Path directory) {
        if (!Files.isDirectory(directory)) {
            throw new CsvReadException("Input directory does not exist: " + directory.toAbsolutePath());
        }
        try (Stream<Path> files = Files.list(directory)) {
            return files.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".csv"))
                    .sorted(Comparator.comparing(Path::getFileName))
                    .toList();
        } catch (IOException e) {
            throw new CsvReadException("Could not list input directory " + directory.toAbsolutePath(), e);
        }
    }

    /**
     * Streams the given file and hands every record to the consumer.
     *
     * @throws CsvReadException if the file cannot be read or does not contain the expected header
     */
    public void forEachRow(Path file, Consumer<PaymentCsvRow> consumer) {
        String fileName = file.getFileName().toString();
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            skipByteOrderMark(reader);

            try (CSVParser parser = FORMAT.parse(reader)) {
                verifyHeader(parser, fileName);

                Iterator<CSVRecord> iterator = parser.iterator();
                while (true) {
                    CSVRecord record;
                    try {
                        if (!iterator.hasNext()) {
                            break;
                        }
                        record = iterator.next();
                    } catch (RuntimeException e) {
                        // The parser position is no longer reliable after a structural error,
                        // therefore this file is stopped here - other files are still processed.
                        throw new CsvReadException(
                                "Malformed CSV structure in %s near line %d".formatted(fileName, parser.getCurrentLineNumber()), e);
                    }
                    consumer.accept(toRow(record, fileName));
                }
            }
        } catch (IOException e) {
            throw new CsvReadException("Could not read " + file.toAbsolutePath(), e);
        }
    }

    /**
     * Files exported from Excel usually start with a UTF-8 byte order mark. Without skipping it
     * the first header would be read as "\uFEFFpayment_id" and the whole file would be rejected.
     */
    private void skipByteOrderMark(BufferedReader reader) throws IOException {
        reader.mark(1);
        if (reader.read() != '\uFEFF') {
            reader.reset();
        }
    }

    private void verifyHeader(CSVParser parser, String fileName) {
        List<String> headerNames = parser.getHeaderNames();
        List<String> missing = new ArrayList<>(CsvColumns.REQUIRED_INPUT_COLUMNS);
        missing.removeAll(headerNames);
        if (!missing.isEmpty()) {
            throw new CsvReadException("File %s is missing the required column(s) %s. Found header: %s"
                    .formatted(fileName, missing, headerNames));
        }
    }

    private PaymentCsvRow toRow(CSVRecord record, String fileName) {
        return new PaymentCsvRow(
                value(record, CsvColumns.PAYMENT_ID),
                value(record, CsvColumns.RECIPIENT_NAME),
                value(record, CsvColumns.RECIPIENT_IBAN),
                value(record, CsvColumns.AMOUNT),
                value(record, CsvColumns.CURRENCY),
                value(record, CsvColumns.PAYMENT_REFERENCE),
                fileName,
                record.getRecordNumber() + 1); // +1 => line number in the file, header included
    }

    /**
     * Tolerates short records: a line with fewer columns than the header yields empty values
     * which are then reported by the validation rules.
     */
    private String value(CSVRecord record, String column) {
        return record.isSet(column) ? record.get(column) : "";
    }
}

