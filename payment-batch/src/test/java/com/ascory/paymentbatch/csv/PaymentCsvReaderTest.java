package com.ascory.paymentbatch.csv;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentCsvReaderTest {

    private static final String HEADER = "payment_id,recipient_name,recipient_iban,amount,currency,payment_reference";

    private final PaymentCsvReader reader = new PaymentCsvReader();

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("reads all records and keeps the raw values")
    void readsRecords(@TempDir Path dir) throws IOException {
        Path file = write(dir, "payments.csv",
                HEADER,
                "100291,Recipient 100291,DE00000000000000100291,322.98,EUR,Invoice 100291",
                "100979,Recipient 100979,,776.45,EUR,Missing iban 100979");

        List<PaymentCsvRow> rows = readAll(file);

        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).paymentId()).isEqualTo("100291");
        assertThat(rows.get(0).amount()).isEqualTo("322.98");
        assertThat(rows.get(0).lineNumber()).isEqualTo(2);
        assertThat(rows.get(1).iban()).isEmpty();
    }

    @Test
    @DisplayName("a line with fewer columns yields empty values instead of an exception")
    void toleratesShortLines(@TempDir Path dir) throws IOException {
        Path file = write(dir, "payments.csv",
                HEADER,
                "100291,Recipient 100291,DE00000000000000100291");

        List<PaymentCsvRow> rows = readAll(file);

        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst().amount()).isEmpty();
        assertThat(rows.getFirst().currency()).isEmpty();
    }

    @Test
    @DisplayName("a file exported with a UTF-8 byte order mark is still readable")
    void toleratesByteOrderMark(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("bom.csv");
        Files.writeString(file, "\uFEFF" + HEADER + System.lineSeparator()
                + "100291,Recipient 100291,DE00000000000000100291,322.98,EUR,Invoice 100291" + System.lineSeparator());

        List<PaymentCsvRow> rows = readAll(file);

        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst().paymentId()).isEqualTo("100291");
    }

    @Test
    @DisplayName("a file without the expected header is rejected as a whole")
    void rejectsUnknownHeader(@TempDir Path dir) throws IOException {
        Path file = write(dir, "wrong.csv", "a,b,c", "1,2,3");

        assertThatThrownBy(() -> readAll(file))
                .isInstanceOf(CsvReadException.class)
                .hasMessageContaining("missing the required column");
    }

    @Test
    @DisplayName("only csv files of the input directory are picked up, sorted by name")
    void findsCsvFilesOnly() throws IOException {
        write(tempDir, "b.csv", HEADER);
        write(tempDir, "a.csv", HEADER);
        write(tempDir, "notes.txt", "ignored");
        Files.createDirectory(tempDir.resolve("subdir"));

        List<Path> files = reader.findCsvFiles(tempDir);

        assertThat(files).extracting(path -> path.getFileName().toString())
                .containsExactly("a.csv", "b.csv");
    }

    @Test
    @DisplayName("a missing input directory is reported")
    void rejectsMissingDirectory() {
        assertThatThrownBy(() -> reader.findCsvFiles(tempDir.resolve("does-not-exist")))
                .isInstanceOf(CsvReadException.class);
    }

    private List<PaymentCsvRow> readAll(Path file) {
        List<PaymentCsvRow> rows = new ArrayList<>();
        reader.forEachRow(file, rows::add);
        return rows;
    }

    private Path write(Path dir, String name, String... lines) throws IOException {
        Path file = dir.resolve(name);
        Files.write(file, List.of(lines));
        return file;
    }
}


