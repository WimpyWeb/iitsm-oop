package com.iitsm.intelligence;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Lightweight TF-IDF cosine similarity engine with stopword removal and light stemming. */
@Component
public class TfIdfSimilarityEngine {

    private static final Set<String> STOPWORDS = Set.of(
            "a", "an", "and", "are", "as", "at", "be", "been", "being", "by",
            "can", "cannot", "could", "did", "do", "does", "for", "from",
            "had", "has", "have", "i", "in", "into", "is", "it", "its",
            "make", "may", "my", "not", "of", "on", "or", "our", "please",
            "should", "that", "the", "their", "them", "there", "these", "they",
            "this", "to", "was", "we", "were", "will", "with", "would", "you", "your"
    );

    /** Tokenizes text, strips stopwords, and applies deterministic light stemming. */
    public List<String> tokenize(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        String[] parts = text.toLowerCase(Locale.ROOT).split("[^a-z0-9]+");
        List<String> tokens = new ArrayList<>();
        for (String raw : parts) {
            if (raw.length() < 2 || STOPWORDS.contains(raw)) {
                continue;
            }
            String stemmed = stem(raw);
            if (stemmed.length() >= 2 && !STOPWORDS.contains(stemmed)) {
                tokens.add(stemmed);
            }
        }
        return tokens;
    }

    /** Computes pairwise TF-IDF cosine similarity between two strings in [0.0, 1.0]. */
    public double similarity(String textA, String textB) {
        if (textA == null || textB == null || textA.isBlank() || textB.isBlank()) {
            return 0.0;
        }
        return similarity(textA, textB, List.of(textA, textB));
    }

    /** Computes corpus-aware TF-IDF cosine similarity between query and candidate text. */
    public double similarity(String queryText, String candidateText, Collection<String> corpusTexts) {
        List<String> queryTokens = tokenize(queryText);
        List<String> candTokens = tokenize(candidateText);
        if (queryTokens.isEmpty() || candTokens.isEmpty()) {
            return 0.0;
        }

        List<List<String>> corpusTokens = new ArrayList<>();
        if (corpusTexts != null) {
            for (String doc : corpusTexts) {
                List<String> dt = tokenize(doc);
                if (!dt.isEmpty()) {
                    corpusTokens.add(dt);
                }
            }
        }
        if (corpusTokens.isEmpty()) {
            corpusTokens.add(queryTokens);
            corpusTokens.add(candTokens);
        }

        Map<String, Double> idf = computeIdf(corpusTokens);
        Map<String, Double> vecA = toTfIdfVector(queryTokens, idf);
        Map<String, Double> vecB = toTfIdfVector(candTokens, idf);
        return cosine(vecA, vecB);
    }

    /** Ranks candidate ID->text pairs meeting at least minScore in descending similarity order. */
    public Map<String, Double> rankSimilar(String queryText, Map<String, String> idToText, double minScore) {
        if (queryText == null || queryText.isBlank() || idToText == null || idToText.isEmpty()) {
            return Map.of();
        }
        List<String> corpus = new ArrayList<>(idToText.values());
        corpus.add(queryText);

        List<Map.Entry<String, Double>> matches = new ArrayList<>();
        for (Map.Entry<String, String> entry : idToText.entrySet()) {
            double score = similarity(queryText, entry.getValue(), corpus);
            if (score >= minScore) {
                matches.add(Map.entry(entry.getKey(), score));
            }
        }
        matches.sort(Map.Entry.<String, Double>comparingByValue(Comparator.reverseOrder()));

        Map<String, Double> ordered = new LinkedHashMap<>();
        for (Map.Entry<String, Double> m : matches) {
            ordered.put(m.getKey(), m.getValue());
        }
        return ordered;
    }

    /** Extracts top frequent stemmed keywords across a collection of incident descriptions. */
    public List<String> topKeywords(Collection<String> texts, int limit) {
        if (texts == null || texts.isEmpty() || limit <= 0) {
            return List.of();
        }
        Map<String, Integer> counts = new HashMap<>();
        for (String text : texts) {
            for (String token : tokenize(text)) {
                counts.merge(token, 1, Integer::sum);
            }
        }
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue(Comparator.reverseOrder())
                        .thenComparing(Map.Entry::getKey))
                .limit(limit)
                .map(Map.Entry::getKey)
                .toList();
    }

    private Map<String, Double> computeIdf(List<List<String>> corpus) {
        int docCount = corpus.size();
        Map<String, Integer> docFreq = new HashMap<>();
        for (List<String> doc : corpus) {
            Set<String> unique = new HashSet<>(doc);
            for (String term : unique) {
                docFreq.merge(term, 1, Integer::sum);
            }
        }
        Map<String, Double> idf = new HashMap<>();
        for (Map.Entry<String, Integer> entry : docFreq.entrySet()) {
            // Smoothed IDF preserves positive weight for shared domain terms in small corpora.
            double weight = 1.0 + 0.25 * Math.log((docCount + 1.0) / (entry.getValue() + 1.0));
            idf.put(entry.getKey(), weight);
        }
        return idf;
    }

    private Map<String, Double> toTfIdfVector(List<String> tokens, Map<String, Double> idf) {
        Map<String, Integer> tf = new HashMap<>();
        for (String token : tokens) {
            tf.merge(token, 1, Integer::sum);
        }
        Map<String, Double> vec = new HashMap<>();
        double len = tokens.size();
        for (Map.Entry<String, Integer> entry : tf.entrySet()) {
            double termFreq = entry.getValue() / len;
            double termIdf = idf.getOrDefault(entry.getKey(), 1.0);
            vec.put(entry.getKey(), termFreq * termIdf);
        }
        return vec;
    }

    private double cosine(Map<String, Double> vecA, Map<String, Double> vecB) {
        double dot = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        for (double v : vecA.values()) {
            normA += v * v;
        }
        for (double v : vecB.values()) {
            normB += v * v;
        }
        if (normA == 0.0 || normB == 0.0) {
            return 0.0;
        }
        for (Map.Entry<String, Double> entry : vecA.entrySet()) {
            Double bVal = vecB.get(entry.getKey());
            if (bVal != null) {
                dot += entry.getValue() * bVal;
            }
        }
        return Math.min(1.0, dot / (Math.sqrt(normA) * Math.sqrt(normB)));
    }

    private String stem(String word) {
        if (word.endsWith("ies") && word.length() > 4) {
            return word.substring(0, word.length() - 3) + "y";
        }
        String normalized = word;
        if (normalized.endsWith("s") && !normalized.endsWith("ss") && normalized.length() > 3) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (normalized.endsWith("ing") && normalized.length() > 5) {
            return normalized.substring(0, normalized.length() - 3);
        }
        if (normalized.endsWith("ation") && normalized.length() > 7) {
            return normalized.substring(0, normalized.length() - 5);
        }
        if (normalized.endsWith("tion") && normalized.length() > 6) {
            return normalized.substring(0, normalized.length() - 4);
        }
        if (normalized.endsWith("able") && normalized.length() > 7) {
            return normalized.substring(0, normalized.length() - 4);
        }
        if (normalized.endsWith("ness") && normalized.length() > 7) {
            return normalized.substring(0, normalized.length() - 4);
        }
        if (normalized.endsWith("ed") && normalized.length() > 4) {
            return normalized.substring(0, normalized.length() - 2);
        }
        if (normalized.endsWith("ly") && normalized.length() > 5) {
            return normalized.substring(0, normalized.length() - 2);
        }
        return normalized;
    }
}
