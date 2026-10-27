package com.mamitiana.efacture.supplier.eval;

import com.mamitiana.efacture.supplier.ExtractedInvoice;
import java.util.Set;

/**
 * One invoice of the evaluation set: the file, and what a careful person would read on it. Values that are not
 * printed on the document are null in {@code expected}; a model that fills them is hallucinating.
 *
 * @param expectedIssues issue codes the checks must raise (the document itself is inconsistent), else empty
 */
public record EvalCase(String id, String description, String fileName, String contentType, byte[] content,
                       ExtractedInvoice expected, Set<String> expectedIssues) {

    public boolean hasTextLayer() {
        return "application/pdf".equals(contentType);
    }
}
