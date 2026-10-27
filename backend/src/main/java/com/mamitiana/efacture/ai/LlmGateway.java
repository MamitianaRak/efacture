package com.mamitiana.efacture.ai;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;

/**
 * Single entry point to the LLMs. Each lane tries its candidates in order and falls back to the next one
 * when a provider fails (quota, outage, timeout). A circuit breaker per provider stops calling a provider
 * that keeps failing, so a dead provider costs one fast skip instead of a timeout on every request.
 *
 * Callers write their request against a {@link ChatClient} (prompt, tools, structured output); the gateway
 * decides which provider runs it.
 */
public class LlmGateway {

    private static final Logger log = LoggerFactory.getLogger(LlmGateway.class);

    private final Map<LlmLane, List<LlmCandidate>> lanes;
    private final CircuitBreakerRegistry breakers;
    private final MeterRegistry meters;

    public LlmGateway(Map<LlmLane, List<LlmCandidate>> lanes, CircuitBreakerConfig breakerConfig, MeterRegistry meters) {
        this.lanes = Map.copyOf(lanes);
        this.breakers = CircuitBreakerRegistry.of(breakerConfig);
        this.meters = meters;
    }

    public static CircuitBreakerConfig defaultBreakerConfig() {
        return CircuitBreakerConfig.custom()
                .slidingWindowSize(10)
                .minimumNumberOfCalls(4)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .permittedNumberOfCallsInHalfOpenState(1)
                .build();
    }

    public <T> LlmResult<T> call(LlmLane lane, Function<ChatClient, T> request) {
        List<LlmCandidate> candidates = lanes.getOrDefault(lane, List.of());
        RuntimeException lastError = null;
        int attempts = 0;

        for (LlmCandidate candidate : candidates) {
            CircuitBreaker breaker = breakers.circuitBreaker(candidate.provider());
            Timer.Sample sample = Timer.start(meters);
            try {
                attempts++;
                T value = breaker.executeSupplier(() -> request.apply(candidate.client()));
                long nanos = sample.stop(timer(lane, candidate));
                count(lane, candidate, "success");
                if (attempts > 1) {
                    meters.counter("efacture.llm.fallbacks", "lane", lane.name(), "provider", candidate.provider()).increment();
                }
                return new LlmResult<>(value, candidate.provider(), candidate.model(), Duration.ofNanos(nanos), attempts);
            } catch (CallNotPermittedException open) {
                count(lane, candidate, "skipped");
                log.warn("LLM provider {} skipped for {}: circuit open", candidate.provider(), lane);
                lastError = open;
            } catch (RuntimeException e) {
                sample.stop(timer(lane, candidate));
                count(lane, candidate, "failure");
                log.warn("LLM provider {} ({}) failed for {}: {}", candidate.provider(), candidate.model(), lane, e.toString());
                lastError = e;
            }
        }
        throw new LlmUnavailableException(lane, lastError);
    }

    /** Provider, model and circuit state of every candidate, for the status endpoint. */
    public Map<LlmLane, List<CandidateStatus>> status() {
        return lanes.entrySet().stream().collect(java.util.stream.Collectors.toMap(
                Map.Entry::getKey,
                e -> e.getValue().stream()
                        .map(c -> new CandidateStatus(c.provider(), c.model(),
                                breakers.circuitBreaker(c.provider()).getState().name()))
                        .toList()));
    }

    public record CandidateStatus(String provider, String model, String circuit) {
    }

    private Timer timer(LlmLane lane, LlmCandidate candidate) {
        return meters.timer("efacture.llm.duration", "lane", lane.name(), "provider", candidate.provider());
    }

    private void count(LlmLane lane, LlmCandidate candidate, String outcome) {
        meters.counter("efacture.llm.calls", "lane", lane.name(), "provider", candidate.provider(), "outcome", outcome)
                .increment();
    }
}
