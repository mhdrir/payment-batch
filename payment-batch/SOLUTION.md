# Solution notes – design decisions, trade-offs, AI usage

This document complements [README.md](README.md) (which explains how to run the application) and
covers *why* the solution looks the way it does, what was left out on purpose, and where AI was used.

**TL;DR** — Spring Boot batch app, no web layer, no Spring Batch. Reads a CSV, validates 3 rules,
classifies via the provided risk-mapping table, writes a result CSV plus an H2 audit trail.
Records are processed and persisted one at a time - deliberately no chunking, since the task
doesn't need it. The one consequential trade-off: `payment_id` has a hard, database-enforced
unique constraint, so a payment that only failed validation can never be retried under the same
id - chosen over a softer check that would allow retries, to guarantee no duplicate reporting
even against a bug in the application layer (2.13). 43 tests, verified via `mvn test` and a real
`docker compose` run.

---

## 1. Interpretation of the task

| Requirement | How it is fulfilled |
|-------------|---------------------|
| Read a CSV file from `/app/input` | `PaymentCsvReader` scans the directory for `*.csv` (more than one file is supported) |
| Parse and process each payment record | Streamed record by record, raw values kept as `String` |
| Validate each record | 3 rules, exactly as specified – see README |
| Use the database to determine the processing type | `PaymentProcessingService` queries `custom_payment_risk_mapping` via `CustomPaymentRiskMappingRepository`, no thresholds in Java |
| Produce a result file in `/app/output` | Result CSV (original data + status + processing type + message) and a summary log |
| Do not stop processing if a single record fails | Failures are contained per record; a broken file only skips that file |
| `payments` table not provided, must be created | `V2__create-payments.sql` |

---

## 2. Key design decisions

**Scope note:** this implements the 5 functional + 3 validation requirements from task.md first.
Beyond that, I added idempotency (2.13), atomic writes (2.15), and a CSV-injection guard (2.16)
because I'd consider them non-negotiable for a real payments pipeline - flagging that explicitly,
since the task asked to stick to what's described. Happy to walk through whether that was the
right call.

18 entries below, grouped here by theme for orientation (numbers refer to the entries themselves,
order is otherwise unchanged):

- **Scope & architecture** — 2.1, 2.2, 2.3, 2.4, 2.8, 2.11, 2.12
- **Correctness & classification** — 2.5, 2.6, 2.10
- **Failure containment** — 2.7, 2.9
- **Resiliency** — 2.13, 2.14, 2.15, 2.16, 2.17, 2.18

### 2.1 Plain `ApplicationRunner` instead of Spring Batch
The task asks for a small, pragmatic solution and lists "complex frameworks" as out of scope.
Spring Batch would add job repository tables, chunk/step configuration and a lot of ceremony for a
single-step job. Records are processed and persisted one at a time (see 2.17) - there is no
chunking to configure in the first place for a bounded, task-scoped input file.
*If the job needed restartability, skip policies, parallel steps, or genuinely large files,
Spring Batch (with real chunked commits) would be the right answer - that is the main scale-up
path for this code.*

### 2.2 Non-web application
It is a batch job: it must start, do its work and terminate with a proper exit code.
`spring.main.web-application-type=none` guarantees that. Consequently the `ports: 8080` mapping from
the compose stub was removed – there is nothing listening. This was a conscious deviation from the
stub, not an oversight.

### 2.3 Raw `String` values in `PaymentCsvRow`
Converting the amount during parsing would turn one malformed value into an exception that aborts
the file. Keeping the row raw lets the validation layer report it as a normal `FAILED` record, and it
also lets the result file echo the original input byte-for-byte.

### 2.4 One `PaymentValidator`, three inline checks
The task specifies exactly 3 rules, so `PaymentValidator` checks all of them directly instead of
through a pluggable rule-bean abstraction - a 4th rule, if the task ever asked for one, is a 4th
method, not a 4th class. All 3 checks are evaluated instead of failing fast, so a record with three
problems reports three reasons in one run.

### 2.5 Classification lives in the database
The amount ranges are read from `custom_payment_risk_mapping` per record. That is what the task asks
for, and it means the classification can be changed with an `UPDATE` instead of a redeployment.
With 1 000 records against an embedded H2 this is irrelevant in terms of performance; for millions of
records the table (3 rows) would be loaded once and matched in memory.

The task's objective mentions that the data is *"enriched"* using the database, so the lookup returns
the `description` of the matched range as well and uses it as the `message` of a successful record
("Payments below 1,000 are processed normally."). All four columns of the provided mapping table are
therefore actually used, and the result file explains *why* a payment was classified that way.

