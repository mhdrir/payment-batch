# Payment Batch – CSV Batch Processing Application

Batch application that reads payment records from CSV files, validates them, classifies them
using the database and writes a result file.

Run it with `docker compose up --build`. It processes `input/payments-to-process.csv`, writes a
result CSV + summary log to `output/`, and exits 0. Rerunning is safe.

* Input: every `*.csv` file in `/app/input`
* Output: one result CSV + one summary log per run in `/app/output`
* Database: H2, schema managed by Flyway, persisted on the host via `./data`
* Runtime: Java 21 / Spring Boot 3.3 (non-web), packaged and executed with Docker
* Resiliency: duplicate `payment_id`s are never reprocessed across runs, result files are written
  atomically, and output values are sanitized against CSV/Excel formula injection – see
  [SOLUTION.md](SOLUTION.md) §2.13-2.18

**Scope:** 
This implements the 5 functional + 3 validation requirements from task.md.
Beyond that, idempotency, atomic writes, and a CSV-injection guard were added because they'd be
non-negotiable none-functionals for a payments pipeline.

---

## Run it

```bash
docker compose up --build
```

The container starts, processes `input/payments-to-process.csv`, writes the result to `output/`
and exits.

```
input/payments-to-process.csv            ->  output/payments-to-process-result-<yyyyMMdd-HHmmssSSS>.csv
                                             output/batch-summary-<yyyyMMdd-HHmmssSSS>.log
```

To rerun with a different file, drop it into `input/` and run the command again. Rerunning with
the same file is safe: `payment_id`s already recorded from an earlier run are reported as
`SKIPPED_DUPLICATE` instead of being reprocessed. Nothing needs to be installed locally except
Docker.

### Build/test without Docker Compose

```bash
# runs the unit + integration tests as well
docker run --rm -v "$PWD":/work -v payment-batch-m2:/root/.m2 -w /work \
  maven:3.9-eclipse-temurin-21 mvn -B clean package
```

With a local JDK 21 + Maven installed, `mvn clean package` works the same way. To run outside
of a container, point the directories to the local folders:

```bash
java -jar target/payment-batch.jar \
  --app.batch.input-directory=input \
  --app.batch.output-directory=output \
  --app.database.path=./data/payments
```

---

## Known limitations

A few gaps are still open, each marked at its exact location in code with `// TODO`:

- Row-count reconciliation — `PaymentBatchService.processFile()`
- Risk-mapping ranges have no non-overlap check — `CustomPaymentRiskMappingRepository`
- Multi-file resilience is untested (the behavior itself already works) — `PaymentBatchService.execute()`

See [SOLUTION.md](SOLUTION.md) §4 for what's considered out of scope entirely
---

## How it works

```
/app/input/*.csv
      │
      ▼
PaymentCsvReader        streams the file record by record (raw String values)
      │
      ▼
PaymentProcessingService  duplicate check -> PaymentValidator (3 checks) -> risk-mapping lookup (database)
      │
      ▼
PaymentProcessingResult SUCCESS + processing type, FAILED + reason, or SKIPPED_DUPLICATE
      │
      ├──────────────► PaymentRepository.save()   -> payments table (one record at a time)
      └──────────────► PaymentResultCsvWriter     -> /app/output/<input>-result-<ts>.csv
```

`PaymentBatchService` orchestrates the run; `PaymentBatchRunner` is the thin `ApplicationRunner`
that triggers it once on startup. The application then terminates.

---

## Reference

### Validation rules

| # | Rule | Failure message example |
|---|------|-------------------------|
| 1 | Amount must be greater than `0` (missing/non-numeric values fail too) | `Amount must be greater than 0 but was -680.61` |
| 2 | IBAN must be present and not empty | `IBAN is missing or empty` |
| 3 | Currency must be `EUR` or `USD` | `Currency 'GBP' is not supported, allowed values are EUR/USD` |

