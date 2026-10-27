package com.mamitiana.efacture.ai;

/** What a request needs from the model; each lane has its own ordered list of provider/model candidates. */
public enum LlmLane {
    /** Short tasks: classify, summarise, draft a reminder. Cheap and quick models first. */
    FAST,
    /** Multi-step reasoning and tool calling (agents). Stronger models first. */
    REASONING,
    /** Reading invoice documents (PDF, images). Needs a model that accepts files. */
    DOCUMENT
}
