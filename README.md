# Architecture Microservices Réactive - Gestion des Prêts Bancaires

## Vue d'ensemble

Ce mini-projet démontre une architecture microservices réactive pour gérer des demandes de prêts bancaires. Les services communiquent de manière asynchrone et réactive, en utilisant des patterns comme l'event-driven et la programmation réactive.

## Architecture

### Services Implémentés

1. **Commercial Service**
   - Calcule le scoring commercial basé sur le profil client
   - Évalue la capacité financière du client
   - Publie un événement `CommercialScoreCalculatedEvent`

2. **Risk Management Service**
   - Analyse le risque de crédit
   - Applique des modèles de notation (simulation ML)
   - Publie un événement `RiskScoreCalculatedEvent`

3. **Credit Service**
   - Orchestration du processus de décision
   - Combine les scores commercial et de risque
   - Prend la décision finale (APPROVED/REJECTED/APPROVED_WITH_CONDITIONS)
   - Publie `LoanDecisionMadeEvent`

4. **Notification Service**
   - Reçoit les décisions de crédit
   - Envoie les notifications (email, SMS)
   - Non-bloquant et asynchrone

5. **OCR Service**
   - Extraction de données depuis documents
   - Placeholder pour la démonstration

## Communication entre Services

### Pattern: Event-Driven avec Reactive Streams

```
Commercial Service          Risk Service
    │                           │
    └──────→ Decision Engine ←──┘
             │
             ├──→ Notification Service
             └──→ Event Bus
```

### Flux Réactif

1. **Requête initiale** → Credit Service
2. **Appels parallèles réactifs** → Commercial Service + Risk Service
3. **Combinaison des résultats** → `Mono.zip()`
4. **Prise de décision** → Règles métier
5. **Notification** → Service asynchrone

## Technologies Utilisées

- **Java 17**: Langage de programmation
- **Spring Boot 3.1.5**: Framework applicatif
- **Spring WebFlux**: Programmation réactive
- **Project Reactor**: Reactive streams (`Mono`, `Flux`)
- **Maven**: Gestion des dépendances

## Installation et Exécution

### Prérequis

- Java 17+
- Maven 3.8+

### Build

```bash
mvn clean install
```

### Exécution de la démo

```bash
java -cp implementation/target/classes:. com.bank.demo.LoanApplicationDemo
```

## Choix Technologiques

### Pourquoi Spring WebFlux?

1. **Non-bloquant**: Pas de threads bloqués en attente de I/O
2. **Scalabilité**: Gère des milliers de connexions avec peu de ressources
3. **Intégration Kafka**: Support natif pour event streaming
4. **Résilience**: Intégration facile avec Resilience4j/Circuit Breaker

### Pourquoi Project Reactor?

1. **Mono & Flux**: Abstractions pour traiter un/plusieurs éléments
2. **Composition réactive**: `zip`, `flatMap`, `merge` pour orchestrer
3. **Gestion d'erreurs**: `doOnError`, `onErrorResume` intégrés
4. **Backpressure**: Gestion de la pression automatique

## Exemples de Flux

### Cas 1: Client Excellent
- Revenu: 5500€ | Emploi: 8 ans
- Score Commercial: EXCELLENT
- Score Risque: LOW
- Résultat: **APPROVED** (montant complet)

### Cas 2: Client Moyen
- Revenu: 3200€ | Emploi: 3 ans
- Score Commercial: GOOD
- Score Risque: MEDIUM
- Résultat: **APPROVED** (90% du montant)

### Cas 3: Client à Risque
- Revenu: 2800€ | Emploi: 1 an
- Score Commercial: POOR
- Score Risque: HIGH
- Résultat: **REJECTED**

## Points Clés de l'Implémentation

### Programmation Réactive
```java
Mono.zip(commercialScoreMono, riskScoreMono)
    .flatMap(scores -> makeDecision(scores))
    .then(notifyDecision())
```

### Event-Driven
```java
commercialService.evaluateScore()
    .doOnNext(event -> eventBus.publish(event))
```

### Non-bloquant
```java
return Mono.fromCallable(() -> heavyCalculation())
    .subscribeOn(Schedulers.boundedElastic())
```

## Avantages de cette Architecture

| Aspect | Avantage |
|--------|----------|
| **Scalabilité** | Services indépendants, scaling horizontal facile |
| **Résilience** | Défaillance d'un service ≠ système complet down |
| **Performance** | Traitement réactif, pas de threads bloqués |
| **Maintenabilité** | Séparation des responsabilités claire |
| **Testabilité** | Services faciles à tester isolément |

## Extensions Possibles

1. **Ajout de Kafka** pour l'event bus réel
2. **Hystrix/Resilience4j** pour circuit breaker
3. **OpenTelemetry** pour la monitoring distribuée
4. **Kubernetes** pour l'orchestration
5. **API Gateway** (Spring Cloud Gateway)
6. **Service Registry** (Eureka/Consul)

---

**Auteur**: TP4 - Architectures Réactives et Microservices
**Date**: 2025-2026