All rules are evaluated for every record, so the result file reports *all* problems of a record,
not just the first one.

### Classification

The processing type is **not** hardcoded. It is resolved from the provided
`custom_payment_risk_mapping` table:

| min_amount | max_amount | processing_type |
|-----------:|-----------:|-----------------|
| 0.01 | 999.99 | `NORMAL` |
| 1 000.00 | 9 999.99 | `FORMAL_APPROVAL_REQUIRED` |
| 10 000.00 | 9 999 999.99 | `HIGH_RISK_REVIEW` |

An amount that is **not covered by any range** (the sample file contains one:
`467 999 097.55 EUR`) is reported as `FAILED` with
`No processing type configured for amount 467999097.55`. It is deliberately not defaulted to
`NORMAL` – silently downgrading an unclassifiable payment would be a real financial risk.

### Output

`<input-file>-result-<timestamp>.csv` – original payment data plus `status`, `processing_type`
and `message`, e.g. `100291,...,322.98,EUR,...,SUCCESS,NORMAL,"Payments below 1,000 are processed normally."`
The `message` of a successful record is the `description` of the matched range, read from the
database, so the result file explains *why* a payment was classified that way.

`batch-summary-<timestamp>.log` – per-file and total counts, e.g.
`records processed : 1000 / success : 939 / failed : 61 / skipped duplicate : 0`.

Every record – successful, failed and skipped-duplicate – is additionally stored in the
`payments` table (skipped-duplicates are the exception: they are reported here but not persisted
again, since the payment_id's original row already is its permanent record), so a run stays
auditable without re-reading the input file.

### Configuration

| Property | Default | Description |
|----------|---------|-------------|
| `app.batch.input-directory` | `/app/input` | scanned for `*.csv` |
| `app.batch.output-directory` | `/app/output` | result + summary files |
| `app.batch.allowed-currencies` | `EUR,USD` | accepted currencies |
| `app.batch.auto-run` | `true` | run the batch on startup (tests set it to `false`) |
| `app.database.path` | `/app/data/payments` | H2 file location |

Each can be overridden with an environment variable, e.g. `APP_BATCH_ALLOWED_CURRENCIES=EUR,USD,GBP`.

### Project layout

```
src/main/java/com/ascory/paymentbatch
├── config/      BatchProperties            – externalised configuration
├── csv/         PaymentCsvReader/Writer    – CSV in and out, raw row DTO, amount parsing
├── domain/      Payment, ProcessingType…   – JPA entities and enums
├── repository/  Spring Data repositories
├── validation/  PaymentValidator           – the 3 rules, in one class
├── runner/      PaymentBatchRunner         – thin ApplicationRunner, triggers one run
└── service/     PaymentBatchService        – orchestration, error containment
                 + PaymentProcessingService (validation, dedup, classification), summary

src/main/resources/db/migration
├── V1__create-custom-mapping.sql   (provided)
└── V2__create-payments.sql         (added – the payments table)
```

### Tests

43 tests, run automatically during `docker compose up --build`.

| Test | Scope |
|------|-------|
| `PaymentValidatorTest` | the 3 rules + "collect all violations" behaviour |
| `PaymentCsvReaderTest` | parsing, short lines, wrong header, file discovery |
| `PaymentResultCsvWriterTest` | atomic result-file writes, CSV/Excel formula-injection guard, persistence-warning message |
| `PaymentProcessingServiceTest` | validation → classification flow, duplicate-payment_id detection, isolated with mocks |
| `CustomPaymentRiskMappingRepositoryIntegrationTest` | classification boundaries against the real migration data |
| `PaymentBatchServiceTest` | a record that fails to persist is reflected in the result file, not silently dropped |
| `PaymentBatchIntegrationTest` | full run: CSV in → result file, summary file, database rows |
| `PaymentBatchRerunIntegrationTest` | rerunning the same file reports every payment_id as skipped-duplicate instead of reprocessing it |
