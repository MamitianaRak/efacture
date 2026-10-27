package com.mamitiana.efacture.ai;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

/** A chat model that answers from a script, for tests that must not touch the network. */
public class ScriptedChatModel implements ChatModel {

    private final Supplier<String> answer;
    private final AtomicInteger calls = new AtomicInteger();

    private ScriptedChatModel(Supplier<String> answer) {
        this.answer = answer;
    }

    public static ScriptedChatModel answering(String text) {
        return new ScriptedChatModel(() -> text);
    }

    public static ScriptedChatModel failing(String error) {
        return new ScriptedChatModel(() -> {
            throw new IllegalStateException(error);
        });
    }

    public int calls() {
        return calls.get();
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        calls.incrementAndGet();
        return new ChatResponse(List.of(new Generation(new AssistantMessage(answer.get()))));
    }
}
