# 🛠️ Task – CSV Batch Processing Application

## Introduction

**Please read this introduction carefully.**

This task is meant to be finished within 6 hours.
It can happen, that you cannot finish parts of the task due to knowledge or time. 
If this happens, please describe in a separate Markdown-file or  at the end of this file, 
how you wanted it to solve and what blocked you there (high-level).

This will not be evaluated as bad. This helps us to understand what ideas you have/had and how you
describe them.

Tips/hints:

1. Try to fulfill the requirements only as described in the task, not as how you can fulfill it (we can discuss this in the in-person session).
2. Try to fulfill one technical requirement after another to not overthrow and/or overcomplicate the start of your implementation.

## Objective

Implement a small batch application that processes payment data from a CSV file, 
enriches and validates the data using a database, and writes the results to 
an output file.

Provide the final implementation either in a zip file or in a hosted repository (Gitlab, Github, etc.).

---

## 🧩 Functional Requirements

Your application should:

1. Read a CSV file from the `/app/input` directory
2. Parse and process each payment record
3. Validate each record using the provided validation rules
4. Use the database to determine the processing type of each payment
5. Produce a result file in the `/app/output` directory

---

## 🧩 Non-functional Requirements

- Do not stop processing if a single record fails
- Focus on clean, maintainable code
- Keep the solution simple and pragmatic

---

## ⚙️ Technical Requirements

- Java (preferably Spring Boot + Spring Data)
- Database interaction (H2)
- Docker support using the provided `docker-compose` stub
- The application should run inside a Docker container
- The following directories are mounted:
  - `input` → `/app/input`
  - `output` → `/app/output`
- This table determines how payments should be classified:
  - `NORMAL`
  - `FORMAL_APPROVAL_REQUIRED`
  - `HIGH_RISK_REVIEW`

⚠️ The `payments` table is **not provided** and must be created by you.

---

## 📥 Input

- A CSV file will be provided in the `input` directory
- The format of the CSV file is predefined (see example data)

---

## 🧪 Validation Rules

Apply the following rules:

1. Amount must be greater than `0`
2. IBAN must be present and not empty
3. Currency must be either `EUR` or `USD`

---

## 📤 Output

- Generate a result file (e.g. CSV or a simple log file) in `/app/output`
- Each processed record should include:
  - original payment data
  - processing status (e.g. SUCCESS / FAILED)
  - optional message or reason

---

## 🚫 Out of Scope

- REST APIs
- Authentication / Security
- Cloud deployment
- Complex frameworks

--- 

## Disclaimer

You are allowed to use AI as a helper. But you should be able to explain the solution that you deliver!
At Ascory Bank, we see AI as a supporting tool. Which means, you should understand what you get as an output and if the
given solution is matching the requirements.
Please be transparent and document the sections that were supported by AI.

---

## Solution

The implementation notes, design decisions, trade-offs and the AI transparency section are in
**[SOLUTION.md](SOLUTION.md)**; instructions on how to run it are in **[README.md](README.md)**.

TL;DR: `docker compose up --build` → result CSV + summary log in `output/`.
Sample file result: 1 000 records, 939 SUCCESS, 61 FAILED (20 amount, 20 currency, 20 IBAN,
1 amount outside every configured range).
