package com.mamitiana.efacture.ai;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * LLM providers and, for each lane, the candidates to try in order, written "provider:model".
 * A provider without an API key is skipped, so the application starts (and its tests run) without any key.
 */
@ConfigurationProperties("app.llm")
public record LlmProperties(Duration timeout, Map<String, Provider> providers, Map<LlmLane, List<String>> lanes) {

    public record Provider(String baseUrl, String apiKey) {

        /** Keys copied into .env with quotes ("...") would otherwise be sent with the quotes and rejected. */
        public Provider {
            if (apiKey != null) {
                apiKey = apiKey.strip().replaceAll("^[\"']|[\"']$", "");
            }
        }

        public boolean configured() {
            return apiKey != null && !apiKey.isBlank();
        }
    }
}
