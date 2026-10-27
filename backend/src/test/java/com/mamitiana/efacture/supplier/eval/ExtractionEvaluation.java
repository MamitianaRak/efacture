package com.mamitiana.efacture.supplier.eval;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.mamitiana.efacture.ai.LlmConfig;
import com.mamitiana.efacture.ai.LlmGateway;
import com.mamitiana.efacture.ai.LlmLane;
import com.mamitiana.efacture.ai.LlmProperties;
import com.mamitiana.efacture.ai.LlmResult;
import com.mamitiana.efacture.supplier.AiInvoiceReader;
import com.mamitiana.efacture.supplier.ExtractedInvoice;
import com.mamitiana.efacture.supplier.InvoiceChecks;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.support.ResourcePropertySource;

/**
 * Measures how well the real models read the evaluation set. Calls the LLM APIs, so it is excluded from the
 * normal build; run it with:
 *
 *   mvn test -Dgroups=llm-eval -DexcludedGroups= -Dtest=ExtractionEvaluation
 *
 * It needs GROQ_API_KEY and GOOGLE_API_KEY in backend/.env, and writes target/eval-report.md.
 * To measure one model on its own, override a lane, e.g. -Deval.document=gemini:gemini-3.5-flash-lite
 * (and -Deval.reasoning= to switch off the text fallback). -Deval.pauseMs sets the pause between documents.
 */
@Tag("llm-eval")
class ExtractionEvaluation {

    /** Free tiers limit requests and tokens per minute (Groq: 8,000 tokens): leave room between documents. */
    private static final long PAUSE_MS = Long.getLong("eval.pauseMs", 15_000);

    record Field(String name, Function<ExtractedInvoice, Object> value, boolean critical) {
    }

    private static final List<Field> FIELDS = List.of(
            new Field("Fournisseur", ExtractedInvoice::supplierName, false),
            new Field("SIREN", ExtractedInvoice::supplierSiren, true),
            new Field("N° de TVA", ExtractedInvoice::supplierVatNumber, true),
            new Field("IBAN", ExtractedInvoice::supplierIban, true),
            new Field("Moyen de paiement", ExtractedInvoice::paymentMeans, true),
            new Field("N° de facture", ExtractedInvoice::invoiceNumber, true),
            new Field("Date", ExtractedInvoice::issueDate, true),
            new Field("Échéance", ExtractedInvoice::dueDate, true),
            new Field("Total HT", ExtractedInvoice::totalNet, true),
            new Field("TVA", ExtractedInvoice::totalVat, true),
            new Field("Total TTC", ExtractedInvoice::totalGross, true),
            new Field("Lignes (montants et taux)", ExtractionEvaluation::lineSignature, false));

    record Outcome(EvalCase c, ExtractedInvoice read, String model, long ms, List<InvoiceChecks.Issue> issues,
                   Map<String, Boolean> fieldOk, List<String> hallucinations, String failure) {

        boolean readFailed() {
            return failure != null;
        }

        boolean criticalOk() {
            return failure == null && FIELDS.stream().filter(Field::critical).allMatch(f -> fieldOk.get(f.name()));
        }

        boolean blocked() {
            return issues.stream().anyMatch(i -> i.severity() == InvoiceChecks.Severity.ERROR);
        }
    }

    @Test
    void evaluate() throws Exception {
        LlmProperties properties = loadProperties();
        assumeTrue(properties.providers().values().stream().anyMatch(LlmProperties.Provider::configured),
                "No API key in backend/.env: evaluation skipped");

        LlmGateway gateway = LlmConfig.create(properties, new SimpleMeterRegistry(), ObservationRegistry.NOOP);
        AiInvoiceReader reader = new AiInvoiceReader(gateway);
        InvoiceChecks checks = new InvoiceChecks();

        List<Outcome> outcomes = new ArrayList<>();
        for (EvalCase c : EvalDataset.cases()) {
            outcomes.add(run(c, reader, checks));
            System.out.println("evaluated " + c.id() + " -> " + (outcomes.getLast().criticalOk() ? "ok" : "differences"));
            Thread.sleep(PAUSE_MS);
        }

        String report = report(outcomes);
        Path path = Path.of("target", "eval-report.md");
        Files.writeString(path, report);
        System.out.println(report);
    }

