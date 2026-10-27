package com.mamitiana.efacture.ai;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Builds one chat client per provider/model from {@link LlmProperties}. Groq and Gemini both expose an
 * OpenAI-compatible API, so the same Spring AI client serves both with a different base URL.
 */
@Configuration
@EnableConfigurationProperties(LlmProperties.class)
public class LlmConfig {

    private static final Logger log = LoggerFactory.getLogger(LlmConfig.class);

    @Bean
    LlmGateway llmGateway(LlmProperties properties, MeterRegistry meters,
                          ObjectProvider<ObservationRegistry> observations) {
        ObservationRegistry observationRegistry = observations.getIfAvailable(() -> ObservationRegistry.NOOP);
        Map<String, ChatClient> clients = new HashMap<>();
        Map<LlmLane, List<LlmCandidate>> lanes = new EnumMap<>(LlmLane.class);

        properties.lanes().forEach((lane, entries) -> {
            List<LlmCandidate> candidates = new ArrayList<>();
            for (String entry : entries) {
                String[] parts = entry.split(":", 2);
                String providerName = parts[0].trim();
                String model = parts[1].trim();
                LlmProperties.Provider provider = properties.providers().get(providerName);
                if (provider == null || !provider.configured()) {
                    log.info("LLM {} not configured (no API key): skipped in lane {}", providerName, lane);
                    continue;
                }
                ChatClient client = clients.computeIfAbsent(entry,
                        key -> ChatClient.create(chatModel(provider, model, properties, observationRegistry, meters)));
                candidates.add(new LlmCandidate(providerName, model, client));
            }
            lanes.put(lane, candidates);
            log.info("LLM lane {}: {}", lane, candidates.stream().map(c -> c.provider() + ":" + c.model()).toList());
        });
        return new LlmGateway(lanes, LlmGateway.defaultBreakerConfig(), meters);
    }

    private static OpenAiChatModel chatModel(LlmProperties.Provider provider, String model, LlmProperties properties,
                                             ObservationRegistry observations, MeterRegistry meters) {
        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .baseUrl(provider.baseUrl())
                .apiKey(provider.apiKey())
                .model(model)
                .timeout(properties.timeout())
                // The gateway handles failures by switching provider; retrying the same one only adds delay.
                .maxRetries(0)
                .build();
        return OpenAiChatModel.builder()
                .options(options)
                .observationRegistry(observations)
                .meterRegistry(meters)
                .build();
    }
}