### 2.6 Amount outside every configured range → `FAILED`
The sample file contains `467 999 097.55 EUR`, which is above the highest `max_amount`
(`9 999 999.99`). The repository therefore returns `Optional.empty()` and the record is failed with
an explicit message. Defaulting to `NORMAL` would mean the largest payment in the file is processed
with the lowest scrutiny – exactly the wrong direction for a bank.

### 2.7 Failure containment is layered
* bad record → only that record fails (`processSafely`)
* database problem while persisting a record → logged, and reflected in that record's result (see 2.17), the run continues
* structurally broken/unreadable file → only that file is skipped, remaining files are processed
* the run summary lists skipped files and per-status counts

### 2.8 Runner and orchestration are separate
`PaymentBatchRunner` (the `ApplicationRunner`) only calls `PaymentBatchService.execute()`. The reason
is testability: Spring Boot executes `ApplicationRunner` beans in `@SpringBootTest` as well, so the
end-to-end test would have run the batch twice (I hit exactly that while writing the test). With the
split, tests disable the runner via `app.batch.auto-run=false` and call the service explicitly.

### 2.9 Every outcome is persisted, including failures and skipped duplicates
The `payments` table stores successes, failures and skipped-duplicates (with nullable
`amount`/`processing_type` and a reason), plus source file and line number. That makes a run
auditable without re-reading the input.

### 2.10 `BigDecimal` everywhere for money
No `double`/`float` for monetary values – rounding errors are unacceptable, and `DECIMAL(19,2)` in the
schema matches the provided mapping table.

### 2.11 Flyway
The provided `V1__create-custom-mapping.sql` already follows Flyway's naming convention, so Flyway
manages the schema and `spring.jpa.hibernate.ddl-auto=validate` verifies that the entities match it.
The provided migration was moved into `src/main/resources/db/migration` **unchanged**.

### 2.12 Build inside Docker
The Dockerfile is multi-stage (`maven:3.9-eclipse-temurin-21` → `amazoncorretto:21.0.11` as given in
the stub). Nobody needs a local JDK/Maven, and dependency resolution is a separate layer so code
changes rebuild fast.

### 2.13 Duplicate `payment_id`s are never reprocessed ("no duplicate reporting")
A payment that has already been recorded in `payments` – successfully or not, in this run or an
earlier one – must never be silently processed and reported a second time; for a bank, that risk
(double-reporting, or worse, double-triggering a downstream payment) matters more than almost
anything else in this system. `payment_id` has a `UNIQUE` index (`ux_payments_payment_id`), and
`PaymentProcessingService` checks for an existing row before validating or classifying a record; if
one exists, the record is reported as `SKIPPED_DUPLICATE` instead. A blank/missing `payment_id` is
normalised to SQL `NULL` before insert, since multiple `NULL`s never conflict under the index but
multiple empty strings would – malformed rows with no id must never be treated as duplicates of
each other. Because each record is persisted immediately, before the next one is processed (see
2.17), the database itself is always up to date for the next check - no separate in-memory
tracking is needed to catch a duplicate appearing twice within the same run.

**Open question, not a settled call:** the index is on `payment_id` alone, not
`(payment_id, status)`. This means once a payment_id has been recorded – even if that attempt only
*failed* validation – it can never be successfully processed under the same id again, even after
the source data is corrected. A softer, application-level-only check (dedupe only against
previously-*successful* payments) would have allowed such retries, at the cost of a weaker
guarantee if that check ever had a bug. This is a business call - hard lock vs. allowing retry of
a corrected, previously-failed payment - that I'd want product/ops to confirm before this ships,
not something I think engineering should decide alone. I implemented the harder constraint as the
safer default in the meantime.

### 2.14 The audit database is persisted on the host
`docker-compose.yaml` mounts `./data:/app/data` in addition to `input`/`output`. Without this, the
H2 file would live only in the container's writable layer and be lost whenever the container is
recreated – which would silently defeat both the audit trail and the duplicate-detection in 2.13,
since a fresh, empty database would no longer know about any payment_id from a previous run.

### 2.15 Result files are written atomically
`PaymentResultCsvWriter` writes to a sibling `*.tmp` file and only moves it to the final name
(`ATOMIC_MOVE`, with a plain-replace fallback) once writing is complete. A process killed mid-run
leaves a `.tmp` file behind instead of a half-written file sitting under the real result name –
the result file is the deliverable of this batch, so a reader must never be able to mistake a
partial file for a complete one.

### 2.16 Output values are sanitized against CSV/Excel formula injection
The input CSV is untrusted. `recipient_name`, `recipient_iban`, `payment_id`, `amount`, `currency`
and `payment_reference` are echoed into the result file, and a value starting with `=`, `+`, `-` or
`@` is interpreted as a formula by Excel/Sheets (CWE-1236) rather than as plain text. Any such value
is quote-prefixed (`'`) before being written – the standard mitigation – so opening the result file
in a spreadsheet can never execute attacker-controlled input. `status`, `processing_type` and
`message` are not sanitized, since they are app-generated text with a fixed prefix and never start
with raw input.

