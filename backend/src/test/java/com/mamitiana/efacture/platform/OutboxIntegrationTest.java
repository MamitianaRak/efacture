package com.mamitiana.efacture.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.mamitiana.efacture.IntegrationTest;
import com.mamitiana.efacture.outbox.OutboxMessage;
import com.mamitiana.efacture.outbox.OutboxRelay;
import com.mamitiana.efacture.outbox.OutboxRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Delivery guarantees of the outbox between eFacture and the platform, under outages and concurrency. */
@WithMockUser(roles = "MANAGER")
class OutboxIntegrationTest extends IntegrationTest {

    private static final Instant NOW = TODAY.atTime(10, 0).atZone(ZoneId.of("Europe/Paris")).toInstant();

    @Autowired
    private MockMvc mvc;
    @Autowired
    private SimulatedPlatform platform;
    @Autowired
    private OutboxRelay relay;
    @Autowired
    private OutboxRepository outbox;

    private int customerId;

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request) {
        return request.contentType(MediaType.APPLICATION_JSON);
    }

    @BeforeEach
    void seller() throws Exception {
        platform.setRates(0, 0);
        mvc.perform(json(put("/api/company")).content("""
                {"legalName": "Atelier Dupont SARL", "siren": "404833048", "vatNumber": "FR83404833048",
                 "street": "1 rue de la Paix", "postalCode": "75002", "city": "Paris", "paymentTerms": "30 jours"}
                """));
        String customer = mvc.perform(json(post("/api/customers")).content("""
                        {"legalName": "Boulangerie Martin", "siren": "552100554", "street": "5 place du Marché",
                         "postalCode": "77000", "city": "Melun"}
                        """))
                .andReturn().getResponse().getContentAsString();
        customerId = JsonPath.read(customer, "$.id");
    }

    private int issuedInvoice() throws Exception {
        String invoice = mvc.perform(json(post("/api/invoices")).content("""
                        {"customerId": %d, "dueDate": "%s",
                         "lines": [{"description": "Site", "quantity": 1, "unitPrice": 1000, "vatRate": 20}]}
                        """.formatted(customerId, TODAY.plusDays(30))))
                .andReturn().getResponse().getContentAsString();
        int id = JsonPath.read(invoice, "$.id");
        mvc.perform(post("/api/invoices/{id}/issue", id)).andExpect(status().isOk());
        return id;
    }

    private int submitted() throws Exception {
        int id = issuedInvoice();
        mvc.perform(post("/api/invoices/{id}/submission", id)).andExpect(status().isCreated());
        return id;
    }

    private String submission(int invoiceId) throws Exception {
        return mvc.perform(get("/api/invoices/{id}/submission", invoiceId)).andReturn().getResponse().getContentAsString();
    }

    private String statusOf(int invoiceId) throws Exception {
        return JsonPath.read(submission(invoiceId), "$.status");
    }

    @Test
    void retriesWhileThePlatformIsDownThenDelivers() throws Exception {
        int invoiceId = submitted();
        platform.setOutage(SimulatedPlatform.Outage.UNAVAILABLE);

        relay.tickAt(NOW);
        String body = submission(invoiceId);
        assertThat((String) JsonPath.read(body, "$.status")).isEqualTo("EN_COURS_ENVOI");
        assertThat((Integer) JsonPath.read(body, "$.deliveryAttempts")).isEqualTo(1);
        assertThat((String) JsonPath.read(body, "$.lastDeliveryError")).contains("indisponible");

        // The retry is not due yet: nothing is sent before its time.
        assertThat(relay.tickAt(NOW.plusSeconds(5))).isZero();

        platform.setOutage(SimulatedPlatform.Outage.NONE);
        relay.tickAt(NOW.plusSeconds(11));

        assertThat(statusOf(invoiceId)).isEqualTo("DEPOSEE");
        assertThat(outbox.countByStatus(OutboxMessage.Status.SENT)).isEqualTo(1);
    }

    @Test
    void aLostAnswerIsRetriedWithoutDuplicatingTheFile() throws Exception {
        int invoiceId = submitted();
        // The platform stores the file, but its answer never comes back.
        platform.setOutage(SimulatedPlatform.Outage.RESPONSE_LOST);
        relay.tickAt(NOW);
        assertThat(statusOf(invoiceId)).isEqualTo("EN_COURS_ENVOI");

        platform.setOutage(SimulatedPlatform.Outage.NONE);
        relay.tickAt(NOW.plusSeconds(11));

        // Two calls, one stored file: the idempotency key made the retry harmless.
        assertThat(platform.submitCalls()).isEqualTo(2);
        assertThat(platform.storedFiles()).isEqualTo(1);
        assertThat(statusOf(invoiceId)).isEqualTo("DEPOSEE");
    }

    @Test
    void givesUpAfterTheLastAttemptAndAllowsANewSubmission() throws Exception {
        int invoiceId = submitted();
        platform.setOutage(SimulatedPlatform.Outage.UNAVAILABLE);

        Instant at = NOW;
        for (int attempt = 1; attempt <= 5; attempt++) {
            relay.tickAt(at);
            at = at.plus(Duration.ofHours(1));
        }

        String body = submission(invoiceId);
        assertThat((String) JsonPath.read(body, "$.status")).isEqualTo("ECHEC_ENVOI");
        assertThat((Integer) JsonPath.read(body, "$.deliveryAttempts")).isEqualTo(5);
        assertThat(outbox.countByStatus(OutboxMessage.Status.FAILED)).isEqualTo(1);
        assertThat(relay.tickAt(at.plus(Duration.ofDays(1)))).as("an abandoned message is never retried").isZero();

        platform.setOutage(SimulatedPlatform.Outage.NONE);
        mvc.perform(post("/api/invoices/{id}/submission", invoiceId)).andExpect(status().isCreated());
    }

    @Test
    void concurrentRelaysSendEachMessageOnce() throws Exception {
        List<Integer> invoices = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            invoices.add(submitted());
        }

        try (ExecutorService pool = Executors.newFixedThreadPool(4)) {
            List<Callable<Integer>> relays = List.of(() -> relay.tickAt(NOW), () -> relay.tickAt(NOW),
                    () -> relay.tickAt(NOW), () -> relay.tickAt(NOW));
            int processed = 0;
            for (var result : pool.invokeAll(relays)) {
                processed += result.get();
            }
            assertThat(processed).isEqualTo(10);
        }

        // SKIP LOCKED: four relays at once, still exactly one call per message.
        assertThat(platform.submitCalls()).isEqualTo(10);
        for (int invoiceId : invoices) {
            assertThat(statusOf(invoiceId)).isEqualTo("DEPOSEE");
        }
    }

    @Test
    void aRefusedSubmissionWritesNoOutboxMessage() throws Exception {
        int invoiceId = submitted();

        mvc.perform(post("/api/invoices/{id}/submission", invoiceId)).andExpect(status().isConflict());

        // Written in the request's transaction: a refused request leaves nothing to send.
        assertThat(outbox.count()).isEqualTo(1);
    }

    @Test
    void thePaymentIsForwardedThroughTheOutboxToo() throws Exception {
        int invoiceId = submitted();
        relay.tickAt(NOW);
        platform.tick();

        mvc.perform(post("/api/invoices/{id}/submission/payment", invoiceId)).andExpect(status().isOk());
        assertThat(outbox.countByStatus(OutboxMessage.Status.PENDING)).isEqualTo(1);

        relay.tickAt(NOW);
        assertThat(outbox.countByStatus(OutboxMessage.Status.SENT)).isEqualTo(2);
        assertThat(statusOf(invoiceId)).isEqualTo("ENCAISSEE");
    }
}
