package com.mamitiana.efacture.ai;

import java.util.List;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Diagnostics for the LLM gateway: which models each lane uses, and a live round trip. */
@RestController
@RequestMapping("/api/ai")
@PreAuthorize("hasRole('MANAGER')")
public class AiController {

    private final LlmGateway gateway;

    public AiController(LlmGateway gateway) {
        this.gateway = gateway;
    }

    @GetMapping("/status")
    public Map<LlmLane, List<LlmGateway.CandidateStatus>> status() {
        return gateway.status();
    }

    @PostMapping("/ping")
    public PingResponse ping(@RequestParam(defaultValue = "FAST") LlmLane lane) {
        LlmResult<String> result = gateway.call(lane, client -> client.prompt()
                .user("Réponds uniquement par le mot : OK")
                .call()
                .content());
        return new PingResponse(result.value(), result.provider(), result.model(), result.duration().toMillis(),
                result.attempts());
    }

    public record PingResponse(String answer, String provider, String model, long durationMs, int attempts) {
    }
}
