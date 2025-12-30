package com.bank.notification.service;

import com.bank.common.event.LoanDecisionMadeEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Service de notification
 * Envoie les notifications (email, SMS) des décisions aux clients
 */
@Slf4j
@Service
public class NotificationService {
    
    /**
     * Envoie une notification de décision
     */
    public Mono<Void> notifyDecision(LoanDecisionMadeEvent event) {
        return Mono.fromRunnable(() -> {
            log.info("Notification envoyée pour demande: {}", event.getLoanApplicationId());
            log.info("Décision: {} | Montant approuvé: {}", 
                     event.getDecision(), event.getApprovedAmount());
            log.info("Raison: {}", event.getDecisionReason());
            
            // Simulation envoi email
            sendEmail(event);
            
            // Simulation envoi SMS
            sendSMS(event);
        })
        .doOnNext(v -> log.info("Notification complète"))
        .doOnError(err -> log.error("Erreur notification", err));
    }
    
    private void sendEmail(LoanDecisionMadeEvent event) {
        log.debug("Envoi email: decision={}, loanId={}", 
                  event.getDecision(), event.getLoanApplicationId());
        // Simulation délai réseau
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    private void sendSMS(LoanDecisionMadeEvent event) {
        log.debug("Envoi SMS: decision={}, loanId={}", 
                  event.getDecision(), event.getLoanApplicationId());
        // Simulation délai réseau
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
