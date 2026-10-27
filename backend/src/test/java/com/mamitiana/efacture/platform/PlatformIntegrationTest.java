package com.mamitiana.efacture.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.mamitiana.efacture.IntegrationTest;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Submission to the simulated platform, driven step by step with tick() instead of the scheduler. */
@WithMockUser(roles = "MANAGER")
class PlatformIntegrationTest extends IntegrationTest {

    private static final String DUE_DATE = TODAY.plusDays(30).toString();

    @Autowired
    private MockMvc mvc;
    @Autowired
    private SimulatedPlatform platform;
    @Autowired
    private ApplicationEventPublisher events;

    @AfterEach
    void resetScenario() {
        platform.setRates(0.1, 0.2);
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request) {
        return request.contentType(MediaType.APPLICATION_JSON);
    }

    private int draft() throws Exception {
        mvc.perform(json(put("/api/company")).content("""
                {"legalName": "Atelier Dupont SARL", "siren": "404833048", "vatNumber": "FR83404833048",
                 "street": "1 rue de la Paix", "postalCode": "75002", "city": "Paris", "paymentTerms": "30 jours"}
                """)).andExpect(status().isOk());
        String customer = mvc.perform(json(post("/api/customers")).content("""
                        {"legalName": "Boulangerie Martin", "siren": "552100554", "street": "5 place du Marché",
                         "postalCode": "77000", "city": "Melun"}
                        """))
                .andReturn().getResponse().getContentAsString();
        String invoice = mvc.perform(json(post("/api/invoices")).content("""
                        {"customerId": %d, "dueDate": "%s",
                         "lines": [{"description": "Site", "quantity": 1, "unitPrice": 1000, "vatRate": 20}]}
                        """.formatted((int) JsonPath.read(customer, "$.id"), DUE_DATE)))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(invoice, "$.id");
    }

    private int issuedInvoice() throws Exception {
        int id = draft();
        mvc.perform(post("/api/invoices/{id}/issue", id)).andExpect(status().isOk());
        return id;
    }

    private String statusOf(int invoiceId) throws Exception {
        String body = mvc.perform(get("/api/invoices/{id}/submission", invoiceId))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.status");
    }

    @Test
    void approvedThenPaid() throws Exception {
        platform.setRates(0, 0);
        int invoiceId = issuedInvoice();

        mvc.perform(post("/api/invoices/{id}/submission", invoiceId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DEPOSEE"))
                .andExpect(jsonPath("$.simulated").value(true));

        platform.tick();
        assertThat(statusOf(invoiceId)).isEqualTo("MISE_A_DISPOSITION");
        platform.tick();
        assertThat(statusOf(invoiceId)).isEqualTo("APPROUVEE");

        mvc.perform(post("/api/invoices/{id}/submission/payment", invoiceId).with(accountant()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ENCAISSEE"))
                .andExpect(jsonPath("$.finalStatus").value(true))
                .andExpect(jsonPath("$.events[*].status")
                        .value(org.hamcrest.Matchers.contains("DEPOSEE", "MISE_A_DISPOSITION", "APPROUVEE", "ENCAISSEE")));

        mvc.perform(get("/api/submissions/latest-statuses"))
                .andExpect(jsonPath("$['%d']".formatted(invoiceId)).value("ENCAISSEE"));
    }

    @Test
    void cannotSubmitTwiceButCanResubmitAfterATechnicalRejection() throws Exception {
        platform.setRates(1, 0);
        int invoiceId = issuedInvoice();

        mvc.perform(post("/api/invoices/{id}/submission", invoiceId)).andExpect(status().isCreated());
        mvc.perform(post("/api/invoices/{id}/submission", invoiceId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("déjà déposée")));

        platform.tick();
        mvc.perform(get("/api/invoices/{id}/submission", invoiceId))
                .andExpect(jsonPath("$.status").value("REJETEE"))
                .andExpect(jsonPath("$.statusReason").isNotEmpty());

        platform.setRates(0, 0);
        mvc.perform(post("/api/invoices/{id}/submission", invoiceId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DEPOSEE"));
    }

    @Test
    void refusedInvoiceCannotBePaid() throws Exception {
        platform.setRates(0, 1);
        int invoiceId = issuedInvoice();
        mvc.perform(post("/api/invoices/{id}/submission", invoiceId)).andExpect(status().isCreated());

        platform.tick();
        platform.tick();

        mvc.perform(get("/api/invoices/{id}/submission", invoiceId))
                .andExpect(jsonPath("$.status").value("REFUSEE"))
                .andExpect(jsonPath("$.statusReason").isNotEmpty());
        mvc.perform(post("/api/invoices/{id}/submission/payment", invoiceId)).andExpect(status().isConflict());
    }

    @Test
    void duplicateNotificationIsAppliedOnce() throws Exception {
        platform.setRates(0, 0);
        int invoiceId = issuedInvoice();
        String body = mvc.perform(post("/api/invoices/{id}/submission", invoiceId))
                .andReturn().getResponse().getContentAsString();
        String externalId = JsonPath.read(body, "$.externalId");

        PlatformNotification delivered = new PlatformNotification(externalId, PlatformStatus.MISE_A_DISPOSITION, null, Instant.now());
        events.publishEvent(delivered);
        events.publishEvent(delivered);

        mvc.perform(get("/api/invoices/{id}/submission", invoiceId))
                .andExpect(jsonPath("$.events.length()").value(2));
    }

    @Test
    void draftsAndAccountantsCannotSubmit() throws Exception {
        int draftId = draft();
        mvc.perform(post("/api/invoices/{id}/submission", draftId)).andExpect(status().isConflict());

        int invoiceId = issuedInvoice();
        mvc.perform(post("/api/invoices/{id}/submission", invoiceId).with(accountant()))
                .andExpect(status().isForbidden());
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor accountant() {
        return org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                .user("comptable@demo.fr").roles("ACCOUNTANT");
    }
}
