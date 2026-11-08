# LedgerLine — Money movement with a ledger you can prove

React + Vite · Java 21 + Spring Boot 3.3 · PostgreSQL 16 · Maven · Spring Security + JWT · Spring Data JPA

> **Honest status:** this source was written without access to Maven, npm or PostgreSQL, so it has **not been compiled or run yet**.
> The first time you run it you may hit small compile/runtime errors. Paste the exact error text to your assistant and fix it before the demo.
> Do not describe the test results as "passed" until `mvn test` and `npm test` actually pass on your machine.

---------------------------------------------------------------------

## 1. Install once (Windows)

| Tool | Version | Check |
|---|---|---|
| JDK | 21 | `java -version` |
| Maven | 3.9+ | `mvn -v` (install: `winget install Apache.Maven`) |
| Node.js | 20 or 22 LTS | `node -v` |
| PostgreSQL | 15/16 | `psql --version` (installer from postgresql.org; remember the `postgres` password you choose) |

## 2. Create the databases (once)

PowerShell (enter your own postgres password when asked):

```powershell
psql -U postgres -c "CREATE DATABASE ledgerline;"
psql -U postgres -c "CREATE DATABASE ledgerline_test;"
```

(No local PostgreSQL? Use Docker instead: `$env:DB_PASSWORD="choose-a-password"; docker compose up -d`, then create `ledgerline_test` the same way.)

## 3. Run the backend (terminal 1)

```powershell
cd backend
$env:DB_PASSWORD = "<your postgres password>"      # required, there is no default
mvn spring-boot:run
```

* API: http://localhost:8080 — Flyway creates all tables automatically on first start.
* Two demo users are created on first start (**local demo only**): `admin@ledgerline.local` and `auditor@ledgerline.local`,
  password `ChangeMe123!`. Pick your own with `$env:LEDGERLINE_SEED_PASSWORD="..."` **before the first start**.
* Optional: `$env:LEDGERLINE_JWT_SECRET="<random string, 32+ chars>"`.

## 4. Run the frontend (terminal 2)

```powershell
cd frontend
npm install
npm run dev
```

Open http://localhost:5173 (the dev server proxies `/api` to port 8080).

## 5. Run the tests

```powershell
cd backend ; $env:DB_PASSWORD = "<your postgres password>" ; mvn test     # needs the ledgerline_test database
cd ..\frontend ; npm test
```

Backend tests cover: 401/403 rules, BCrypt storage, ownership, deposit/withdraw/transfer, invalid amounts, overdraft refusal with
no partial writes, duplicate keys (sequential **and** concurrent), 10 concurrent withdrawals, opposite concurrent transfers (deadlock
check), immutable ledger, DB-level "debits = credits" rollback, DB-level negative-balance refusal, audit of rejected attempts.

---------------------------------------------------------------------

## 6. Architecture in one minute

```
React (Vite)  --JSON/JWT-->  Spring Boot  --JPA-->  PostgreSQL
 forms, tables   /api/**      Controller -> Service -> Repository      tables + constraints + triggers
```

* **Controllers** (`web/`) parse requests, check roles (`@PreAuthorize`), return DTOs. No business logic.
* **Services** (`service/`) hold the rules. `PaymentExecutor` is the only code that moves money.
* **Repositories** (`repo/`) talk to PostgreSQL. **Entities** (`domain/`) map to tables.
* **Security** (`security/`): login returns a signed JWT; a filter validates it on each request and loads the user (and their *current* role) from the DB.

### The accounting model (double-entry)
`balance = total CREDITS − total DEBITS` for every account. A special `EXTERNAL-CASH` account is the outside world, so **every** transaction has exactly one DEBIT and one CREDIT of the same amount:

| Action | Debit | Credit |
|---|---|---|
| Deposit | EXTERNAL-CASH | your account |
| Withdrawal | your account | EXTERNAL-CASH |
| Transfer | sender | receiver |

Because every transaction is balanced, **all balances in the system always add up to exactly 0** (money is conserved) — the Overview page for Admin/Auditor proves it live.

### Transaction flow (`PaymentExecutor.post`, one DB transaction)
1. Find the two account **ids** (no entity is loaded yet — see note below).
2. **Lock both rows** `SELECT … FOR UPDATE`, always in ascending id order (prevents deadlocks). Competing requests wait here.
3. Check the caller **owns** the account (403 otherwise).
4. **Idempotency**: same user + same `Idempotency-Key` + same payload → return the original result (`200`, header `Idempotent-Replay: true`); different payload → `409`.
5. Rules: accounts active, **sufficient funds** (checked on the freshly locked balance).
6. Insert transaction + 2 ledger entries, update both balances, write audit row.
7. Commit. PostgreSQL then re-checks: debits = credits (deferred trigger), balance ≥ 0 (CHECK), key unique (UNIQUE).
If anything throws, the whole thing rolls back — no partial updates. Rejected attempts are audited afterwards, in `PaymentService` (outside the rolled-back transaction).

*Why ids first?* If Hibernate loaded the account before locking it, it could keep a stale balance in memory. Loading the entity only through the locking query guarantees we see the latest committed balance.

### Defence in depth (what protects you even if app code has a bug)
| Requirement | Application | Database |
|---|---|---|
| No duplicate payment | idempotency lookup under lock | `UNIQUE(initiated_by, idempotency_key)` |
| No overspending | balance check under row lock | `CHECK (is_external OR balance >= 0)` |
| Books balance | two entries per tx | deferred trigger `trg_ledger_balanced` |
| Immutable history | no update/delete code | triggers block UPDATE/DELETE/TRUNCATE on `ledger_entries`, `transactions`, `audit_logs` |
| Atomicity | `@Transactional` | transaction rollback |

