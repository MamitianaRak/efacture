package com.mamitiana.efacture.invoice;

public enum InvoiceStatus {
    /** Editable, no number yet. */
    DRAFT,
    /** Numbered and frozen: corrections go through a credit note. */
    ISSUED
}
