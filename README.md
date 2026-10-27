# eFacture PME

[![CI](https://github.com/MamitianaRak/efacture/actions/workflows/ci.yml/badge.svg)](https://github.com/MamitianaRak/efacture/actions/workflows/ci.yml)

**Factur-X e-invoicing for French small businesses, built with Spring Boot 4 (Java 25) and Angular 22.**

France is making electronic invoicing mandatory between VAT-registered businesses. Every company must be able to **receive** e-invoices since **1 September 2026**, and small and medium businesses must **issue** them from **1 September 2027**. Invoices travel through government-registered platforms (*plateformes agréées*) in structured formats such as **Factur-X**: a readable PDF with machine-readable XML embedded inside.

eFacture PME lets a small business create compliant invoices and credit notes, enforces the legal rules a spreadsheet cannot, produces Factur-X files that pass the official EN 16931 validation, and follows each invoice on the platform until it is paid.

> **Demo project.** No invoice is sent to any tax authority or real platform. Connecting to a real *plateforme agréée* requires registration, so the platform is **simulated** behind an interface a real client could implement.

## Features

- **Seller profile and customers**, with SIREN (Luhn checksum) and French VAT number (key) validation
- **Invoice lifecycle**: draft, issue, credit note. An issued invoice can no longer be edited or deleted; corrections go through a credit note that references it
- **Gapless numbering** per document type and year: `F-2026-00001`, `AV-2026-00001`, with no gap or duplicate even under concurrent issuance
- **Mandatory mentions** checked before issuance; every missing item is reported at once (`422` with the list)
- **Amounts** in `BigDecimal`, VAT computed per rate on the rate total, as EN 16931 requires
- **Factur-X export**: PDF/A-3 with embedded CII XML, EN 16931 profile, validated with the Mustang validator
- **Simulated registered platform**: submission, then the lifecycle statuses of the reform (deposited, rejected, delivered, approved, refused, paid) with realistic rejection and refusal reasons
- **Roles with JWT authentication**: the manager runs everything; the accountant reads, follows payments and handles credit notes
- **Dashboard**: yearly revenue, collected, receivable and overdue amounts, monthly chart, invoices needing action
- **Angular UI** with live totals preview, live platform status timeline and French number and date formats

## Architecture

```mermaid
flowchart LR
    UI["Angular 22<br/>signals, guards, interceptor"] -- "REST / JSON + JWT" --> API["Spring Boot 4<br/>Spring Security"]
    API --> DB[("PostgreSQL<br/>Flyway migrations")]
    API --> FX["Mustangproject<br/>Factur-X PDF/A-3 + XML"]
    API -- "PlatformGateway" --> PA["Simulated platform<br/>(async, validates Factur-X)"]
    PA -- "status notifications" --> API
```

| Layer | Stack |
|---|---|
| Backend | Java 25, Spring Boot 4.1, Spring Data JPA / Hibernate, Bean Validation, Flyway |
| Security | Spring Security 7, OAuth2 resource server, HS256 JWT, bcrypt, method security |
| Database | PostgreSQL 17 |
| Factur-X | Mustangproject 2.26 (generation and validation) |
| Frontend | Angular 22, TypeScript, reactive forms, signals |
| Tests | JUnit 5, Mockito, Testcontainers, MockMvc, Spring Security Test, Vitest |

The backend is organised by feature (`company`, `customer`, `invoice`, `facturx`, `platform`, `dashboard`, `security`), each with its entity, repository, service, controller and DTO records. Flyway owns the schema; Hibernate maps the entities and validates them against it at startup.

## Technical decisions

**Gapless numbering without a gap on failure.** French law requires a continuous, chronological sequence. The number is assigned at issuance, never at draft creation, by a single atomic upsert:

```sql
INSERT INTO invoice_sequence (type, year, last_value) VALUES (:type, :year, 1)
ON CONFLICT (type, year) DO UPDATE SET last_value = invoice_sequence.last_value + 1
RETURNING last_value
```

It runs inside the issuing transaction: concurrent issuances queue on the row lock, and if the issuance fails, the increment rolls back with it. A test issues 20 invoices in parallel and checks they receive exactly numbers 1 to 20.

**Business rules live in the entity.** `Invoice` exposes `draft()`, `issue()`, `creditNoteFor()` and `updateDraft()` instead of setters, so an issued invoice cannot be modified whatever code calls it. A database `CHECK` constraint backs it up: a draft never has a number, an issued document always has one.

**Optimistic locking.** `@Version` on `Invoice` prevents two concurrent edits or issuances of the same draft from both succeeding; the loser gets a `409`.

**VAT per rate, not per line.** EN 16931 computes VAT once per rate on the sum of that rate's lines. Rounding line by line gives different totals (three lines of 0.10 at 5.5 %: 0.03 line by line, 0.02 on the total), and a Factur-X validator would reject the mismatch.

**Factur-X is rebuilt on each download** from the frozen invoice, so the file can never drift from the data.

**The platform behind a port.** The application only knows the `PlatformGateway` interface. The simulator implements it like a real platform would behave: it works asynchronously, really validates each Factur-X file, rejects or refuses at configurable rates, and sends status notifications back as events.

**A state machine for the lifecycle.** `PlatformStatus` lists the allowed transitions; `Submission.apply()` refuses impossible ones (a buyer cannot approve an invoice that was never delivered) and ignores a status received twice, since platforms may deliver the same notification more than once. Every change is kept in a history.

**One live submission per invoice, enforced by the database.** A partial unique index (`WHERE status <> 'REJETEE'`) guarantees it even when two clicks arrive at the same time; a new submission is only possible after a technical rejection.

**Authorization next to the business rule.** Rules sit on the controllers with `@PreAuthorize`, including a business-aware one: `hasRole('MANAGER') or @invoiceAccess.isCreditNote(#id)` lets the accountant issue credit notes but not invoices. Login returns the same `401` for an unknown email and a wrong password, so it does not reveal which accounts exist.

**Dashboard in SQL.** Figures are aggregated in PostgreSQL with CTEs (`DISTINCT ON` for each invoice's latest platform status, credited invoices, receivables) rather than by loading entities. An invoice is receivable when it is issued, not cancelled by a credit note, and neither paid nor refused.

**Errors follow RFC 9457** (`ProblemDetail`), with the list of missing mentions or invalid fields, which the UI displays as is.

## Tests

| Suite | Count | What it covers |
|---|---|---|
| Backend unit (JUnit 5, Mockito) | 41 | SIREN and VAT keys, totals and rounding, invoice rules, mandatory mentions, service behaviour, platform state machine |
| Factur-X | 5 | Generated files pass the Mustang validator (PDF/A-3 and EN 16931 rules); embedded XML carries the same totals |
| Backend integration (Testcontainers) | 17 | API lifecycle on a real PostgreSQL, 20 concurrent issuances, real logins and JWTs per role, platform scenarios (approved then paid, rejected then resubmitted, refused, duplicate notification), every dashboard figure |
| Frontend (Vitest) | 7 | Totals preview matching the backend figures, auth interceptor (token, expiry, 401) |

Testcontainers caught a real schema mismatch (`CHAR` columns against `String` fields) that unit tests could not see. The simulator is driven step by step in tests (`tick()`), with its rates set to 0 or 1, so platform scenarios are deterministic.

## Run locally

Requirements: Java 25, Maven, Node.js 24, Docker.

```bash
# 1. Database (PostgreSQL on port 5433)
docker compose up -d

# 2. Backend on http://localhost:8080
cd backend
mvn spring-boot:run

# 3. Frontend on http://localhost:4200 (proxies /api to the backend)
cd frontend
npm install
npx ng serve
```

Open http://localhost:4200 and sign in with a demo account (password `demo1234`):

| Account | Role |
|---|---|
| `gerant@demo.fr` | Manager: everything |
| `comptable@demo.fr` | Accountant: read access, payments, credit notes |

Then fill in **Mon entreprise**, add a customer, create and issue an invoice, and submit it to the platform: its status moves every 5 seconds.

Configuration (environment variables):

| Variable | Default | Purpose |
|---|---|---|
| `JWT_SECRET` | development value | HS256 signing key, at least 32 bytes. **Set it in any deployed environment.** |
| `DEMO_USERS` | `true` | Create the two demo accounts at startup |
| `DATABASE_URL`, `DATABASE_USER`, `DATABASE_PASSWORD` | local Docker database | PostgreSQL connection |

The simulator's pace and rates are set in `application.properties` (`app.platform.simulator.*`).

Run the tests:

```bash
cd backend && mvn test        # needs Docker for the Testcontainers suite
cd frontend && npx ng test --watch=false
```

## API

All endpoints except login require a bearer token.

| Method | Path | Role | Description |
|---|---|---|---|
| `POST` | `/api/auth/login` | public | Returns a JWT |
| `GET` | `/api/auth/me` | any | Current user and role |
| `GET` | `/api/dashboard?year=` | any | Yearly figures and items needing action |
| `GET` / `PUT` | `/api/company` | any / manager | Seller profile |
| `GET` / `POST` | `/api/customers` | any / manager | List, create customers |
| `GET` / `PUT` | `/api/customers/{id}` | any / manager | Read, update a customer |
| `GET` | `/api/invoices?status=` | any | List invoices, optionally by status |
| `POST` | `/api/invoices` | manager | Create a draft |
| `GET` / `PUT` `DELETE` | `/api/invoices/{id}` | any / manager | Read, edit or delete a draft |
| `POST` | `/api/invoices/{id}/issue` | manager, or accountant for a credit note | Issue: assigns the number, freezes the document |
| `POST` | `/api/invoices/{id}/credit-note` | manager, accountant | Create a credit note cancelling an issued invoice |
| `GET` | `/api/invoices/{id}/facturx` | any | Download the Factur-X PDF |
| `GET` / `POST` | `/api/invoices/{id}/submission` | any / manager | Latest platform submission with its history; submit |
| `POST` | `/api/invoices/{id}/submission/payment` | manager, accountant | Declare the invoice paid (*encaissée*) |
| `GET` | `/api/submissions/latest-statuses` | any | Latest platform status of each invoice |

## Roadmap

- **Kafka** for platform notifications, with a transactional **outbox** (see limitations)
- **Supplier invoices**: import a Factur-X file and read its XML
- Docker images, GitHub Actions CI, online demo

## Limitations

- **Submission is not atomic with the platform call.** If the platform accepts a file and the database write then fails, the platform knows a submission we do not. The standard fix is a transactional outbox, planned with Kafka.
- The simulator keeps its state in memory: pending submissions are lost on restart.
- One instance serves a single issuing company.
- The PDF is Mustang's standard rendering of the invoice data, not a branded layout.
- The buyer's electronic invoicing address, used by the reform to route invoices between platforms, is not handled yet.
- JWTs are stored in `sessionStorage`: closing the tab ends the session. They cannot be revoked before they expire (8 hours).

The full specification (in French) is in [docs/CAHIER_DES_CHARGES.md](docs/CAHIER_DES_CHARGES.md).
