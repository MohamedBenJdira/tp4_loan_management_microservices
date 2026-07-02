package com.bank.commercial.service;

import java.util.Set;

import org.junit.jupiter.api.Test;

import reactor.test.StepVerifier;

class CommercialScoringServiceTest {

    private static final Set<String> VALID_RATINGS = Set.of("EXCELLENT", "GOOD", "ACCEPTABLE", "POOR");

    @Test
    void evaluateScoreShouldReturnEventWithValidFieldsScoreAndRating() {
        CommercialScoringService service = new CommercialScoringService();

        StepVerifier.create(service.evaluateScore("loan-123", "Alice Martin", 4500.0, 6, 30000.0))
            .assertNext(event -> {
                org.junit.jupiter.api.Assertions.assertNotNull(event);
                org.junit.jupiter.api.Assertions.assertNotNull(event.getLoanApplicationId());
                org.junit.jupiter.api.Assertions.assertNotNull(event.getDetails());
                org.junit.jupiter.api.Assertions.assertNotNull(event.getCommercialRating());
                org.junit.jupiter.api.Assertions.assertNotNull(event.getEventId());
                org.junit.jupiter.api.Assertions.assertNotNull(event.getTimestamp());
                org.junit.jupiter.api.Assertions.assertNotNull(event.getAggregateId());

                org.junit.jupiter.api.Assertions.assertTrue(event.getCommercialScore() >= 0.0);
                org.junit.jupiter.api.Assertions.assertTrue(event.getCommercialScore() <= 100.0);

                org.junit.jupiter.api.Assertions.assertTrue(VALID_RATINGS.contains(event.getCommercialRating()));
            })
            .verifyComplete();
    }
}
