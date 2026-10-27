package com.mamitiana.efacture.supplier;

/** Gives test code outside this package access to the reader's prompt and document preparation. */
public final class AiInvoiceReaderAccess {

    private AiInvoiceReaderAccess() {
    }

    public static String instructions() {
        return AiInvoiceReader.INSTRUCTIONS;
    }

    public static AiInvoiceReader.Document prepare(byte[] content, String contentType) {
        return AiInvoiceReader.prepare(content, contentType);
    }
}
