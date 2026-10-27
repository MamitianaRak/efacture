package com.mamitiana.efacture.platform;

import java.util.EnumSet;
import java.util.Set;

/**
 * Lifecycle of an e-invoice on the registered platform. DEPOSEE, REJETEE, REFUSEE and ENCAISSEE are the
 * four statuses every platform must handle under the French reform; the others are common intermediate steps.
 */
public enum PlatformStatus {
    /** Received by the seller's platform. Mandatory. */
    DEPOSEE(true),
    /** Technical rejection by the platform (invalid format or data). Mandatory, final: fix and resubmit. */
    REJETEE(true),
    /** Delivered to the buyer. */
    MISE_A_DISPOSITION(false),
    /** Accepted by the buyer. */
    APPROUVEE(false),
    /** Business refusal by the buyer, with a reason. Mandatory, final: issue a credit note. */
    REFUSEE(true),
    /** Payment received, declared by the seller. Mandatory, final. */
    ENCAISSEE(true);

    private final boolean mandatory;

    PlatformStatus(boolean mandatory) {
        this.mandatory = mandatory;
    }

    public boolean isMandatory() {
        return mandatory;
    }

    public boolean isFinal() {
        return nextStatuses().isEmpty();
    }

    public Set<PlatformStatus> nextStatuses() {
        return switch (this) {
            case DEPOSEE -> EnumSet.of(REJETEE, MISE_A_DISPOSITION);
            case MISE_A_DISPOSITION -> EnumSet.of(APPROUVEE, REFUSEE, ENCAISSEE);
            case APPROUVEE -> EnumSet.of(ENCAISSEE);
            case REJETEE, REFUSEE, ENCAISSEE -> EnumSet.noneOf(PlatformStatus.class);
        };
    }

    public boolean canMoveTo(PlatformStatus next) {
        return nextStatuses().contains(next);
    }
}
