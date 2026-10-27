-- How the supplier expects to be paid: only a transfer needs the supplier's IBAN.
ALTER TABLE supplier_invoice ADD COLUMN payment_means VARCHAR(20) NOT NULL DEFAULT 'UNKNOWN';

-- Text layer of the PDF, to check that identifiers read by a model really appear in the document.
ALTER TABLE supplier_invoice ADD COLUMN document_text TEXT;

-- Last manual correction: values entered by a person are no longer "read by the model".
ALTER TABLE supplier_invoice ADD COLUMN corrected_by VARCHAR(200);
ALTER TABLE supplier_invoice ADD COLUMN corrected_at TIMESTAMPTZ;
