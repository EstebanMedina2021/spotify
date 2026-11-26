package com.playlist.recommendation;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.ai.provider", havingValue = "ollama")
public class OllamaAiClient implements AiRecommendationClient {

    private static final String PROVIDER = "OLLAMA";

    private final ChatClient chatClient;
    private final PromptTemplates promptTemplates;
    private final AiResponseParser parser;

    public OllamaAiClient(ChatClient.Builder chatClientBuilder,
                          PromptTemplates promptTemplates,
                          AiResponseParser parser) {
        this.chatClient = chatClientBuilder.build();
        this.promptTemplates = promptTemplates;
        this.parser = parser;
    }

    @Override
    public CompletableFuture<RecommendationResult> recommend(List<CancionSnapshot> canciones, String promptVersion) {
        return CompletableFuture.supplyAsync(() -> {
            String raw;
            try {
                raw = chatClient.prompt().user(promptTemplates.buildPrompt(canciones)).call().content();
            } catch (Exception ex) {
                throw AiErrorMapper.map(ex, PROVIDER);
            }
            return parser.parse(raw, PROVIDER, promptVersion);
        });
    }
}
