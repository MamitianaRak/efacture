package com.mamitiana.efacture.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

class LlmGatewayTest {

    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();

    private static LlmCandidate candidate(String provider, ScriptedChatModel model) {
        return new LlmCandidate(provider, provider + "-model", ChatClient.create(model));
    }

    private LlmGateway gateway(LlmCandidate... candidates) {
        return new LlmGateway(Map.of(LlmLane.FAST, List.of(candidates)), LlmGateway.defaultBreakerConfig(), meters);
    }

    private static String ask(ChatClient client) {
        return client.prompt().user("question").call().content();
    }

    @Test
    void usesTheFirstCandidateWhenItAnswers() {
        LlmResult<String> result = gateway(
                candidate("groq", ScriptedChatModel.answering("from groq")),
                candidate("gemini", ScriptedChatModel.answering("from gemini")))
                .call(LlmLane.FAST, LlmGatewayTest::ask);

        assertThat(result.value()).isEqualTo("from groq");
        assertThat(result.provider()).isEqualTo("groq");
        assertThat(result.attempts()).isEqualTo(1);
    }

    @Test
    void fallsBackToTheNextProviderWhenOneFails() {
        LlmResult<String> result = gateway(
                candidate("groq", ScriptedChatModel.failing("429 Too Many Requests")),
                candidate("gemini", ScriptedChatModel.answering("from gemini")))
                .call(LlmLane.FAST, LlmGatewayTest::ask);

        assertThat(result.value()).isEqualTo("from gemini");
        assertThat(result.provider()).isEqualTo("gemini");
        assertThat(result.attempts()).isEqualTo(2);
        assertThat(meters.counter("efacture.llm.fallbacks", "lane", "FAST", "provider", "gemini").count()).isEqualTo(1);
    }

    @Test
    void reportsUnavailableWhenEveryProviderFails() {
        LlmGateway gateway = gateway(
                candidate("groq", ScriptedChatModel.failing("timeout")),
                candidate("gemini", ScriptedChatModel.failing("503")));

        assertThatThrownBy(() -> gateway.call(LlmLane.FAST, LlmGatewayTest::ask))
                .isInstanceOf(LlmUnavailableException.class)
                .hasRootCauseMessage("503");
    }

    @Test
    void laneWithoutConfiguredProviderIsUnavailable() {
        LlmGateway gateway = new LlmGateway(Map.of(), LlmGateway.defaultBreakerConfig(), meters);

        assertThatThrownBy(() -> gateway.call(LlmLane.DOCUMENT, LlmGatewayTest::ask))
                .isInstanceOf(LlmUnavailableException.class);
    }

    @Test
    void stopsCallingAProviderThatKeepsFailing() {
        ScriptedChatModel broken = ScriptedChatModel.failing("quota exhausted");
        LlmGateway gateway = new LlmGateway(
                Map.of(LlmLane.FAST, List.of(candidate("groq", broken), candidate("gemini", ScriptedChatModel.answering("ok")))),
                CircuitBreakerConfig.custom()
                        .slidingWindowSize(4).minimumNumberOfCalls(4).failureRateThreshold(50)
                        .waitDurationInOpenState(Duration.ofMinutes(1))
                        .build(),
                meters);

        for (int i = 0; i < 10; i++) {
            assertThat(gateway.call(LlmLane.FAST, LlmGatewayTest::ask).provider()).isEqualTo("gemini");
        }

        // After 4 failures the circuit opens: the broken provider is skipped instead of being called 10 times.
        assertThat(broken.calls()).isEqualTo(4);
        assertThat(gateway.status().get(LlmLane.FAST).getFirst().circuit()).isEqualTo("OPEN");
    }

    record Extracted(String supplier, double total) {
    }

    @Test
    void structuredOutputGoesThroughTheGateway() {
        LlmResult<Extracted> result = gateway(candidate("gemini", ScriptedChatModel.answering("""
                {"supplier": "Imprimerie Dupont", "total": 1234.5}
                """)))
                .call(LlmLane.FAST, client -> client.prompt().user("extract").call().entity(Extracted.class));

        assertThat(result.value()).isEqualTo(new Extracted("Imprimerie Dupont", 1234.5));
    }
}