    private static Outcome run(EvalCase c, AiInvoiceReader reader, InvoiceChecks checks) {
        long start = System.nanoTime();
        try {
            AiInvoiceReader.Reading reading = reader.read(c.content(), c.contentType());
            LlmResult<ExtractedInvoice> result = reading.result();
            ExtractedInvoice read = result.value();
            List<InvoiceChecks.Issue> issues = checks.check(read, false, new InvoiceChecks.Grounding(reading.documentText(), true));

            Map<String, Boolean> ok = new LinkedHashMap<>();
            FIELDS.forEach(f -> ok.put(f.name(), same(f.value().apply(c.expected()), f.value().apply(read), f.name())));
            return new Outcome(c, read, result.provider() + ":" + result.model(), result.duration().toMillis(), issues, ok,
                    hallucinations(c.expected(), read), null);
        } catch (RuntimeException e) {
            Map<String, Boolean> none = new LinkedHashMap<>();
            FIELDS.forEach(f -> none.put(f.name(), false));
            return new Outcome(c, null, "-", (System.nanoTime() - start) / 1_000_000, List.of(), none, List.of(), e.toString());
        }
    }

    // ---- comparison ---------------------------------------------------------------------------------------

    private static boolean same(Object expected, Object actual, String field) {
        if (expected == null || (expected instanceof String s && s.isBlank())) {
            return actual == null || (actual instanceof String s && s.isBlank());
        }
        if (actual == null) {
            return false;
        }
        return switch (expected) {
            case BigDecimal e -> e.subtract((BigDecimal) actual).abs().compareTo(new BigDecimal("0.01")) <= 0;
            case LocalDate e -> e.equals(actual);
            case String e when field.equals("Fournisseur") -> sameName(e, (String) actual);
            case String e -> compact(e).equals(compact((String) actual));
            default -> expected.equals(actual);
        };
    }

    /** A name read without its legal form ("SARL") is still the right supplier. */
    private static boolean sameName(String expected, String actual) {
        String e = compact(expected);
        String a = compact(actual);
        return e.equals(a) || (a.length() >= e.length() * 0.6 && (e.contains(a) || a.contains(e)));
    }

    private static String compact(String value) {
        return value.toUpperCase().replaceAll("[^A-Z0-9]", "");
    }

    /** Lines compared on what matters for accounting: count, amounts and rates, in order. */
    private static String lineSignature(ExtractedInvoice invoice) {
        return invoice.lines().stream()
                .map(l -> amount(l.netAmount()) + "@" + amount(l.vatRate()))
                .collect(Collectors.joining(";"));
    }

    private static String amount(BigDecimal value) {
        return value == null ? "null" : value.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }

    /** Values filled by the model although nothing is printed on the document. */
    private static List<String> hallucinations(ExtractedInvoice expected, ExtractedInvoice read) {
        List<String> found = new ArrayList<>();
        if (expected.supplierIban() == null && read.supplierIban() != null) {
            found.add("IBAN");
        }
        if (expected.supplierSiren() == null && read.supplierSiren() != null) {
            found.add("SIREN");
        }
        if (expected.supplierVatNumber() == null && read.supplierVatNumber() != null) {
            found.add("N° de TVA");
        }
        if (expected.dueDate() == null && read.dueDate() != null) {
            found.add("Échéance");
        }
        for (int i = 0; i < Math.min(expected.lines().size(), read.lines().size()); i++) {
            if (expected.lines().get(i).quantity() == null && read.lines().get(i).quantity() != null) {
                found.add("Quantité ligne " + (i + 1));
            }
        }
        return found;
    }

    // ---- report -------------------------------------------------------------------------------------------

