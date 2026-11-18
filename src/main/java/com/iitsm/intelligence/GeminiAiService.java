package com.iitsm.intelligence;

import com.iitsm.domain.model.Category;
import com.iitsm.domain.model.Incident;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Optional Gemini REST API adapter implementing AiTextService with automatic local fallback. */
@Component("geminiAiService")
public class GeminiAiService implements AiTextService {

    private static final Logger log = LoggerFactory.getLogger(GeminiAiService.class);
    private static final String GEMINI_URL_TEMPLATE =
            "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent?key=%s";

    private final LocalTfIdfAiService localFallback;
    private final RestClient restClient;
    private final String apiKey;
    private final String modelName;

    /** Constructs GeminiAiService configured from GEMINI_API_KEY and app.ai.gemini.model. */
    public GeminiAiService(
            LocalTfIdfAiService localFallback,
            @Value("${app.ai.gemini.api-key:}") String apiKey,
            @Value("${app.ai.gemini.model:gemini-2.5-flash}") String modelName) {
        this.localFallback = localFallback;
        this.restClient = RestClient.create();
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.modelName = modelName == null || modelName.isBlank() ? "gemini-2.5-flash" : modelName.trim();
    }

    /** Returns true when a non-blank Gemini API key is configured. */
    public boolean isEnabled() {
        return !apiKey.isBlank();
    }

    @Override
    public Optional<Category> suggestCategory(String description, List<Category> categories) {
        if (!isEnabled() || categories == null || categories.isEmpty()) {
            return localFallback.suggestCategory(description, categories);
        }
        try {
            String names = categories.stream().map(Category::getName).reduce((a, b) -> a + ", " + b).orElse("");
            String prompt = "Pick the single best IT incident category from [" + names
                    + "] for this issue. Reply with ONLY the category name.\nIssue: " + description;
            String reply = callGemini(prompt);
            if (reply != null) {
                Optional<Category> matched = categories.stream()
                        .filter(c -> reply.trim().equalsIgnoreCase(c.getName())
                                || reply.toLowerCase().contains(c.getName().toLowerCase()))
                        .findFirst();
                if (matched.isPresent()) {
                    return matched;
                }
            }
        } catch (Exception ex) {
            log.warn("Gemini suggestCategory failed; falling back to LocalTfIdfAiService: {}", ex.getMessage());
        }
        return localFallback.suggestCategory(description, categories);
    }

    @Override
    public Optional<String> suggestResolution(Incident incident, List<Incident> resolvedHistory) {
        if (!isEnabled() || incident == null) {
            return localFallback.suggestResolution(incident, resolvedHistory);
        }
        try {
            String serviceName = incident.getAffectedService() != null ? incident.getAffectedService().getName() : "IT";
            String prompt = "Provide concise 3-step troubleshooting guidance for IT service '"
                    + serviceName + "' issue: " + incident.getDescription();
            String reply = callGemini(prompt);
            if (reply != null && !reply.isBlank()) {
                return Optional.of("Gemini suggestion: " + reply.trim());
            }
        } catch (Exception ex) {
            log.warn("Gemini suggestResolution failed; falling back to LocalTfIdfAiService: {}", ex.getMessage());
        }
        return localFallback.suggestResolution(incident, resolvedHistory);
    }

    @SuppressWarnings("unchecked")
    private String callGemini(String promptText) {
        String url = String.format(GEMINI_URL_TEMPLATE, modelName, apiKey);
        Map<String, Object> body = Map.of(
                "contents", List.of(Map.of("parts", List.of(Map.of("text", promptText))))
        );
        Map<String, Object> response = restClient.post()
                .uri(url)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(Map.class);
        if (response == null) {
            return null;
        }
        Object candidatesObj = response.get("candidates");
        if (!(candidatesObj instanceof List<?> candidates) || candidates.isEmpty()) {
            return null;
        }
        Object first = candidates.get(0);
        if (!(first instanceof Map<?, ?> firstMap)) {
            return null;
        }
        Object contentObj = firstMap.get("content");
        if (!(contentObj instanceof Map<?, ?> contentMap)) {
            return null;
        }
        Object partsObj = contentMap.get("parts");
        if (!(partsObj instanceof List<?> parts) || parts.isEmpty()) {
            return null;
        }
        Object part0 = parts.get(0);
        if (part0 instanceof Map<?, ?> partMap && partMap.get("text") != null) {
            return partMap.get("text").toString();
        }
        return null;
    }
}
