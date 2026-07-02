package com.bank.common.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class CommercialScoreCalculatedEvent extends DomainEvent {
    private String loanApplicationId;
    private double commercialScore;
    private String commercialRating;
    private String details;
    
    public CommercialScoreCalculatedEvent(String loanApplicationId, double score, String rating, String details) {
        super(loanApplicationId);
        this.loanApplicationId = loanApplicationId;
        this.commercialScore = score;
        this.commercialRating = rating;
        this.details = details;
    }
}
