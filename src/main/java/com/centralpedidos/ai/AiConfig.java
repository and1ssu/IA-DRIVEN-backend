package com.centralpedidos.ai;

import java.net.URI;
import java.time.Duration;

public record AiConfig(
        String apiKey,
        URI baseUrl,
        String model,
        boolean required,
        Duration timeout
) {
    public static AiConfig fromEnvironment() {
        String apiKey = firstNonBlank(System.getenv("OPENAI_API_KEY"), System.getenv("AI_API_KEY"));
        String baseUrl = firstNonBlank(System.getenv("AI_BASE_URL"), "https://api.openai.com/v1");
        String model = firstNonBlank(System.getenv("AI_MODEL"), "gpt-4o-mini");
        boolean required = "true".equalsIgnoreCase(firstNonBlank(System.getenv("AI_REQUIRED"), "false"));
        return new AiConfig(apiKey, URI.create(baseUrl), model, required, Duration.ofSeconds(25));
    }

    public boolean enabled() {
        return apiKey != null && !apiKey.isBlank();
    }

    private static String firstNonBlank(String first, String fallback) {
        if (first != null && !first.isBlank()) {
            return first.trim();
        }
        return fallback;
    }
}
