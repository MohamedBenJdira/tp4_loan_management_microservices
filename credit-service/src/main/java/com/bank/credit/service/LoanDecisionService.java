package com.bank.credit.service;

import com.bank.common.event.CommercialScoreCalculatedEvent;
import com.bank.common.event.RiskScoreCalculatedEvent;
import com.bank.common.event.LoanDecisionMadeEvent;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service de crédit - Moteur de décision
 * Combine les scores commerciaux et de risque pour générer une décision
 */
@Slf4j
@Service
public class LoanDecisionService {
    
    private final ConcurrentHashMap<String, LoanContext> loanContexts = new ConcurrentHashMap<>();
    
    @Data
    @AllArgsConstructor
    public static class LoanContext {
        private String loanApplicationId;
        private CommercialScoreCalculatedEvent commercialScore;
        private RiskScoreCalculatedEvent riskScore;
        private double requestedAmount;
        private String clientName;
    }
    
    /**
     * Enregistre une nouvelle demande de prêt
     */
    public Mono<String> registerLoanApplication(
            String loanApplicationId,
            String clientName,
            double requestedAmount) {
        
        return Mono.fromCallable(() -> {
            log.info("Enregistrement nouvelle demande: {} pour {}", loanApplicationId, clientName);
            loanContexts.put(loanApplicationId, 
                new LoanContext(loanApplicationId, null, null, requestedAmount, clientName));
            return loanApplicationId;
        });
    }
    
    /**
     * Traite le score commercial
     */
    public Mono<Void> handleCommercialScore(CommercialScoreCalculatedEvent event) {
        return Mono.fromRunnable(() -> {
            log.info("Traitement score commercial reçu: {}", event.getCommercialScore());
            LoanContext context = loanContexts.get(event.getAggregateId());
            if (context != null) {
                context.setCommercialScore(event);
                log.debug("Context mis à jour avec score commercial");
            }
        });
    }
    
    /**
     * Traite le score de risque
     */
    public Mono<Void> handleRiskScore(RiskScoreCalculatedEvent event) {
        return Mono.fromRunnable(() -> {
            log.info("Traitement score de risque reçu: {}", event.getRiskScore());
            LoanContext context = loanContexts.get(event.getAggregateId());
            if (context != null) {
                context.setRiskScore(event);
                log.debug("Context mis à jour avec score risque");
            }
        });
    }
    
    /**
     * Prend une décision finale quand les deux scores sont disponibles
     */
    public Mono<LoanDecisionMadeEvent> makeDecision(String loanApplicationId) {
        return Mono.fromCallable(() -> {
            LoanContext context = loanContexts.get(loanApplicationId);
            
            if (context == null) {
                throw new IllegalArgumentException("Contexte de prêt non trouvé: " + loanApplicationId);
            }
            
            if (context.getCommercialScore() == null || context.getRiskScore() == null) {
                throw new IllegalStateException("Scores non disponibles pour la décision");
            }
            
            log.info("Décision finale pour {} - Commercial: {}, Risque: {}", 
                     loanApplicationId,
                     context.getCommercialScore().getCommercialScore(),
                     context.getRiskScore().getRiskScore());
            
            return makeDecisionLogic(context);
        })
        .doOnNext(event -> {
            log.info("Décision finale: {} pour le montant {}", 
                     event.getDecision(), event.getApprovedAmount());
            loanContexts.remove(loanApplicationId);
        })
        .doOnError(err -> log.error("Erreur prise de décision", err));
    }
    
    private LoanDecisionMadeEvent makeDecisionLogic(LoanContext context) {
        double commercialScore = context.getCommercialScore().getCommercialScore();
        double riskScore = context.getRiskScore().getRiskScore();
        String riskLevel = context.getRiskScore().getRiskLevel();
        
        // Règles de décision
        String decision;
        double approvedAmount = 0;
        String reason = "";
        
        if (riskScore >= 80) {
            decision = "REJECTED";
            reason = "Risque trop élevé (" + riskLevel + ")";
        }
        else if (commercialScore < 40) {
            decision = "REJECTED";
            reason = "Profil commercial insuffisant";
        }
        else if (riskScore >= 70 && commercialScore < 60) {
            decision = "REJECTED";
            reason = "Combinaison risque/commercial unfavorable";
        }
        else if (riskScore >= 60) {
            // Approbation partielle
            decision = "APPROVED_WITH_CONDITIONS";
            approvedAmount = context.getRequestedAmount() * 0.7; // 70% du montant demandé
            reason = "Accordé à 70% - Risque: " + riskLevel;
        }
        else if (commercialScore >= 70) {
            decision = "APPROVED";
            approvedAmount = context.getRequestedAmount();
            reason = "Profil excellent - Conditions standards";
        }
        else {
            decision = "APPROVED";
            approvedAmount = context.getRequestedAmount() * 0.9; // 90% du montant demandé
            reason = "Approuvé à 90% - Risque moyen";
        }
        
        return new LoanDecisionMadeEvent(
                context.getLoanApplicationId(),
                decision,
                approvedAmount,
                reason
        );
    }
}
