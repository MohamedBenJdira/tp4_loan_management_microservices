package com.bank.risk.service;

import com.bank.common.event.RiskScoreCalculatedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Service d'analyse de risque
 * Effectue une analyse de crédit et génère un score de risque
 */
@Slf4j
@Service
public class RiskAnalysisService {
    
    /**
     * Analyse le risque de manière réactive
     * Simule un modèle ML complexe
     */
    public Mono<RiskScoreCalculatedEvent> analyzeRisk(
            String loanApplicationId,
            String clientName,
            double monthlyIncome,
            double existingDebts,
            int creditHistoryYears) {
        
        return Mono.fromCallable(() -> {
            log.info("Analyse de risque pour {}: dettes={}, historique={} ans", 
                     clientName, existingDebts, creditHistoryYears);
            
            // Simulation calcul score de risque (ML model)
            double riskScore = calculateRiskScore(monthlyIncome, existingDebts, creditHistoryYears);
            String riskLevel = determineRiskLevel(riskScore);
            String recommendation = getRecommendation(riskScore, riskLevel);
            
            log.info("Score de risque calculé: {} ({}) - Recommandation: {}", 
                     riskScore, riskLevel, recommendation);
            
            return new RiskScoreCalculatedEvent(
                    loanApplicationId,
                    riskScore,
                    riskLevel,
                    recommendation
            );
        })
        .doOnNext(event -> log.info("Événement risque publié: {}", event.getEventId()))
        .doOnError(err -> log.error("Erreur analyse risque", err));
    }
    
    private double calculateRiskScore(double monthlyIncome, double existingDebts, int creditHistoryYears) {
        // Score inversé: plus élevé = plus de risque
        double riskScore = 50.0; // Base
        
        // Ratio d'endettement
        double debtRatio = existingDebts / monthlyIncome;
        if (debtRatio > 0.5) riskScore += 30;
        else if (debtRatio > 0.3) riskScore += 15;
        
        // Historique crédit
        if (creditHistoryYears >= 10) riskScore -= 20;
        else if (creditHistoryYears >= 5) riskScore -= 10;
        else if (creditHistoryYears < 1) riskScore += 15;
        
        // Revenu
        if (monthlyIncome < 2000) riskScore += 20;
        else if (monthlyIncome > 5000) riskScore -= 10;
        
        // Aléatoire
        riskScore += (Math.random() - 0.5) * 8;
        
        return Math.min(100, Math.max(0, riskScore));
    }
    
    private String determineRiskLevel(double riskScore) {
        if (riskScore >= 70) return "HIGH";
        if (riskScore >= 50) return "MEDIUM";
        return "LOW";
    }
    
    private String getRecommendation(double riskScore, String riskLevel) {
        if (riskScore >= 80) return "REJECT - Trop de risque";
        if (riskScore >= 70) return "REQUIRE_COLLATERAL - Garantie requise";
        if (riskScore >= 50) return "ACCEPT_WITH_CONDITIONS - Taux majoré";
        return "ACCEPT - Conditions standards";
    }
}
