package com.mamitiana.efacture.ai;

import java.time.Duration;

/** A model's answer and where it came from, kept for the audit trail of AI-assisted decisions. */
public record LlmResult<T>(T value, String provider, String model, Duration duration, int attempts) {
}
