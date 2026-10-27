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
        return new Submission(InvoiceFixtures.issuedInvoice(), "SIM-1", NOW);
    }

    @Test
    void startsDepositedWithOneEvent() {
        Submission submission = submission();

        assertThat(submission.getStatus()).isEqualTo(PlatformStatus.DEPOSEE);
        assertThat(submission.getEvents()).extracting(SubmissionEvent::getStatus).containsExactly(PlatformStatus.DEPOSEE);
    }

    @Test
    void followsTheHappyPath() {
        Submission submission = submission();

        submission.apply(PlatformStatus.MISE_A_DISPOSITION, null, PLATFORM, NOW.plusSeconds(5));
        submission.apply(PlatformStatus.APPROUVEE, null, PLATFORM, NOW.plusSeconds(10));
        submission.apply(PlatformStatus.ENCAISSEE, null, SubmissionEvent.Source.SELLER, NOW.plusSeconds(15));

        assertThat(submission.getStatus().isFinal()).isTrue();
        assertThat(submission.getEvents()).extracting(SubmissionEvent::getStatus).containsExactly(
                PlatformStatus.DEPOSEE, PlatformStatus.MISE_A_DISPOSITION, PlatformStatus.APPROUVEE, PlatformStatus.ENCAISSEE);
    }

    @Test
    void replayingTheSameStatusChangesNothing() {
        Submission submission = submission();
        submission.apply(PlatformStatus.MISE_A_DISPOSITION, null, PLATFORM, NOW);

        boolean changed = submission.apply(PlatformStatus.MISE_A_DISPOSITION, null, PLATFORM, NOW.plusSeconds(1));

        assertThat(changed).isFalse();
        assertThat(submission.getEvents()).hasSize(2);
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
