package com.iitsm.intelligence;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/** Selects LocalTfIdfAiService by default or GeminiAiService when GEMINI_API_KEY is configured. */
@Configuration
public class AiConfig {

    /** Exposes the active AiTextService bean (local TF-IDF default, Gemini when key present). */
    @Bean
    @Primary
    public AiTextService activeAiTextService(LocalTfIdfAiService local, GeminiAiService gemini) {
        return gemini.isEnabled() ? gemini : local;
    }
}
