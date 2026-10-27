-- Transactional outbox: messages to the registered platform are written in the same transaction as the business
-- change, then sent by a relay. If the transaction rolls back, nothing is sent; if it commits, sending is guaranteed.
CREATE TABLE outbox_message (
    id              BIGSERIAL PRIMARY KEY,
    type            VARCHAR(40)   NOT NULL,
    aggregate_id    BIGINT        NOT NULL,
    status          VARCHAR(20)   NOT NULL,
    attempts        INT           NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ   NOT NULL,
    last_error      VARCHAR(1000),
    created_at      TIMESTAMPTZ   NOT NULL,
    sent_at         TIMESTAMPTZ
);

-- The relay only ever looks for pending messages that are due.
CREATE INDEX outbox_message_due_idx ON outbox_message (next_attempt_at) WHERE status = 'PENDING';

-- A submission now exists before the platform has answered: its platform id comes later.
ALTER TABLE platform_submission ALTER COLUMN external_id DROP NOT NULL;
ALTER TABLE platform_submission ADD COLUMN delivery_attempts INT NOT NULL DEFAULT 0;
ALTER TABLE platform_submission ADD COLUMN last_delivery_error VARCHAR(1000);

-- A submission that could not be delivered frees the invoice for a new one, like a technical rejection.
DROP INDEX platform_submission_one_live_per_invoice;
CREATE UNIQUE INDEX platform_submission_one_live_per_invoice
    ON platform_submission (invoice_id) WHERE status NOT IN ('REJETEE', 'ECHEC_ENVOI');
