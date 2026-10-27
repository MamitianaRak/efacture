package com.mamitiana.efacture.common;

import java.util.List;

/** Thrown when an invoice cannot be issued because legal mentions are missing. */
public class MissingMentionsException extends RuntimeException {

    private final List<String> violations;

    public MissingMentionsException(List<String> violations) {
        super("La facture ne peut pas être émise : mentions obligatoires manquantes");
        this.violations = List.copyOf(violations);
    }

    public List<String> getViolations() {
        return violations;
    }
}
