package com.mamitiana.efacture.ai;

/** Every candidate of a lane failed or is switched off. */
public class LlmUnavailableException extends RuntimeException {

    public LlmUnavailableException(LlmLane lane, Throwable lastError) {
        super("Aucun modèle d'IA disponible pour la voie " + lane + " : réessayez dans quelques instants", lastError);
    }
}
