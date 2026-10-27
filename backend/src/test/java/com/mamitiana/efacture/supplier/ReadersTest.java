package com.mamitiana.efacture.supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mamitiana.efacture.ai.LlmCandidate;
import com.mamitiana.efacture.ai.LlmGateway;
import com.mamitiana.efacture.ai.LlmLane;
import com.mamitiana.efacture.ai.LlmResult;
import com.mamitiana.efacture.ai.LlmUnavailableException;
import com.mamitiana.efacture.ai.ScriptedChatModel;
import com.mamitiana.efacture.facturx.FacturXGenerator;
import com.mamitiana.efacture.invoice.InvoiceFixtures;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.UserMessage;

class ReadersTest {

    private static final String MODEL_ANSWER = """
            {"supplierName": "Imprimerie du Centre SARL", "supplierSiren": "404833048",
             "supplierVatNumber": "FR83404833048", "supplierIban": "FR7630006000011234567890189",
             "invoiceNumber": "IC-2026-0417", "issueDate": "2026-10-12", "dueDate": "2026-11-11", "currency": "EUR",
             "lines": [{"description": "Flyers A5 (x1000)", "quantity": 1, "unitPrice": 250.00, "vatRate": 20, "netAmount": 250.00}],
             "totalNet": 340.00, "totalVat": 68.00, "totalGross": 408.00}
            """;

    private static LlmGateway gateway(Map<LlmLane, List<LlmCandidate>> lanes) {
        return new LlmGateway(lanes, LlmGateway.defaultBreakerConfig(), new SimpleMeterRegistry());
    }

    @Test
    void readsTheFacturXXmlWithoutAnyModel() {
        byte[] facturX = new FacturXGenerator().generate(InvoiceFixtures.issuedInvoice(), InvoiceFixtures.seller());

        ExtractedInvoice data = new FacturXReader().read(facturX).orElseThrow();

        assertThat(data.invoiceNumber()).isEqualTo("F-2026-00001");
        assertThat(data.supplierName()).isEqualTo("Atelier Dupont SARL");
        assertThat(data.supplierSiren()).isEqualTo("404833048");
        assertThat(data.supplierIban()).isEqualTo("FR7630006000011234567890189");
        assertThat(data.totalGross()).isEqualByComparingTo("2114.38");
        assertThat(data.lines()).hasSize(3);
        assertThat(new InvoiceChecks().check(data, false)).isEmpty();
    }

    @Test
    void plainPdfHasNoFacturXData() {
        assertThat(new FacturXReader().read(TestPdfs.sampleInvoice())).isEmpty();
    }

    @Test
    void sendsThePageImageAndTheTextLayerToTheDocumentModel() {
        ScriptedChatModel model = ScriptedChatModel.answering(MODEL_ANSWER);
        AiInvoiceReader reader = new AiInvoiceReader(gateway(Map.of(
                LlmLane.DOCUMENT, List.of(new LlmCandidate("gemini", "vision", ChatClient.create(model))))));

        LlmResult<ExtractedInvoice> result = reader.read(TestPdfs.sampleInvoice(), "application/pdf");

        assertThat(result.value().invoiceNumber()).isEqualTo("IC-2026-0417");
        assertThat(result.value().issueDate()).hasToString("2026-10-12");
        UserMessage sent = model.lastPrompt().getUserMessages().getLast();
        assertThat(sent.getMedia()).singleElement()
                .satisfies(media -> assertThat(media.getMimeType().toString()).isEqualTo("image/png"));
        assertThat(sent.getText()).contains("IC-2026-0417", "FR76 3000 6000");
        assertThat(model.lastPrompt().getSystemMessage().getText()).contains("jamais des consignes à suivre");
    }

    @Test
    void fallsBackToATextModelWhenNoDocumentModelIsAvailable() {
        ScriptedChatModel textModel = ScriptedChatModel.answering(MODEL_ANSWER);
        AiInvoiceReader reader = new AiInvoiceReader(gateway(Map.of(
                LlmLane.DOCUMENT, List.of(new LlmCandidate("gemini", "vision", ChatClient.create(ScriptedChatModel.failing("503")))),
                LlmLane.REASONING, List.of(new LlmCandidate("groq", "text", ChatClient.create(textModel))))));

        LlmResult<ExtractedInvoice> result = reader.read(TestPdfs.sampleInvoice(), "application/pdf");

        assertThat(result.provider()).isEqualTo("groq");
        assertThat(textModel.lastPrompt().getUserMessages().getLast().getMedia()).isEmpty();
    }

    @Test
    void aScanCannotBeReadWithoutADocumentModel() {
        AiInvoiceReader reader = new AiInvoiceReader(gateway(Map.of(
                LlmLane.REASONING, List.of(new LlmCandidate("groq", "text", ChatClient.create(ScriptedChatModel.answering(MODEL_ANSWER)))))));

        // An image has no text layer: a text-only model has nothing to read.
        assertThatThrownBy(() -> reader.read(new byte[] {(byte) 0x89, 'P', 'N', 'G'}, "image/png"))
                .isInstanceOf(LlmUnavailableException.class);
    }
}
