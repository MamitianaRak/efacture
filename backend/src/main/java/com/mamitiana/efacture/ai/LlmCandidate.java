package com.mamitiana.efacture.ai;

import org.springframework.ai.chat.client.ChatClient;

/** One provider/model pair a lane can use. */
public record LlmCandidate(String provider, String model, ChatClient client) {
}