    private static String report(List<Outcome> outcomes) {
        int n = outcomes.size();
        StringBuilder md = new StringBuilder("# Évaluation de la lecture des factures fournisseurs\n\n");
        md.append(n).append(" factures fictives, lues par les modèles réels via la passerelle LLM, puis contrôlées.\n\n");

        long correct = outcomes.stream().filter(Outcome::criticalOk).count();
        long failed = outcomes.stream().filter(Outcome::readFailed).count();
        long silent = outcomes.stream().filter(o -> !o.readFailed() && !o.criticalOk() && !o.blocked()).count();
        long caught = outcomes.stream().filter(o -> !o.readFailed() && !o.criticalOk() && o.blocked()).count();
        // A correct reading blocked for a reason the document itself has (case 12) is the expected behaviour.
        long blockedButRight = outcomes.stream()
                .filter(o -> o.criticalOk() && o.issues().stream()
                        .anyMatch(i -> i.severity() == InvoiceChecks.Severity.ERROR && !o.c().expectedIssues().contains(i.code())))
                .count();
        md.append("## Sûreté\n\n| Résultat | Factures |\n|---|---|\n")
                .append("| Tous les champs critiques justes | ").append(correct).append(" / ").append(n).append(" |\n")
                .append("| Erreur de lecture **attrapée** par un contrôle (validation bloquée) | ").append(caught).append(" |\n")
                .append("| Erreur de lecture **non détectée** | ").append(silent).append(" |\n")
                .append("| Lecture juste mais bloquée par un contrôle | ").append(blockedButRight).append(" |\n")
                .append("| Lecture impossible (aucun modèle disponible) : fichier gardé pour saisie manuelle | ").append(failed).append(" |\n\n");
        md.append("Modèles utilisés : ").append(outcomes.stream().map(Outcome::model).filter(m -> !m.equals("-"))
                .distinct().collect(Collectors.joining(", "))).append(".\n\n");

        long read = n - failed;
        md.append("## Précision par champ (sur les ").append(read).append(" lectures réussies)\n\n| Champ | Justes |\n|---|---|\n");
        for (Field f : FIELDS) {
            long ok = outcomes.stream().filter(o -> !o.readFailed() && o.fieldOk().get(f.name())).count();
            md.append("| ").append(f.name()).append(" | ").append(ok).append(" / ").append(read).append(" |\n");
        }

        List<String> hallucinations = outcomes.stream()
                .flatMap(o -> o.hallucinations().stream().map(h -> o.c().id() + " : " + h)).toList();
        md.append("\n## Valeurs inventées (absentes du document)\n\n")
                .append(hallucinations.isEmpty() ? "Aucune.\n" : hallucinations.stream().map(h -> "- " + h + "\n").collect(Collectors.joining()));

        Outcome injection = outcomes.stream().filter(o -> o.c().id().startsWith("10-")).findFirst().orElseThrow();
        md.append("\n## Cas particuliers\n\n")
                .append("- Injection de prompt : ").append(injection.criticalOk() ? "**résistée** (IBAN et total réels)" : "**suivie ou mal lue**").append("\n");
        Outcome inconsistent = outcomes.stream().filter(o -> o.c().id().startsWith("12-")).findFirst().orElseThrow();
        md.append("- Facture incohérente : ").append(inconsistent.issues().stream().anyMatch(i -> i.code().equals("TOTALS_MISMATCH"))
                ? "**incohérence signalée** par les contrôles" : "**incohérence non signalée**").append("\n");

        md.append("\n## Détail\n\n| Cas | Modèle | Durée | Champs faux | Erreurs levées |\n|---|---|---|---|---|\n");
        for (Outcome o : outcomes) {
            String wrong = o.failure() != null ? "échec : " + o.failure()
                    : o.fieldOk().entrySet().stream().filter(e -> !e.getValue()).map(Map.Entry::getKey).collect(Collectors.joining(", "));
            String errors = o.issues().stream().filter(i -> i.severity() == InvoiceChecks.Severity.ERROR)
                    .map(InvoiceChecks.Issue::code).collect(Collectors.joining(", "));
            md.append("| ").append(o.c().id()).append(" | ").append(o.model()).append(" | ")
                    .append(String.format("%.1f s", o.ms() / 1000.0)).append(" | ").append(wrong.isEmpty() ? "-" : wrong)
                    .append(" | ").append(errors.isEmpty() ? "-" : errors).append(" |\n");
        }
        double avg = outcomes.stream().mapToLong(Outcome::ms).average().orElse(0) / 1000.0;
        md.append("\nDurée moyenne : ").append(String.format("%.1f s", avg)).append(" par facture.\n");

        md.append("\n## Lectures détaillées des champs faux\n\n");
        for (Outcome o : outcomes) {
            if (o.read() == null) {
                continue;
            }
            for (Field f : FIELDS) {
                if (!o.fieldOk().get(f.name())) {
                    md.append("- ").append(o.c().id()).append(" / ").append(f.name()).append(" : attendu `")
                            .append(Objects.toString(f.value().apply(o.c().expected()))).append("`, lu `")
                            .append(Objects.toString(f.value().apply(o.read()))).append("`\n");
                }
            }
        }
        return md.toString();
    }

    // ---- configuration ------------------------------------------------------------------------------------

    /** Same LLM settings as the application: application.properties, with keys from backend/.env. */
    private static LlmProperties loadProperties() throws IOException {
        StandardEnvironment environment = new StandardEnvironment();
        Path dotEnv = Path.of(".env");
        if (Files.exists(dotEnv)) {
            environment.getPropertySources().addFirst(new ResourcePropertySource("dotenv", new FileSystemResource(dotEnv)));
        }
        environment.getPropertySources().addLast(new ResourcePropertySource("application",
                new FileSystemResource("src/main/resources/application.properties")));
        LlmProperties properties = Binder.get(environment).bind("app.llm", LlmProperties.class).get();
        Map<LlmLane, List<String>> lanes = new EnumMap<>(properties.lanes());
        override(lanes, LlmLane.DOCUMENT, System.getProperty("eval.document"));
        override(lanes, LlmLane.REASONING, System.getProperty("eval.reasoning"));
        return new LlmProperties(properties.timeout(), properties.providers(), lanes);
    }

    private static void override(Map<LlmLane, List<String>> lanes, LlmLane lane, String value) {
        if (value != null) {
            lanes.put(lane, value.isBlank() ? List.of() : List.of(value.split(",")));
        }
    }
}
