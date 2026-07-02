package com.bank.common.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class LoanDecisionMadeEvent extends DomainEvent {
    private String loanApplicationId;
    private String decision;
    private double approvedAmount;
    private String decisionReason;
    
    public LoanDecisionMadeEvent(String loanApplicationId, String decision, double amount, String reason) {
        super(loanApplicationId);
        this.loanApplicationId = loanApplicationId;
        this.decision = decision;
        this.approvedAmount = amount;
        this.decisionReason = reason;
    }
}
