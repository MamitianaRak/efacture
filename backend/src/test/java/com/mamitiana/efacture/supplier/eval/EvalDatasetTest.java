package com.mamitiana.efacture.supplier.eval;

import static org.assertj.core.api.Assertions.assertThat;

import com.mamitiana.efacture.supplier.InvoiceChecks;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

/**
 * The evaluation set is only worth something if its expected values are right. This test, which needs no model,
 * checks every case against its own document before any score is computed.
 */
class EvalDatasetTest {

    private final InvoiceChecks checks = new InvoiceChecks();
    private final List<EvalCase> cases = EvalDataset.cases();

    private static String text(byte[] pdf) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            return new PDFTextStripper().getText(document);
        }
    }

    @Test
    void hasTwentyCasesWithUniqueIds() {
        assertThat(cases).hasSize(20);
        assertThat(cases.stream().map(EvalCase::id).collect(Collectors.toSet())).hasSize(20);
    }

    @Test
    void expectedValuesRaiseExactlyTheExpectedErrors() {
        for (EvalCase c : cases) {
            Set<String> errors = checks.check(c.expected(), false).stream()
                    .filter(i -> i.severity() == InvoiceChecks.Severity.ERROR)
                    .map(InvoiceChecks.Issue::code)
                    .collect(Collectors.toSet());
            assertThat(errors).as(c.id()).isEqualTo(c.expectedIssues());
        }
    }

    @Test
    void expectedIdentifiersAndTotalsArePrintedOnTheDocument() throws IOException {
        for (EvalCase c : cases.stream().filter(EvalCase::hasTextLayer).toList()) {
            String text = text(c.content());
            List<InvoiceChecks.Issue> grounding = checks.check(c.expected(), false, new InvoiceChecks.Grounding(text, true));
            assertThat(grounding).as(c.id()).noneMatch(i -> i.code().endsWith("_NOT_IN_DOCUMENT"));
            assertThat(text).as(c.id()).contains(EvalDataset.amount(c.expected().totalGross()));
        }
    }

    @Test
    void scansHaveNoTextLayer() {
        assertThat(cases.stream().filter(c -> !c.hasTextLayer()))
                .hasSize(2)
                .allSatisfy(c -> assertThat(c.content()).startsWith((byte) 0x89, (byte) 'P', (byte) 'N', (byte) 'G'));
    }

    /** Writes the files to target/, to look at what the model is given. */
    @Test
    void writesTheDatasetForInspection() throws IOException {
        Path folder = Files.createDirectories(Path.of("target", "eval-dataset"));
        for (EvalCase c : cases) {
            Files.write(folder.resolve(c.fileName()), c.content());
        }
        assertThat(folder.toFile().list()).hasSize(20);
    }
}
