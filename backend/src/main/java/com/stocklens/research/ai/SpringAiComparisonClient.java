package com.stocklens.research.ai;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Component
public class SpringAiComparisonClient implements ComparisonAiClient {

    private final ObjectProvider<ChatClient.Builder> chatClientBuilder;
    private final AiComparisonProperties properties;

    public SpringAiComparisonClient(
            ObjectProvider<ChatClient.Builder> chatClientBuilder, AiComparisonProperties properties) {
        this.chatClientBuilder = chatClientBuilder;
        this.properties = properties;
    }

    @Override
    public AiComparisonResult generate(AiComparisonPrompt prompt) {
        if (!properties.isConfigured()) throw new AiGenerationUnavailableException();
        ChatClient.Builder builder = chatClientBuilder.getIfAvailable();
        if (builder == null) throw new AiGenerationUnavailableException();
        try {
            return builder.build().prompt()
                    .system(prompt.systemMessage())
                    .user(prompt.userMessage())
                    .call()
                    .entity(AiComparisonResult.class);
        } catch (RuntimeException exception) {
            String message = exception.getMessage() == null ? "" : exception.getMessage().toLowerCase();
            if (message.contains("429") || message.contains("rate limit")) throw new AiRateLimitedException(exception);
            throw new AiProviderException(exception);
        }
    }
}
