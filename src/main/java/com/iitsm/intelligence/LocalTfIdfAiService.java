package com.iitsm.intelligence;

import com.iitsm.domain.model.Category;
import com.iitsm.domain.model.Incident;
import com.iitsm.domain.model.Resolution;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Default local offline AiTextService powered by KeywordClassifier and TfIdfSimilarityEngine. */
@Component("localTfIdfAiService")
public class LocalTfIdfAiService implements AiTextService {

    private static final double MIN_RESOLUTION_SIMILARITY = 0.2;

    private final KeywordClassifier keywordClassifier;
    private final TfIdfSimilarityEngine similarityEngine;

    /** Constructs LocalTfIdfAiService using local keyword and TF-IDF engines. */
    public LocalTfIdfAiService(KeywordClassifier keywordClassifier, TfIdfSimilarityEngine similarityEngine) {
        this.keywordClassifier = keywordClassifier;
        this.similarityEngine = similarityEngine;
    }

    @Override
    public Optional<Category> suggestCategory(String description, List<Category> categories) {
        return keywordClassifier.suggestCategory(description, categories);
    }

    @Override
    public Optional<String> suggestResolution(Incident incident, List<Incident> resolvedHistory) {
        if (incident == null || resolvedHistory == null || resolvedHistory.isEmpty()) {
            return Optional.empty();
        }
        Map<String, String> candidateTexts = new LinkedHashMap<>();
        Map<String, Incident> byId = new LinkedHashMap<>();
        for (Incident past : resolvedHistory) {
            if (past.getResolution() != null && !past.getId().equals(incident.getId())) {
                candidateTexts.put(past.getId(), past.getDescription());
                byId.put(past.getId(), past);
            }
        }
        if (candidateTexts.isEmpty()) {
            return Optional.empty();
        }
        Map<String, Double> ranked = similarityEngine.rankSimilar(
                incident.getDescription(), candidateTexts, MIN_RESOLUTION_SIMILARITY);
        if (ranked.isEmpty()) {
            return Optional.empty();
        }
        Map.Entry<String, Double> best = ranked.entrySet().iterator().next();
        Incident match = byId.get(best.getKey());
        Resolution res = match.getResolution();
        String suggestion = String.format("From similar incident %s (%.0f%% match): %s — Steps: %s",
                match.getId(), best.getValue() * 100, res.getSummary(), res.getSteps());
        return Optional.of(suggestion);
    }
}
