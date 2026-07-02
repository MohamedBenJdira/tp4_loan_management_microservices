package com.bank.common.event;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, property = "@class")
public abstract class DomainEvent implements Serializable {
    private String eventId;
    private LocalDateTime timestamp;
    private String aggregateId;
    
    public DomainEvent(String aggregateId) {
        this.aggregateId = aggregateId;
        this.timestamp = LocalDateTime.now();
        this.eventId = java.util.UUID.randomUUID().toString();
    }
}
