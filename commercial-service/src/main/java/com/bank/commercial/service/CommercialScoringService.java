package com.bank.commercial.service;

import com.bank.common.event.CommercialScoreCalculatedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Service d'évaluation du scoring commercial
 * Analyse le profil client et génère un score commercial
 */
@Slf4j
@Service
public class CommercialScoringService {
    
    /**
     * Calcule le score commercial de manière réactive
     * Simule un traitement asynchrone
     */
    public Mono<CommercialScoreCalculatedEvent> evaluateScore(
            String loanApplicationId,
            String clientName,
            double monthlyIncome,
            int yearsEmployment,
            double requestedAmount) {
        
        return Mono.fromCallable(() -> {
            log.info("Évaluation commerciale pour {}: revenu={}, emploi={} ans", 
                     clientName, monthlyIncome, yearsEmployment);
            
            // Simulation calcul score commercial
            double score = calculateCommercialScore(monthlyIncome, yearsEmployment, requestedAmount);
            String rating = getRating(score);
            String details = generateDetails(clientName, monthlyIncome, score);
            
            log.info("Score commercial calculé: {} ({})", score, rating);
            
            return new CommercialScoreCalculatedEvent(
                    loanApplicationId,
                    score,
                    rating,
                    details
            );
        })
        .doOnNext(event -> log.info("Événement commercial publié: {}", event.getEventId()))
        .doOnError(err -> log.error("Erreur évaluation commerciale", err));
    }
    
    private double calculateCommercialScore(double monthlyIncome, int yearsEmployment, double requestedAmount) {
        // Algorithme simplifié
        double score = 50.0; // Base
        
        // Bonus revenu
        if (monthlyIncome > 5000) score += 20;
        else if (monthlyIncome > 3000) score += 10;
        
        // Bonus ancienneté emploi
        if (yearsEmployment >= 5) score += 15;
        else if (yearsEmployment >= 2) score += 8;
        
        // Ratio montant/revenu
        double ratio = requestedAmount / (monthlyIncome * 12);
        if (ratio < 2) score += 5;
        else if (ratio > 5) score -= 10;
        
        // Aléatoire pour simuler la variabilité
        score += (Math.random() - 0.5) * 5;
        
        return Math.min(100, Math.max(0, score));
    }
    
    private String getRating(double score) {
        if (score >= 80) return "EXCELLENT";
        if (score >= 60) return "GOOD";
        if (score >= 40) return "ACCEPTABLE";
        return "POOR";
    }
    
    private String generateDetails(String clientName, double monthlyIncome, double score) {
        return String.format("Client: %s | Revenu: %.2f€ | Score: %.2f", 
                            clientName, monthlyIncome, score);
    }
}
