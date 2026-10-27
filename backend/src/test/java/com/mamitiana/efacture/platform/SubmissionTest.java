package com.mamitiana.efacture.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mamitiana.efacture.common.BusinessRuleException;
import com.mamitiana.efacture.invoice.InvoiceFixtures;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class SubmissionTest {

    private static final Instant NOW = Instant.parse("2026-10-27T10:00:00Z");
    private static final SubmissionEvent.Source PLATFORM = SubmissionEvent.Source.PLATFORM;

    private static Submission submission() {
        Submission submission = new Submission(InvoiceFixtures.issuedInvoice(), NOW);
        submission.deposited("SIM-1", NOW);
        return submission;
    }

    @Test
    void startsQueuedThenDepositedWithThePlatformId() {
        Submission queued = new Submission(InvoiceFixtures.issuedInvoice(), NOW);
        assertThat(queued.getStatus()).isEqualTo(PlatformStatus.EN_COURS_ENVOI);
        assertThat(queued.getExternalId()).isNull();

        queued.deposited("SIM-1", NOW);

        assertThat(queued.getExternalId()).isEqualTo("SIM-1");
        assertThat(queued.getEvents()).extracting(SubmissionEvent::getStatus)
                .containsExactly(PlatformStatus.EN_COURS_ENVOI, PlatformStatus.DEPOSEE);
    }

    @Test
    void abandonedDeliveryFreesTheInvoiceForANewSubmission() {
        Submission queued = new Submission(InvoiceFixtures.issuedInvoice(), NOW);
        queued.deliveryAttemptFailed("503", NOW);
        queued.deliveryAbandoned("503", NOW);

        assertThat(queued.getStatus()).isEqualTo(PlatformStatus.ECHEC_ENVOI);
        assertThat(queued.getDeliveryAttempts()).isEqualTo(1);
        assertThat(PlatformStatus.allowingResubmission()).contains(PlatformStatus.ECHEC_ENVOI, PlatformStatus.REJETEE);
    }

    @Test
    void followsTheHappyPath() {
        Submission submission = submission();

        submission.apply(PlatformStatus.MISE_A_DISPOSITION, null, PLATFORM, NOW.plusSeconds(5));
        submission.apply(PlatformStatus.APPROUVEE, null, PLATFORM, NOW.plusSeconds(10));
        submission.apply(PlatformStatus.ENCAISSEE, null, SubmissionEvent.Source.SELLER, NOW.plusSeconds(15));

        assertThat(submission.getStatus().isFinal()).isTrue();
        assertThat(submission.getEvents()).extracting(SubmissionEvent::getStatus).containsExactly(
                PlatformStatus.EN_COURS_ENVOI, PlatformStatus.DEPOSEE, PlatformStatus.MISE_A_DISPOSITION, PlatformStatus.APPROUVEE, PlatformStatus.ENCAISSEE);
    }

    @Test
    void replayingTheSameStatusChangesNothing() {
        Submission submission = submission();
        submission.apply(PlatformStatus.MISE_A_DISPOSITION, null, PLATFORM, NOW);

        boolean changed = submission.apply(PlatformStatus.MISE_A_DISPOSITION, null, PLATFORM, NOW.plusSeconds(1));

        assertThat(changed).isFalse();
        assertThat(submission.getEvents()).hasSize(3);
    }

    @Test
    void refusesImpossibleTransitions() {
        Submission submission = submission();

        // A buyer cannot approve an invoice that has not been delivered yet.
        assertThatThrownBy(() -> submission.apply(PlatformStatus.APPROUVEE, null, PLATFORM, NOW))
                .isInstanceOf(BusinessRuleException.class);
        assertThat(submission.getStatus()).isEqualTo(PlatformStatus.DEPOSEE);
    }

    @ParameterizedTest
    @EnumSource(value = PlatformStatus.class, names = {"REJETEE", "REFUSEE", "ENCAISSEE"})
    void finalStatusesAreTheEndOfTheRoad(PlatformStatus status) {
        assertThat(status.isFinal()).isTrue();
        assertThat(status.isMandatory()).isTrue();
    }
}
