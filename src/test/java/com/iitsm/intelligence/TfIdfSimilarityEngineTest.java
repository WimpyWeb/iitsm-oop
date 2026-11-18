package com.iitsm.intelligence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Unit tests verifying TF-IDF similarity detection on course payment examples and thresholds. */
class TfIdfSimilarityEngineTest {

    private TfIdfSimilarityEngine engine;

    @BeforeEach
    void setUp() {
        engine = new TfIdfSimilarityEngine();
    }

    @Test
    void coursePaymentExamplesAreAllFlaggedRelated() {
        String s1 = "Payment application unavailable";
        String s2 = "Payment transaction failing";
        String s3 = "Unable to make payment";
        List<String> corpus = List.of(s1, s2, s3);

        double sim12 = engine.similarity(s1, s2, corpus);
        double sim13 = engine.similarity(s1, s3, corpus);
        double sim23 = engine.similarity(s2, s3, corpus);

        assertTrue(sim12 >= 0.2 && sim12 < 0.8, "Expected s1 vs s2 related, got " + sim12);
        assertTrue(sim13 >= 0.2 && sim13 < 0.8, "Expected s1 vs s3 related, got " + sim13);
        assertTrue(sim23 >= 0.2 && sim23 < 0.8, "Expected s2 vs s3 related, got " + sim23);
    }

    @Test
    void nearIdenticalDescriptionsExceedExactDuplicateThreshold() {
        String s1 = "The payment application is unavailable and users are unable to complete transactions.";
        String s2 = "Payment application is unavailable and users unable to complete transactions";
        double sim = engine.similarity(s1, s2);
        assertTrue(sim >= 0.8, "Expected exact duplicate score >= 0.8, got " + sim);
    }

    @Test
    void unrelatedDescriptionsRemainBelowRelatedThreshold() {
        double sim = engine.similarity(
                "Payment application unavailable",
                "Office printer paper jam on fourth floor");
        assertTrue(sim < 0.2, "Expected unrelated score < 0.2, got " + sim);
    }

    @Test
    void rankSimilarFiltersAndOrdersDescending() {
        Map<String, String> candidates = Map.of(
                "INC1001", "Payment application unavailable",
                "INC1002", "Printer toner empty",
                "INC1003", "Payment transaction failing"
        );
        Map<String, Double> ranked = engine.rankSimilar("Unable to make payment", candidates, 0.2);
        assertEquals(2, ranked.size());
        assertTrue(ranked.containsKey("INC1001"));
        assertTrue(ranked.containsKey("INC1003"));
    }
}