### 2.17 The result file never disagrees with the audit database
Each record is persisted to `payments` *before* its own line is written to the result file, not
after - there is no batching in between, so a record's true persistence outcome is always known
before it is reported. If persisting a record fails, it gets
`NOT PERSISTED TO AUDIT DATABASE - retry this payment_id manually` appended to its message, while
its original `SUCCESS`/`FAILED` status and reason are left untouched. Overwriting the status with a
dedicated "persistence failed" value was considered and rejected: a payment can simultaneously be
"valid, would classify as NORMAL" *and* "not yet in the audit database", and an operator needs both
facts, not one silently replacing the other.

### 2.18 Result and summary filenames use millisecond precision
`yyyyMMdd-HHmmssSSS` instead of second precision, so two runs started within the same second (e.g.
an automated retry) never collide on filename and silently overwrite each other's result.

---

## 3. Verified result of the provided sample file

`docker compose up --build` on `input/payments-to-process.csv` (1 000 records, ~0.9 s):

```
records processed : 1000
success           : 939
failed            : 61
skipped duplicate : 0
by processing type:
  NORMAL                     699
  FORMAL_APPROVAL_REQUIRED   180
  HIGH_RISK_REVIEW            60
by failure reason :
  amount                      20   (0.00 and negative values)
  currency                    20   (GBP / CHF / XXX)
  recipient_iban              20   (empty)
  no_processing_type_mapping   1   (467 999 097.55 EUR – above every configured range)
```

Rerunning the same command against the same `input/` and `data/` reports all 1000 records as
`skipped duplicate` instead of reprocessing them – see 2.13.

---

## 4. Considered out of scope

| Topic | Current state | Next step |
|-------|---------------|-----------|
| IBAN validation | presence only, as specified | MOD-97 checksum + country-length check |
| Result file naming | `<input>-result-<timestamp>.csv` | move processed input files into an `archive/` folder |
| Currency handling | `EUR`/`USD` as configured strings | ISO-4217 + amount ranges per currency (a 10 000 USD range ≠ 10 000 EUR range) |
| Mapping lookup | one query per record | cached at startup + `@Scheduled` refresh |
| Insert throughput | one transaction per record; no batching, since the provided file doesn't warrant it | reintroduce chunked commits if a genuinely large file ever needs to be processed |
| Container user | runs as root so it can write into the bind-mounted `output/` | non-root user + matching host UID/GID mapping |
| Observability | log output + summary file; exit code is always 0 regardless of failure ratio | Micrometer counters, exit code ≠ 0 when the failure ratio exceeds a threshold |
| Large files | streaming reads/writes (works); one DB round-trip per record | chunked commits if throughput on much larger files ever matters |
| Concurrent runs | no lock; two containers hitting the same input/DB at once is not addressed | file-based or DB advisory lock at batch start |
| Aggregate failure breakdown | flat counters only in the summary log (by design, see 2.9) | reinstate a per-category (by rule, by processing type) breakdown if diagnosing a repeated problem becomes relevant |
| Security | out of scope per task (secrets, auth) | secrets for a real database |

Nothing in the task description was left unimplemented – the items above are deliberate scope
decisions, not blockers.

---

## 5. AI usage

AI was used as a supporting tool throughout, both GitHub Copilot and Claude Code for:

* **Scaffolding and boilerplate** – `pom.xml`, entity getters, Javadoc phrasing, the first draft of
  the README, and the implementation and tests for the duplicate-detection, persistence-durability,
  atomic-write, formula-injection and result/CSV-consistency design decisions in section 2.
* **Test data variations** – parameterised test cases for the validation rules, the boundary values
  of the mapping ranges, and the rerun/duplicate scenarios.
* **Review-style questions and independent review passes** – e.g. "what happens if the CSV has
  fewer columns than the header", and a structured resiliency/idempotency review that surfaced
  several of the design decisions in section 2.

What was *not* delegated and was decided/verified by me:

* the overall architecture and package structure,
* the decision against Spring Batch and against a web application,
* the handling of the out-of-range amount (`467 999 097.55`) – I found this record while inspecting
  the sample data and decided it must fail rather than default,
* the failure-containment layering (record / file),
* the database schema for `payments`, including nullable columns for failed and skipped-duplicate
  records,
* Various explicit decisions,
* verification: the full test suite (43 tests) and the Docker Compose run were executed and the
  output files were inspected by me, not just accepted from the agent's report.

Every generated line was reviewed; I can explain and defend each part of the solution.
