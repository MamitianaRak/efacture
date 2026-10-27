-- Invoices received from suppliers: the original file, what was read from it, and how.
CREATE TABLE supplier_invoice (
    id                  BIGSERIAL PRIMARY KEY,
    status              VARCHAR(20)   NOT NULL,
    source              VARCHAR(20)   NOT NULL,
    file_name           VARCHAR(255)  NOT NULL,
    content_type        VARCHAR(100)  NOT NULL,
    file_content        BYTEA         NOT NULL,
    supplier_name       VARCHAR(200),
    supplier_siren      VARCHAR(9),
    supplier_vat_number VARCHAR(20),
    supplier_iban       VARCHAR(34),
    invoice_number      VARCHAR(50),
    issue_date          DATE,
    due_date            DATE,
    currency            VARCHAR(3),
    total_net           NUMERIC(14,2),
    total_vat           NUMERIC(14,2),
    total_gross         NUMERIC(14,2),
    -- Which model read the file, for the audit trail (null when the Factur-X XML was read directly).
    extraction_provider VARCHAR(30),
    extraction_model    VARCHAR(100),
    extraction_ms       BIGINT,
    received_by         VARCHAR(200)  NOT NULL,
    received_at         TIMESTAMPTZ   NOT NULL,
    reviewed_by         VARCHAR(200),
    reviewed_at         TIMESTAMPTZ,
    rejection_reason    VARCHAR(500),
    version             BIGINT        NOT NULL DEFAULT 0
);

CREATE INDEX supplier_invoice_supplier_number_idx ON supplier_invoice (supplier_siren, invoice_number);

CREATE TABLE supplier_invoice_line (
    id                  BIGSERIAL PRIMARY KEY,
    supplier_invoice_id BIGINT        NOT NULL REFERENCES supplier_invoice (id) ON DELETE CASCADE,
    position            INT           NOT NULL,
    description         VARCHAR(500),
    quantity            NUMERIC(12,3),
    unit_price          NUMERIC(14,2),
    vat_rate            NUMERIC(4,2),
    net_amount          NUMERIC(14,2)
);

-- Problems found by the deterministic checks, recomputed after each correction.
CREATE TABLE supplier_invoice_issue (
    id                  BIGSERIAL PRIMARY KEY,
    supplier_invoice_id BIGINT        NOT NULL REFERENCES supplier_invoice (id) ON DELETE CASCADE,
    code                VARCHAR(50)   NOT NULL,
    severity            VARCHAR(10)   NOT NULL,
    message             VARCHAR(500)  NOT NULL
);