## 7. Database schema (`backend/src/main/resources/db/migration/V1__schema.sql`)
`users(id, email UNIQUE, password_hash, full_name, role, created_at)` ·
`accounts(id, account_number UNIQUE, owner_id→users, name, balance NUMERIC(19,2), status, is_external, created_at)` ·
`transactions(id, reference UUID, type, amount NUMERIC(19,2) >0, status, from_account_id, to_account_id, description, initiated_by, idempotency_key, request_hash, created_at)` ·
`ledger_entries(id, transaction_id, account_id, direction DEBIT|CREDIT, amount, balance_after, created_at)` ·
`audit_logs(id, user_id, action, entity_type, entity_id, details, created_at)`.

## 8. Roles
| | ACCOUNT_HOLDER | AUDITOR | ADMIN |
|---|---|---|---|
| Register / login | ✔ | seeded | seeded |
| Create accounts, deposit, withdraw, transfer (own accounts) | ✔ | ✘ | ✘ |
| See own accounts, history, ledger, statements | ✔ | all (read-only) | all |
| Full ledger, audit log, integrity report | ✘ | ✔ | ✔ |
| Freeze/unfreeze accounts, list users, change roles | ✘ | ✘ | ✔ |

Public registration only creates ACCOUNT_HOLDERs. An admin promotes users (Users page).

## 9. API reference (all under `/api`, JSON, `Authorization: Bearer <token>` except auth)
Money amounts are strings like `"125.50"`. Errors look like `{status, code, message, fieldErrors}`.

| Method & path | Role | Notes |
|---|---|---|
| `POST /auth/register` `{fullName,email,password}` | public | 201 → `{token,user}` |
| `POST /auth/login` `{email,password}` | public | 200 → `{token,user}`; 401 on bad credentials |
| `GET /me` | any | current user |
| `GET /accounts` · `POST /accounts {name}` | any · holder | holders: own accounts only |
| `GET /accounts/{id}` | owner/staff | 403 if not yours |
| `PATCH /accounts/{id}/status {status: ACTIVE|FROZEN}` | admin | |
| `POST /accounts/{id}/deposit` `{amount,description}` | holder (owner) | header `Idempotency-Key` required |
| `POST /accounts/{id}/withdraw` `{amount,description}` | holder (owner) | 422 `INSUFFICIENT_FUNDS` |
| `POST /transfers` `{fromAccountId,toAccountNumber,amount,description}` | holder (owner of source) | header `Idempotency-Key` required |
| `GET /transactions` | any | holders: own; staff: latest 100 |
| `GET /accounts/{id}/transactions` · `/ledger` · `/statement?from=YYYY-MM-DD&to=YYYY-MM-DD` | owner/staff | |
| `GET /ledger` · `GET /audit-logs` · `GET /reports/integrity` | admin, auditor | read-only |
| `GET /admin/users` · `PATCH /admin/users/{id}/role {role}` | admin | |

Status codes: 201 created · 200 replayed duplicate · 400 validation · 401 not signed in · 403 not allowed · 404 not found · 409 key reused with different payload / email taken · 422 business rule (funds, frozen).

## 10. Demo script (10 minutes)
1. Register **Alice** and **Bob** (two browsers or a private window). Each creates an account.
2. Alice deposits ₹1000 → show the ledger tab: one DEBIT on `EXTERNAL-CASH`, one CREDIT on her account.
3. Alice transfers ₹250 to Bob's account number. Show both balances and both ledgers.
4. Alice tries to withdraw ₹5000 → rejected, balance and ledger unchanged.
5. **Duplicate protection:** in PowerShell, send the same request twice:
   ```powershell
   $t = "<alice token>"; $k = [guid]::NewGuid().ToString()
   1..2 | % { Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/accounts/<id>/withdraw `
     -Headers @{Authorization="Bearer $t"; "Idempotency-Key"=$k} -ContentType "application/json" -Body '{"amount":"10.00"}' }
   ```
   Both return the same transaction id; the balance dropped only once.
6. **Concurrency:** run `mvn test -Dtest=LedgerLineIntegrationTest#concurrentWithdrawalsCannotOverspend` — 10 simultaneous ₹30 withdrawals on ₹100: exactly 3 succeed, balance ₹10.
7. Log in as `auditor@ledgerline.local`: Overview shows debits = credits and money conserved; try a write → 403.
8. In `psql`, try `UPDATE ledger_entries SET amount = 1;` → the database refuses (append-only trigger).
9. Log in as admin: freeze Bob's account, change a user's role, read the audit log.

## 11. Known limitations (be upfront about these in the evaluation)
* Single currency (INR); no FX. Amounts have 2 decimals.
* All deposits/withdrawals lock the single `EXTERNAL-CASH` row, so those two operations are serialised globally. Correct and simple; at large scale you would shard this account.
* Lists are capped (100 transactions / 200 ledger rows); no pagination UI.
* No refresh tokens, password reset, email verification, or rate limiting. JWT is kept in `sessionStorage`.
* Frozen accounts block new debits/credits; there is no account-closing flow.
* Reversals are not implemented — the ledger is append-only, so a correction would be a new, opposite transaction.
* Frontend UI has only a small unit test (money helpers); no browser end-to-end tests.
