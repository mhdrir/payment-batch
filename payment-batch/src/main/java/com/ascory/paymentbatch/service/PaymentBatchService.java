package com.ascory.paymentbatch.service;

import com.ascory.paymentbatch.config.BatchProperties;
import com.ascory.paymentbatch.csv.PaymentCsvReader;
import com.ascory.paymentbatch.csv.PaymentCsvRow;
import com.ascory.paymentbatch.csv.PaymentResultCsvWriter;
import com.ascory.paymentbatch.repository.PaymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Orchestrates a batch run: read the input directory, process every record, write the
 * result files and persist the outcome.
 * <p>
 * Failure handling is layered so a problem is always contained at the smallest possible level:
 * a bad record fails only that record, a broken file fails only that file.
 */
@Service
public class PaymentBatchService {

    private static final Logger log = LoggerFactory.getLogger(PaymentBatchService.class);

    // millisecond precision so two runs started within the same second never collide on filename
    private static final DateTimeFormatter FILE_TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmssSSS").withZone(ZoneOffset.UTC);

    private final BatchProperties properties;
    private final PaymentCsvReader csvReader;
    private final PaymentProcessingService processingService;
    private final PaymentRepository paymentRepository;

    public PaymentBatchService(BatchProperties properties,
                               PaymentCsvReader csvReader,
                               PaymentProcessingService processingService,
                               PaymentRepository paymentRepository) {
        this.properties = properties;
        this.csvReader = csvReader;
        this.processingService = processingService;
        this.paymentRepository = paymentRepository;
    }

    /**
     * Executes one batch run.
     *
     * @return the totals of the run (also written to the summary file)
     */
    public BatchSummary execute() {
        Instant startedAt = Instant.now();
        String runTimestamp = FILE_TIMESTAMP.format(startedAt);

        log.info("Starting payment batch - input: {}, output: {}",
                properties.inputDirectory().toAbsolutePath(), properties.outputDirectory().toAbsolutePath());

        BatchSummary totals = new BatchSummary("TOTAL");
        List<BatchSummary> perFile = new ArrayList<>();

        List<Path> inputFiles;
        try {
            inputFiles = csvReader.findCsvFiles(properties.inputDirectory());
        } catch (RuntimeException e) {
            log.error("Input directory could not be read: {}", e.getMessage());
            return totals;
        }

        if (inputFiles.isEmpty()) {
            log.warn("No CSV file found in {} - nothing to do", properties.inputDirectory().toAbsolutePath());
            return totals;
        }

        createOutputDirectory();

        for (Path inputFile : inputFiles) {
            try {
                BatchSummary fileSummary = processFile(inputFile, runTimestamp, startedAt);
                perFile.add(fileSummary);
                totals.merge(fileSummary);
                log.info("Finished {}", fileSummary.toShortString());
            } catch (Exception e) {
                // A broken file must not prevent the remaining files from being processed.
                // TODO: add a test proving one structurally-broken file doesn't stop the others
                //  in a multi-file run - the behavior above already does this, it's just untested
                log.error("Skipping file {} - {}", inputFile.getFileName(), e.getMessage(), e);
                totals.recordSkippedFile(inputFile.getFileName().toString(), e.getMessage());
            }
        }

        Duration duration = Duration.between(startedAt, Instant.now());
        log.info("Batch finished in {} ms - {}", duration.toMillis(), totals.toShortString());
        writeSummaryFile(perFile, totals, runTimestamp, startedAt, duration);
        return totals;
    }

    private BatchSummary processFile(Path inputFile, String runTimestamp, Instant processedAt) {
        String fileName = inputFile.getFileName().toString();
        Path resultFile = properties.outputDirectory().resolve(resultFileName(fileName, runTimestamp));
        BatchSummary summary = new BatchSummary(fileName);

        log.info("Processing {} -> {}", fileName, resultFile.getFileName());

        try (PaymentResultCsvWriter writer = new PaymentResultCsvWriter(resultFile)) {
            csvReader.forEachRow(inputFile, row -> {
                PaymentProcessingResult result = processSafely(row);
                if (!result.isSkippedDuplicate()) {
                    result = persist(result, processedAt);
                }
                writer.write(result);
                summary.record(result);
            });
            // TODO: compare records read to summary.getTotal() and flag a mismatch - nothing
            //  currently catches a file that aborts partway through (see the catch in execute())
        }
        return summary;
    }

    /**
     * Guarantees that a single record can never stop the batch.
     */
    private PaymentProcessingResult processSafely(PaymentCsvRow row) {
        try {
            return processingService.process(row);
        } catch (Exception e) {
            log.error("Unexpected error while processing {}:{} (payment_id={})",
                    row.sourceFile(), row.lineNumber(), row.paymentId(), e);
            return PaymentProcessingResult.failed(row, null,
                    "Unexpected error: " + e.getClass().getSimpleName() + " - " + e.getMessage());
        }
    }

    /**
     * The run continues even if the database is temporarily unavailable; the result file gets a
     * warning appended to its message instead of silently disagreeing with the payments table.
     */
    private PaymentProcessingResult persist(PaymentProcessingResult result, Instant processedAt) {
        try {
            paymentRepository.save(result.toEntity(processedAt));
            return result;
        } catch (Exception e) {
            log.error("Could not persist payment_id={} ({}:{}), continuing with the next record",
                    result.row().paymentId(), result.row().sourceFile(), result.row().lineNumber(), e);
            return result.withPersistenceWarning();
        }
    }

    private void createOutputDirectory() {
        try {
            Files.createDirectories(properties.outputDirectory());
        } catch (IOException e) {
            throw new UncheckedIOException(
                    "Could not create output directory " + properties.outputDirectory().toAbsolutePath(), e);
        }
    }

    private String resultFileName(String inputFileName, String runTimestamp) {
        int dot = inputFileName.lastIndexOf('.');
        String base = dot > 0 ? inputFileName.substring(0, dot) : inputFileName;
        return "%s-result-%s.csv".formatted(base, runTimestamp);
    }

    private void writeSummaryFile(List<BatchSummary> perFile,
                                  BatchSummary totals,
                                  String runTimestamp,
                                  Instant startedAt,
                                  Duration duration) {
        Path summaryFile = properties.outputDirectory().resolve("batch-summary-%s.log".formatted(runTimestamp));
        List<String> lines = new ArrayList<>();
        lines.add("Payment batch run " + runTimestamp);
        lines.add("started at : " + startedAt);
        lines.add("duration   : " + duration.toMillis() + " ms");
        lines.add("");
        perFile.forEach(summary -> {
            lines.addAll(summary.toReportLines());
            lines.add("");
        });
        lines.addAll(totals.toReportLines());

        try {
            Files.write(summaryFile, lines, StandardCharsets.UTF_8);
            log.info("Summary written to {}", summaryFile.toAbsolutePath());
        } catch (IOException e) {
            log.error("Could not write summary file {}", summaryFile.toAbsolutePath(), e);
        }
    }
}
