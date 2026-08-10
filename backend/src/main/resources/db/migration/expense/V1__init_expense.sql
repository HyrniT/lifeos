-- =====================================================================
--  expense-service schema
--  Money is NUMERIC(19,4) everywhere — never floating point.
-- =====================================================================

CREATE TABLE account (
    id                  UUID          PRIMARY KEY,
    user_id             UUID          NOT NULL,
    name                VARCHAR(80)   NOT NULL,
    type                VARCHAR(24)   NOT NULL DEFAULT 'CASH',
    currency            VARCHAR(3)    NOT NULL DEFAULT 'USD',
    opening_balance     NUMERIC(19,4) NOT NULL DEFAULT 0,
    current_balance     NUMERIC(19,4) NOT NULL DEFAULT 0,
    credit_limit        NUMERIC(19,4),
    icon                VARCHAR(48)            DEFAULT 'wallet',
    color               VARCHAR(16)            DEFAULT '#111111',
    exclude_from_totals BOOLEAN       NOT NULL DEFAULT FALSE,
    sort_order          INT           NOT NULL DEFAULT 0,
    archived            BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_account_user ON account (user_id, archived, sort_order);

CREATE TABLE category (
    id             UUID          PRIMARY KEY,
    user_id        UUID          NOT NULL,
    name           VARCHAR(80)   NOT NULL,
    kind           VARCHAR(16)   NOT NULL DEFAULT 'EXPENSE',
    icon           VARCHAR(48)            DEFAULT 'tag',
    color          VARCHAR(16)            DEFAULT '#111111',
    parent_id      UUID,
    monthly_budget NUMERIC(19,4),
    sort_order     INT           NOT NULL DEFAULT 0,
    archived       BOOLEAN       NOT NULL DEFAULT FALSE,
    is_system      BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_category_user ON category (user_id, kind, sort_order);

CREATE TABLE transaction (
    id                UUID          PRIMARY KEY,
    user_id           UUID          NOT NULL,
    account_id        UUID          NOT NULL REFERENCES account (id),
    to_account_id     UUID          REFERENCES account (id),
    category_id       UUID          REFERENCES category (id),
    amount            NUMERIC(19,4) NOT NULL CHECK (amount > 0),
    currency          VARCHAR(3)    NOT NULL DEFAULT 'USD',
    type              VARCHAR(16)   NOT NULL,
    occurred_on       DATE          NOT NULL,
    note              VARCHAR(500),
    merchant          VARCHAR(120),
    recurring_rule_id UUID,
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_tx_user_date ON transaction (user_id, occurred_on DESC);
CREATE INDEX idx_tx_account   ON transaction (account_id, occurred_on DESC);
CREATE INDEX idx_tx_category  ON transaction (category_id, occurred_on DESC);
-- Free-text search over notes and merchant names without a separate search engine.
-- Guarded because a managed Postgres may refuse to install pg_trgm: the query is a
-- plain ILIKE either way, so a missing extension costs a scan, not a feature.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'pg_trgm') THEN
        EXECUTE 'CREATE INDEX idx_tx_note_trgm     ON transaction USING GIN (note gin_trgm_ops)';
        EXECUTE 'CREATE INDEX idx_tx_merchant_trgm ON transaction USING GIN (merchant gin_trgm_ops)';
    ELSE
        RAISE NOTICE 'pg_trgm not installed - skipping trigram indexes on transaction';
    END IF;
END
$$;

CREATE TABLE transaction_tags (
    transaction_id UUID        NOT NULL REFERENCES transaction (id) ON DELETE CASCADE,
    tag            VARCHAR(40) NOT NULL
);
CREATE INDEX idx_tx_tags ON transaction_tags (transaction_id);
CREATE INDEX idx_tx_tag_name ON transaction_tags (tag);

CREATE TABLE budget (
    id              UUID          PRIMARY KEY,
    user_id         UUID          NOT NULL,
    category_id     UUID          REFERENCES category (id),
    name            VARCHAR(80)   NOT NULL,
    amount          NUMERIC(19,4) NOT NULL CHECK (amount > 0),
    period          VARCHAR(16)   NOT NULL DEFAULT 'MONTHLY',
    start_date      DATE          NOT NULL,
    rollover        BOOLEAN       NOT NULL DEFAULT FALSE,
    alert_threshold INT           NOT NULL DEFAULT 80,
    active          BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_budget_user ON budget (user_id, active);

CREATE TABLE recurring_rule (
    id          UUID          PRIMARY KEY,
    user_id     UUID          NOT NULL,
    name        VARCHAR(120)  NOT NULL,
    account_id  UUID          NOT NULL REFERENCES account (id),
    category_id UUID          REFERENCES category (id),
    amount      NUMERIC(19,4) NOT NULL CHECK (amount > 0),
    currency    VARCHAR(3)    NOT NULL DEFAULT 'USD',
    type        VARCHAR(16)   NOT NULL,
    cadence     VARCHAR(16)   NOT NULL DEFAULT 'MONTHLY',
    next_run_on DATE          NOT NULL,
    end_on      DATE,
    last_run_on DATE,
    note        VARCHAR(500),
    active      BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_recurring_due ON recurring_rule (active, next_run_on);
