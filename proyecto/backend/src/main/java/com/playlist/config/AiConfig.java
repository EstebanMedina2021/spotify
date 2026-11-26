package com.playlist.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiConfig {

    /**
     * Spring AI auto-configura un ChatClient.Builder por cada proveedor
     * que esté en el classpath. Como solo tendremos un proveedor activo
     * (gemini u ollama), el Builder inyectado corresponde al correcto.
     *
     * Si en el futuro se activan ambos, habría que cualificar el Builder
     * con @Qualifier("googleGenAiChatClientBuilder") o similar.
     */
    @Bean
    public ChatClient.Builder chatClientBuilder(ChatClient.Builder builder) {
        return builder;
    }
}