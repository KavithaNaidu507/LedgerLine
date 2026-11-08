-- LedgerLine schema. Convention: account.balance = SUM(CREDIT entries) - SUM(DEBIT entries).
-- A special EXTERNAL account represents "the outside world" (cash in / cash out), so every
-- transaction, including deposits and withdrawals, has exactly one DEBIT and one CREDIT.

CREATE TABLE users (
  id            BIGSERIAL PRIMARY KEY,
  email         VARCHAR(150) NOT NULL UNIQUE,
  password_hash VARCHAR(100) NOT NULL,
  full_name     VARCHAR(100) NOT NULL,
  role          VARCHAR(20)  NOT NULL CHECK (role IN ('ADMIN','ACCOUNT_HOLDER','AUDITOR')),
  created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE accounts (
  id             BIGSERIAL PRIMARY KEY,
  account_number VARCHAR(20)  NOT NULL UNIQUE,
  owner_id       BIGINT REFERENCES users(id),
  name           VARCHAR(60)  NOT NULL,
  balance        NUMERIC(19,2) NOT NULL DEFAULT 0,
  status         VARCHAR(10)  NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','FROZEN')),
  is_external    BOOLEAN      NOT NULL DEFAULT FALSE,
  created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
  CONSTRAINT chk_no_overdraft CHECK (is_external OR balance >= 0),
  CONSTRAINT chk_owner CHECK ((is_external AND owner_id IS NULL) OR (NOT is_external AND owner_id IS NOT NULL))
);
CREATE INDEX idx_accounts_owner ON accounts(owner_id);

CREATE TABLE transactions (
  id               BIGSERIAL PRIMARY KEY,
  reference        UUID          NOT NULL UNIQUE,
  type             VARCHAR(12)   NOT NULL CHECK (type IN ('DEPOSIT','WITHDRAWAL','TRANSFER')),
  amount           NUMERIC(19,2) NOT NULL CHECK (amount > 0),
  status           VARCHAR(12)   NOT NULL DEFAULT 'COMPLETED',
  from_account_id  BIGINT        NOT NULL REFERENCES accounts(id),
  to_account_id    BIGINT        NOT NULL REFERENCES accounts(id),
  description      VARCHAR(200),
  initiated_by     BIGINT        NOT NULL REFERENCES users(id),
  idempotency_key  VARCHAR(100)  NOT NULL,
  request_hash     VARCHAR(64)   NOT NULL,
  created_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
  CONSTRAINT chk_different_accounts CHECK (from_account_id <> to_account_id),
  -- THE duplicate-payment guard: the same user can never reuse an idempotency key.
  CONSTRAINT uq_idempotency UNIQUE (initiated_by, idempotency_key)
);
CREATE INDEX idx_tx_from ON transactions(from_account_id);
CREATE INDEX idx_tx_to ON transactions(to_account_id);

CREATE TABLE ledger_entries (
  id             BIGSERIAL PRIMARY KEY,
  transaction_id BIGINT        NOT NULL REFERENCES transactions(id),
  account_id     BIGINT        NOT NULL REFERENCES accounts(id),
  direction      VARCHAR(6)    NOT NULL CHECK (direction IN ('DEBIT','CREDIT')),
  amount         NUMERIC(19,2) NOT NULL CHECK (amount > 0),
  balance_after  NUMERIC(19,2) NOT NULL,
  created_at     TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX idx_entries_account ON ledger_entries(account_id, id);
CREATE INDEX idx_entries_tx ON ledger_entries(transaction_id);

CREATE TABLE audit_logs (
  id          BIGSERIAL PRIMARY KEY,
  user_id     BIGINT,
  action      VARCHAR(40)  NOT NULL,
  entity_type VARCHAR(30),
  entity_id   VARCHAR(40),
  details     VARCHAR(500),
  created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Append-only: history can never be edited or deleted, not even by buggy application code.
CREATE FUNCTION ll_block_mutation() RETURNS trigger AS $$
BEGIN
  RAISE EXCEPTION '% is append-only: % is not allowed', TG_TABLE_NAME, TG_OP;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_ledger_immutable BEFORE UPDATE OR DELETE ON ledger_entries FOR EACH ROW EXECUTE FUNCTION ll_block_mutation();
CREATE TRIGGER trg_ledger_no_truncate BEFORE TRUNCATE ON ledger_entries FOR EACH STATEMENT EXECUTE FUNCTION ll_block_mutation();
CREATE TRIGGER trg_tx_immutable BEFORE UPDATE OR DELETE ON transactions FOR EACH ROW EXECUTE FUNCTION ll_block_mutation();
CREATE TRIGGER trg_audit_immutable BEFORE UPDATE OR DELETE ON audit_logs FOR EACH ROW EXECUTE FUNCTION ll_block_mutation();

-- Double-entry guard checked at COMMIT: for every transaction, total debits must equal total credits.
CREATE FUNCTION ll_check_balanced() RETURNS trigger AS $$
DECLARE d NUMERIC; c NUMERIC;
BEGIN
  SELECT COALESCE(SUM(amount) FILTER (WHERE direction = 'DEBIT'), 0),
         COALESCE(SUM(amount) FILTER (WHERE direction = 'CREDIT'), 0)
    INTO d, c FROM ledger_entries WHERE transaction_id = NEW.transaction_id;
  IF d <> c THEN
    RAISE EXCEPTION 'Unbalanced ledger for transaction %: debits % <> credits %', NEW.transaction_id, d, c;
  END IF;
  RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE CONSTRAINT TRIGGER trg_ledger_balanced AFTER INSERT ON ledger_entries
  DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION ll_check_balanced();

-- The "outside world" account used as the other side of deposits and withdrawals.
INSERT INTO accounts (account_number, name, is_external) VALUES ('EXTERNAL-CASH', 'External cash', TRUE);
