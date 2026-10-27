package com.mamitiana.efacture.platform;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.random.RandomGenerator;
import org.mustangproject.validator.ZUGFeRDValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Stand-in for a registered platform, for the demo. It behaves like one from the outside: it accepts
 * files, works asynchronously, really validates the Factur-X, then notifies statuses one step at a time.
 * Technical rejections and buyer refusals happen at configurable rates. State is kept in memory.
 */
@Component
public class SimulatedPlatform implements PlatformGateway {

    private static final Logger log = LoggerFactory.getLogger(SimulatedPlatform.class);

    static final List<String> TECHNICAL_REJECTIONS = List.of(
            "Destinataire introuvable dans l'annuaire des plateformes",
            "Doublon : une facture avec ce numéro a déjà été déposée");

    static final List<String> BUYER_REFUSALS = List.of(
            "Montant différent du bon de commande",
            "Prestation non reçue à ce jour",
            "Référence de commande manquante");

    private enum Stage {
        RECEIVED,
        DELIVERED
    }

    private record Pending(String invoiceNumber, byte[] file, Stage stage) {
    }

    private final Map<String, Pending> inbox = new ConcurrentHashMap<>();
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final RandomGenerator random;
    private final boolean validateFiles;
    private volatile double rejectionRate;
    private volatile double refusalRate;

    /**
     * @param validateFiles runs the full Factur-X validator on each file. It needs a few hundred MB of memory,
     *                      more than a 512 MB hosting tier allows next to the application, so the online demo
     *                      turns it off; it stays on locally and in CI.
     */
    public SimulatedPlatform(ApplicationEventPublisher events, Clock clock,
                             @Value("${app.platform.simulator.rejection-rate:0.1}") double rejectionRate,
                             @Value("${app.platform.simulator.refusal-rate:0.2}") double refusalRate,
                             @Value("${app.platform.simulator.validate-files:true}") boolean validateFiles) {
        this.events = events;
        this.clock = clock;
        this.random = new SplittableRandom();
        this.rejectionRate = rejectionRate;
        this.refusalRate = refusalRate;
        this.validateFiles = validateFiles;
    }

    /** Changes the scenario at runtime: 0 never happens, 1 always happens. Used by tests and demos. */
    public void setRates(double rejectionRate, double refusalRate) {
        this.rejectionRate = rejectionRate;
        this.refusalRate = refusalRate;
    }

    @Override
    public String submit(String invoiceNumber, byte[] facturX) {
        String externalId = "SIM-" + UUID.randomUUID();
        inbox.put(externalId, new Pending(invoiceNumber, facturX, Stage.RECEIVED));
        log.info("Simulated platform received {} as {}", invoiceNumber, externalId);
        return externalId;
    }

    @Override
    public void reportStatus(String externalId, PlatformStatus status) {
        // Once paid, the buyer has nothing left to decide.
        if (status == PlatformStatus.ENCAISSEE) {
            inbox.remove(externalId);
        }
    }

    /** Moves every pending file one step forward. Called on a schedule, or directly by tests. */
    public void tick() {
        for (Map.Entry<String, Pending> entry : Map.copyOf(inbox).entrySet()) {
            String externalId = entry.getKey();
            Pending pending = entry.getValue();
            switch (pending.stage()) {
                case RECEIVED -> {
                    String rejection = technicalCheck(pending);
                    if (rejection != null) {
                        inbox.remove(externalId);
                        notify(externalId, PlatformStatus.REJETEE, rejection);
                    } else {
                        inbox.put(externalId, new Pending(pending.invoiceNumber(), pending.file(), Stage.DELIVERED));
                        notify(externalId, PlatformStatus.MISE_A_DISPOSITION, null);
                    }
                }
                case DELIVERED -> {
                    inbox.remove(externalId);
                    if (random.nextDouble() < refusalRate) {
                        notify(externalId, PlatformStatus.REFUSEE, pick(BUYER_REFUSALS));
                    } else {
                        notify(externalId, PlatformStatus.APPROUVEE, null);
                    }
                }
            }
        }
    }

    /** Real validation first (a broken file is always rejected), then the simulated rejection rate. */
    private String technicalCheck(Pending pending) {
        if (validateFiles) {
            ZUGFeRDValidator validator = new ZUGFeRDValidator();
            validator.validate(pending.file(), pending.invoiceNumber() + ".pdf");
            if (!validator.wasCompletelyValid()) {
                return "Fichier Factur-X non conforme à la norme EN 16931";
            }
        }
        return random.nextDouble() < rejectionRate ? pick(TECHNICAL_REJECTIONS) : null;
    }

    private void notify(String externalId, PlatformStatus status, String reason) {
        try {
            events.publishEvent(new PlatformNotification(externalId, status, reason, clock.instant()));
        } catch (RuntimeException e) {
            log.error("Status {} for {} could not be applied", status, externalId, e);
        }
    }

    private String pick(List<String> reasons) {
        return reasons.get(random.nextInt(reasons.size()));
    }
}
