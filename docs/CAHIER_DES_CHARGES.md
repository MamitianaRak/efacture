# eFacture PME : cahier des charges

## 1. Le problème

En France, la facturation électronique devient obligatoire entre entreprises assujetties à la TVA :

- depuis le **1er septembre 2026**, toutes les entreprises doivent pouvoir **recevoir** des factures électroniques ;
- au **1er septembre 2027**, les TPE et PME devront aussi les **émettre**.

Les factures ne s'envoient plus par simple PDF : elles passent par une **plateforme agréée** (PA) immatriculée par l'administration fiscale, dans un format structuré comme **Factur-X** (un PDF lisible avec un fichier XML intégré que les logiciels lisent automatiquement).

Beaucoup de petites entreprises facturent encore avec Word ou Excel. eFacture PME leur permet de faire des factures conformes, de suivre leur statut et de recevoir celles de leurs fournisseurs.

> **Projet de démonstration.** La plateforme agréée est **simulée** : aucune facture n'est réellement transmise à l'administration. L'interface et le README l'indiquent clairement.

## 2. Utilisateurs

| Rôle | Ce qu'il fait |
|---|---|
| Gérant | Gère les clients, crée et émet les factures, consulte le tableau de bord |
| Comptable | Consulte les factures, émet les avoirs, suit les paiements et les factures fournisseurs |

## 3. Règles métier

1. **Numérotation** : chronologique, continue, sans trou ni doublon, une série par type et par année (`F-2026-00001` pour les factures, `AV-2026-00001` pour les avoirs). Le numéro est attribué **à l'émission**, jamais au brouillon : un brouillon supprimé ne laisse donc pas de trou.
2. **Inaltérabilité** : une facture émise ne se modifie plus et ne se supprime plus. Une erreur se corrige par un **avoir** qui fait référence à la facture d'origine.
3. **Mentions obligatoires** vérifiées avant l'émission : identité, adresse, SIREN et numéro de TVA du vendeur ; nom, adresse et SIREN du client ; date d'émission et date d'échéance ; conditions de paiement ; au moins une ligne. L'émission est refusée avec la liste précise de ce qui manque.
4. **Montants** : calcul en `BigDecimal`, arrondi au centime. La TVA est calculée par taux sur le total des lignes de ce taux (méthode de la norme européenne EN 16931). Taux acceptés : 20 %, 10 %, 5,5 %, 2,1 % et 0 %.
5. **Contrôles d'identifiants** : SIREN vérifié par sa clé de Luhn, numéro de TVA français vérifié par sa clé.

## 4. Cycle de vie d'une facture

```
BROUILLON ──émettre──▶ ÉMISE ──déposer──▶ DÉPOSÉE ──▶ ACCEPTÉE ──▶ PAYÉE
                                              │
                                              └──▶ REFUSÉE (motif)
```

Les états après ÉMISE sont renvoyés par la plateforme agréée simulée (étape 2).

## 5. Architecture

```
Angular (front) ──REST/JSON──▶ Spring Boot (API) ──▶ PostgreSQL
                                     │
                                     ├──▶ Mustangproject (PDF Factur-X)
                                     └──▶ Kafka ──▶ plateforme agréée simulée
```

| Couche | Technologies |
|---|---|
| Back-end | Java 25, Spring Boot 4, Spring Data JPA, Bean Validation, Flyway |
| Base de données | PostgreSQL |
| Factur-X | Mustangproject (création et validation) |
| Sécurité | Spring Security, JWT |
| Messagerie | Kafka |
| Front-end | Angular, TypeScript |
| Tests | JUnit 5, Mockito, Testcontainers, MockMvc |
| Livraison | Docker, GitHub Actions, Render |

## 6. Étapes

### Étape 1 : facturer correctement
- Fiche de l'entreprise émettrice, clients (contrôle du SIREN et de la TVA)
- Factures en brouillon, émission avec numérotation, avoirs
- Contrôle des mentions obligatoires, calcul des montants
- Génération du PDF **Factur-X** et validation
- Tests JUnit, Mockito et Testcontainers
- Front Angular : clients, factures, émission

### Étape 2 : la plateforme agréée simulée
- Dépôt de la facture, statuts (déposée, refusée, acceptée, payée) avec des refus simulés
- Spring Security et JWT, rôles gérant et comptable
- Tableau de bord : impayés, retards, chiffre d'affaires

### Étape 3 : réception et production
- Changements de statut publiés dans Kafka
- Réception des factures fournisseurs : import Factur-X et lecture du XML
- Option IA : lecture d'une facture fournisseur scannée par un LLM, en JSON structuré
- Docker, CI GitHub Actions, déploiement, README avec schéma

## 7. Hors périmètre

- Connexion à une vraie plateforme agréée (elle demande une immatriculation)
- Multi-entreprises : une instance gère une seule entreprise émettrice
- E-reporting (transmission des données de vente aux particuliers)
