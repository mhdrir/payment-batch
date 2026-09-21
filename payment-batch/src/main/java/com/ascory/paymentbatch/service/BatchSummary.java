package com.ascory.paymentbatch.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Mutable counters of a batch run (per file and for the whole run).
 */
public class BatchSummary {

    private final String scope;
    private long total;
    private long success;
    private long failed;
    private long skippedDuplicate;
    private final List<String> skippedFiles = new ArrayList<>();

    public BatchSummary(String scope) {
        this.scope = scope;
    }

    public void record(PaymentProcessingResult result) {
        total++;
        if (result.isSuccess()) {
            success++;
        } else if (result.isSkippedDuplicate()) {
            skippedDuplicate++;
        } else {
            failed++;
        }
    }

    public void recordSkippedFile(String fileName, String reason) {
        skippedFiles.add("%s (%s)".formatted(fileName, reason));
    }

    public void merge(BatchSummary other) {
        this.total += other.total;
        this.success += other.success;
        this.failed += other.failed;
        this.skippedDuplicate += other.skippedDuplicate;
        this.skippedFiles.addAll(other.skippedFiles);
    }

    public String getScope() {
        return scope;
    }

    public long getTotal() {
        return total;
    }

    public long getSuccess() {
        return success;
    }

    public long getFailed() {
        return failed;
    }

    public long getSkippedDuplicate() {
        return skippedDuplicate;
    }

    public List<String> getSkippedFiles() {
        return Collections.unmodifiableList(skippedFiles);
    }

    /** Single line version used for log output. */
    public String toShortString() {
        return "%s: %d records, %d SUCCESS, %d FAILED, %d SKIPPED_DUPLICATE"
                .formatted(scope, total, success, failed, skippedDuplicate);
    }

    /** Multi line version used for the summary file. */
    public List<String> toReportLines() {
        List<String> lines = new ArrayList<>();
        lines.add(scope);
        lines.add("  records processed : " + total);
        lines.add("  success           : " + success);
        lines.add("  failed            : " + failed);
        lines.add("  skipped duplicate : " + skippedDuplicate);
        if (!skippedFiles.isEmpty()) {
            lines.add("  skipped files     :");
            skippedFiles.forEach(file -> lines.add("    " + file));
        }
        return lines;
    }
}
