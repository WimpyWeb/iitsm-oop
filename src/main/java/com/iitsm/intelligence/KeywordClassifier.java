package com.iitsm.intelligence;

import com.iitsm.domain.model.Category;
import com.iitsm.domain.model.ServiceEntity;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Keyword-based classifier matching incident descriptions against DB Category and Service keywords. */
@Component
public class KeywordClassifier {

    /** Suggests the best matching Category from DB categories based on keyword overlap. */
    public Optional<Category> suggestCategory(String text, List<Category> categories) {
        if (text == null || text.isBlank() || categories == null || categories.isEmpty()) {
            return Optional.empty();
        }
        String lower = text.toLowerCase(Locale.ROOT);
        Category best = null;
        int bestScore = 0;

        for (Category category : categories) {
            int score = scoreTokens(lower, category.getName(), category.getKeywords());
            if (score > bestScore) {
                bestScore = score;
                best = category;
            }
        }
        return Optional.ofNullable(best);
    }

    /** Suggests the best matching ServiceEntity from DB services based on keyword overlap. */
    public Optional<ServiceEntity> suggestService(String text, List<ServiceEntity> services) {
        if (text == null || text.isBlank() || services == null || services.isEmpty()) {
            return Optional.empty();
        }
        String lower = text.toLowerCase(Locale.ROOT);
        ServiceEntity best = null;
        int bestScore = 0;

        for (ServiceEntity service : services) {
            int score = scoreTokens(lower, service.getName(), service.getKeywords());
            if (score > bestScore) {
                bestScore = score;
                best = service;
            }
        }
        return Optional.ofNullable(best);
    }

    private int scoreTokens(String lowerText, String entityName, Collection<String> keywords) {
        int score = 0;
        if (entityName != null && !entityName.isBlank()
                && lowerText.contains(entityName.toLowerCase(Locale.ROOT))) {
            score += 3;
        }
        if (keywords != null) {
            for (String kw : keywords) {
                if (kw != null && !kw.isBlank() && lowerText.contains(kw.toLowerCase(Locale.ROOT))) {
                    score += 2;
                }
            }
        }
        return score;
    }
}
