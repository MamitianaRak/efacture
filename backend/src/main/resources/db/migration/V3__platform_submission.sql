-- Submission of an issued invoice to the registered platform (plateforme agréée) and its lifecycle.
CREATE TABLE platform_submission (
    id            BIGSERIAL PRIMARY KEY,
    invoice_id    BIGINT       NOT NULL REFERENCES invoice (id),
    external_id   VARCHAR(64)  NOT NULL UNIQUE,
    status        VARCHAR(30)  NOT NULL,
    status_reason VARCHAR(500),
    submitted_at  TIMESTAMPTZ  NOT NULL,
    updated_at    TIMESTAMPTZ  NOT NULL,
    version       BIGINT       NOT NULL DEFAULT 0
);

-- At most one live submission per invoice: a new one is only possible after a technical rejection.
-- Enforced by the database, so two concurrent clicks cannot both submit.
CREATE UNIQUE INDEX platform_submission_one_live_per_invoice
    ON platform_submission (invoice_id) WHERE status <> 'REJETEE';

CREATE TABLE platform_submission_event (
    id            BIGSERIAL PRIMARY KEY,
    submission_id BIGINT       NOT NULL REFERENCES platform_submission (id) ON DELETE CASCADE,
    status        VARCHAR(30)  NOT NULL,
    reason        VARCHAR(500),
    source        VARCHAR(20)  NOT NULL,
    occurred_at   TIMESTAMPTZ  NOT NULL
);

CREATE INDEX platform_submission_event_submission_idx ON platform_submission_event (submission_id);
