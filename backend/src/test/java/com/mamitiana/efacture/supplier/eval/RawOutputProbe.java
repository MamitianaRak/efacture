package com.mamitiana.efacture.supplier.eval;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.mamitiana.efacture.ai.LlmCandidate;
import com.mamitiana.efacture.ai.LlmGateway;
import com.mamitiana.efacture.ai.LlmLane;
import com.mamitiana.efacture.supplier.ExtractedInvoice;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;

/** Debugging aid: prints a model's raw answer for one evaluation case, before any JSON parsing. */
@Tag("llm-eval")
class RawOutputProbe {

    @Test
    void printRawAnswer() throws Exception {
        String key = Files.readAllLines(Path.of(".env")).stream()
                .filter(l -> l.startsWith("GOOGLE_API_KEY=")).map(l -> l.substring(15).replace("\"", "").strip())
                .findFirst().orElse("");
        assumeTrue(!key.isBlank());
        String model = System.getProperty("probe.model", "gemini-3.5-flash-lite");

        ChatClient client = ChatClient.create(OpenAiChatModel.builder().options(OpenAiChatOptions.builder()
                .baseUrl("https://generativelanguage.googleapis.com/v1beta/openai/").apiKey(key).model(model)
                .maxRetries(0).build()).build());
        EvalCase c = EvalDataset.cases().getFirst();
        var document = com.mamitiana.efacture.supplier.AiInvoiceReaderAccess.prepare(c.content(), c.contentType());

        var response = client.prompt()
                .system(com.mamitiana.efacture.supplier.AiInvoiceReaderAccess.instructions())
                .user(u -> u.text("Extrais les données de cette facture. Texte extrait du PDF :\n---\n" + document.text()
                        + "\n---\n" + new BeanOutputConverter<>(ExtractedInvoice.class).getFormat())
                        .media(document.images().toArray(org.springframework.ai.content.Media[]::new)))
                .call()
                .chatResponse();
        System.out.println("FINISH REASON: " + response.getResult().getMetadata().getFinishReason());
        System.out.println("USAGE: " + response.getMetadata().getUsage());
        System.out.println("RAW ANSWER:\n" + response.getResult().getOutput().getText());
    }
}
