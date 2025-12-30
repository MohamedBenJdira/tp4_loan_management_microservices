package com.bank.common.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RiskScoreCalculatedEvent extends DomainEvent {
    private String loanApplicationId;
    private double riskScore;
    private String riskLevel;
    private String recommendation;
    
    public RiskScoreCalculatedEvent(String loanApplicationId, double score, String level, String rec) {
        super(loanApplicationId);
        this.loanApplicationId = loanApplicationId;
        this.riskScore = score;
        this.riskLevel = level;
        this.recommendation = rec;
    }
}
