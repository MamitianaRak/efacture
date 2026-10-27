-- Seller: one row, the company that issues the invoices.
CREATE TABLE company (
    id              BIGINT PRIMARY KEY,
    legal_name      VARCHAR(200) NOT NULL,
    siren           VARCHAR(9)      NOT NULL,
    vat_number      VARCHAR(13)  NOT NULL,
    street          VARCHAR(200) NOT NULL,
    postal_code     VARCHAR(10)  NOT NULL,
    city            VARCHAR(100) NOT NULL,
    country_code    VARCHAR(2)      NOT NULL DEFAULT 'FR',
    email           VARCHAR(200),
    iban            VARCHAR(34),
    payment_terms   VARCHAR(500) NOT NULL,
    CONSTRAINT company_single_row CHECK (id = 1)
);

CREATE TABLE customer (
    id              BIGSERIAL PRIMARY KEY,
    legal_name      VARCHAR(200) NOT NULL,
    siren           VARCHAR(9),
    vat_number      VARCHAR(13),
    street          VARCHAR(200) NOT NULL,
    postal_code     VARCHAR(10)  NOT NULL,
    city            VARCHAR(100) NOT NULL,
    country_code    VARCHAR(2)      NOT NULL DEFAULT 'FR',
    email           VARCHAR(200),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE invoice (
    id                  BIGSERIAL PRIMARY KEY,
    type                VARCHAR(20)   NOT NULL,
    status              VARCHAR(20)   NOT NULL,
    number              VARCHAR(20)   UNIQUE,
    customer_id         BIGINT        NOT NULL REFERENCES customer (id),
    original_invoice_id BIGINT        REFERENCES invoice (id),
    issue_date          DATE,
    due_date            DATE,
    currency            VARCHAR(3)       NOT NULL DEFAULT 'EUR',
    total_net           NUMERIC(14,2) NOT NULL DEFAULT 0,
    total_vat           NUMERIC(14,2) NOT NULL DEFAULT 0,
    total_gross         NUMERIC(14,2) NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    issued_at           TIMESTAMPTZ,
    version             BIGINT        NOT NULL DEFAULT 0,
    -- An issued document always carries its number, a draft never does.
    CONSTRAINT invoice_number_iff_issued CHECK ((status = 'DRAFT') = (number IS NULL)),
    CONSTRAINT credit_note_has_origin CHECK ((type = 'CREDIT_NOTE') = (original_invoice_id IS NOT NULL))
);

CREATE INDEX invoice_customer_idx ON invoice (customer_id);
CREATE INDEX invoice_status_idx ON invoice (status);

CREATE TABLE invoice_line (
    id          BIGSERIAL PRIMARY KEY,
    invoice_id  BIGINT        NOT NULL REFERENCES invoice (id) ON DELETE CASCADE,
    position    INT           NOT NULL,
    description VARCHAR(500)  NOT NULL,
    quantity    NUMERIC(12,3) NOT NULL CHECK (quantity > 0),
    unit_price  NUMERIC(14,2) NOT NULL CHECK (unit_price >= 0),
    vat_rate    NUMERIC(4,2)  NOT NULL,
    UNIQUE (invoice_id, position)
);

-- One gapless counter per document type and year, incremented inside the issuing transaction.
CREATE TABLE invoice_sequence (
    type       VARCHAR(20) NOT NULL,
    year       INT         NOT NULL,
    last_value BIGINT      NOT NULL,
    PRIMARY KEY (type, year)
);
