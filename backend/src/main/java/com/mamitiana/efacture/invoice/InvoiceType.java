package com.mamitiana.efacture.invoice;

public enum InvoiceType {
    INVOICE("F"),
    CREDIT_NOTE("AV");

    private final String numberPrefix;

    InvoiceType(String numberPrefix) {
        this.numberPrefix = numberPrefix;
    }

    public String numberPrefix() {
        return numberPrefix;
    }
}
