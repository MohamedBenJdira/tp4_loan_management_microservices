package com.bank.demo;

import com.bank.commercial.service.CommercialScoringService;
import com.bank.risk.service.RiskAnalysisService;
import com.bank.credit.service.LoanDecisionService;
import com.bank.notification.service.NotificationService;
import com.bank.common.event.*;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuples;
import java.time.Duration;

/**
 * DEMO: Orchestration du processus complet de demande de prêt
 * Démonstration de la communication réactive entre microservices
 */
@Slf4j
public class LoanApplicationDemo {
    
    private final CommercialScoringService commercialService;
    private final RiskAnalysisService riskService;
    private final LoanDecisionService creditService;
    private final NotificationService notificationService;
    
    public LoanApplicationDemo(
            CommercialScoringService commercialService,
            RiskAnalysisService riskService,
            LoanDecisionService creditService,
            NotificationService notificationService) {
        this.commercialService = commercialService;
        this.riskService = riskService;
        this.creditService = creditService;
        this.notificationService = notificationService;
    }
    
    /**
     * Traite une demande de prêt de bout en bout
     * Orchestration des services réactifs
     */
    public Mono<LoanDecisionMadeEvent> processLoanApplication(
            String loanApplicationId,
            String clientName,
            double monthlyIncome,
            int yearsEmployment,
            double existingDebts,
            int creditHistoryYears,
            double requestedAmount) {
        
        log.info("╔════════════════════════════════════════════════════════╗");
        log.info("║  TRAITEMENT DEMANDE DE PRÊT REACTIVE                  ║");
        log.info("║  Demande: {} | Client: {}                           ║", 
                 loanApplicationId.substring(0, Math.min(8, loanApplicationId.length())), 
                 clientName);
        log.info("║  Montant demandé: {:.2f}€                              ║", requestedAmount);
        log.info("╚════════════════════════════════════════════════════════╝");
        
        // Phase 1: Enregistrement
        return creditService.registerLoanApplication(loanApplicationId, clientName, requestedAmount)
            .flatMap(appId -> {
                log.info("\n[PHASE 1] Enregistrement demande - ID: {}\n", appId);
                
                // Phase 2: Appels parallèles réactifs
                Mono<CommercialScoreCalculatedEvent> commercialScoreMono = 
                    commercialService.evaluateScore(
                        appId, clientName, monthlyIncome, yearsEmployment, requestedAmount);
                
                Mono<RiskScoreCalculatedEvent> riskScoreMono = 
                    riskService.analyzeRisk(
                        appId, clientName, monthlyIncome, existingDebts, creditHistoryYears);
                
                // Combinaison des résultats
                return Mono.zip(commercialScoreMono, riskScoreMono)
                    .doOnNext(tuple -> {
                        log.info("\n[PHASE 2] Scores reçus parallèlement:");
                        log.info("  ✓ Commercial: {}", tuple.getT1().getCommercialRating());
                        log.info("  ✓ Risque: {}", tuple.getT2().getRiskLevel());
                        log.info();
                    })
                    .zipWith(Mono.just(appId))
                    .flatMap(zipData -> {
                        var scores = zipData.getT1();
                        var appId2 = zipData.getT2();
                        
                        CommercialScoreCalculatedEvent commScore = scores.getT1();
                        RiskScoreCalculatedEvent riskScore = scores.getT2();
                        
                        // Mise à jour du contexte de crédit
                        return creditService.handleCommercialScore(commScore)
                            .then(creditService.handleRiskScore(riskScore))
                            .then(Mono.just(appId2));
                    })
                    .flatMap(appId2 -> {
                        log.info("[PHASE 3] Prise de décision...");
                        return creditService.makeDecision(appId2);
                    })
                    .doOnNext(decision -> {
                        log.info("\n[PHASE 4] Décision finale:");
                        log.info("  Decision: {}", decision.getDecision());
                        log.info("  Montant approuvé: {:.2f}€", decision.getApprovedAmount());
                        log.info("  Raison: {}", decision.getDecisionReason());
                        log.info();
                    })
                    .flatMap(decision -> 
                        notificationService.notifyDecision(decision)
                            .then(Mono.just(decision))
                    )
                    .doOnNext(decision -> {
                        log.info("[PHASE 5] Notification envoyée");
                        log.info("\n╔════════════════════════════════════════════════════════╗");
                        log.info("║  TRAITEMENT COMPLÉTÉ                                  ║");
                        log.info("╚════════════════════════════════════════════════════════╝\n");
                    });
            });
    }
    
    /**
     * Exemple d'utilisation
     */
    public static void main(String[] args) {
        // Les services seraient injectés par Spring en production
        CommercialScoringService commercialService = new CommercialScoringService();
        RiskAnalysisService riskService = new RiskAnalysisService();
        LoanDecisionService creditService = new LoanDecisionService();
        NotificationService notificationService = new NotificationService();
        
        LoanApplicationDemo demo = new LoanApplicationDemo(
            commercialService, riskService, creditService, notificationService);
        
        // Cas 1: Client avec bon profil
        demo.processLoanApplication(
                "LOAN-001",
                "Jean Dupont",
                5500.0,    // monthlyIncome
                8,         // yearsEmployment
                1500.0,    // existingDebts
                7,         // creditHistoryYears
                50000.0    // requestedAmount
            )
            .doOnError(Throwable::printStackTrace)
            .block();
        
        // Cas 2: Client avec profil moyen
        demo.processLoanApplication(
                "LOAN-002",
                "Marie Martin",
                3200.0,
                3,
                2500.0,
                2,
                30000.0
            )
            .doOnError(Throwable::printStackTrace)
            .block();
        
        // Cas 3: Client avec profil risqué
        demo.processLoanApplication(
                "LOAN-003",
                "Pierre Moreau",
                2800.0,
                1,
                3000.0,
                0,
                25000.0
            )
            .doOnError(Throwable::printStackTrace)
            .block();
    }
}
