package com.mamitiana.efacture.invoice;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.mamitiana.efacture.IntegrationTest;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Business flow as the manager; role rules are covered by SecurityIntegrationTest. */
@WithMockUser(roles = "MANAGER")
class InvoiceApiIntegrationTest extends IntegrationTest {

    private static final int YEAR = LocalDate.now(ZoneId.of("Europe/Paris")).getYear();
    private static final String DUE_DATE = LocalDate.now(ZoneId.of("Europe/Paris")).plusDays(30).toString();

    @Autowired
    private MockMvc mvc;

    @BeforeEach
    void createSeller() throws Exception {
        send(put("/api/company"), """
                {"legalName": "Atelier Dupont SARL", "siren": "404833048", "vatNumber": "FR83404833048",
                 "street": "1 rue de la Paix", "postalCode": "75002", "city": "Paris",
                 "paymentTerms": "Paiement à 30 jours. Pénalités : 3 fois le taux légal. Indemnité forfaitaire : 40 EUR."}
                """).andExpect(status().isOk());
    }

    private ResultActions send(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
                               String json) throws Exception {
        return mvc.perform(request.contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private int createCustomer(String sirenJson) throws Exception {
        String body = send(post("/api/customers"), """
                {"legalName": "Boulangerie Martin", "siren": %s, "street": "5 place du Marché",
                 "postalCode": "77000", "city": "Melun"}
                """.formatted(sirenJson))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private int createDraft(int customerId) throws Exception {
        String body = send(post("/api/invoices"), """
                {"customerId": %d, "dueDate": "%s", "lines": [
                  {"description": "Site vitrine", "quantity": 1, "unitPrice": 1500.00, "vatRate": 20},
                  {"description": "Formation", "quantity": 2, "unitPrice": 250.00, "vatRate": 20}]}
                """.formatted(customerId, DUE_DATE))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.number").doesNotExist())
                .andExpect(jsonPath("$.totalNet").value(2000.00))
                .andExpect(jsonPath("$.totalGross").value(2400.00))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    @Test
    void fullLifecycleFromDraftToCreditNote() throws Exception {
        int invoiceId = createDraft(createCustomer("\"552100554\""));

        mvc.perform(post("/api/invoices/{id}/issue", invoiceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ISSUED"))
                .andExpect(jsonPath("$.number").value("F-%d-00001".formatted(YEAR)));

        byte[] pdf = mvc.perform(get("/api/invoices/{id}/facturx", invoiceId))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("F-%d-00001.pdf".formatted(YEAR))))
                .andReturn().getResponse().getContentAsByteArray();
        org.assertj.core.api.Assertions.assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");

        // An issued invoice is frozen.
        send(put("/api/invoices/{id}", invoiceId), """
                {"customerId": 1, "dueDate": "%s", "lines": []}
                """.formatted(DUE_DATE)).andExpect(status().isConflict());
        mvc.perform(delete("/api/invoices/{id}", invoiceId)).andExpect(status().isConflict());

        // Corrections go through a credit note, numbered in its own series.
        String creditNote = mvc.perform(post("/api/invoices/{id}/credit-note", invoiceId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("CREDIT_NOTE"))
                .andExpect(jsonPath("$.originalInvoiceNumber").value("F-%d-00001".formatted(YEAR)))
                .andExpect(jsonPath("$.totalGross").value(2400.00))
                .andReturn().getResponse().getContentAsString();
        int creditNoteId = JsonPath.read(creditNote, "$.id");

        mvc.perform(post("/api/invoices/{id}/issue", creditNoteId))
                .andExpect(jsonPath("$.number").value("AV-%d-00001".formatted(YEAR)));
        mvc.perform(post("/api/invoices/{id}/credit-note", invoiceId)).andExpect(status().isConflict());
    }

    @Test
    void incompleteInvoiceIsRefusedWithTheListOfMissingMentions() throws Exception {
        int invoiceId = createDraft(createCustomer("null"));

        mvc.perform(post("/api/invoices/{id}/issue", invoiceId))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.violations", hasSize(1)))
                .andExpect(jsonPath("$.violations[0]").value("Client : SIREN (obligatoire en facturation électronique)"));

        // The refused attempt consumed no number.
        int completeId = createDraft(createCustomer("\"552100554\""));
        mvc.perform(post("/api/invoices/{id}/issue", completeId))
                .andExpect(jsonPath("$.number").value("F-%d-00001".formatted(YEAR)));
    }

    @Test
    void rejectsInvalidInput() throws Exception {
        send(post("/api/customers"), """
                {"legalName": "Client", "siren": "123456789", "street": "rue", "postalCode": "77000", "city": "Melun"}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", hasItem(org.hamcrest.Matchers.startsWith("siren"))));

        int customerId = createCustomer("\"552100554\"");
        send(post("/api/invoices"), """
                {"customerId": %d, "lines": [{"description": "x", "quantity": 1, "unitPrice": 10, "vatRate": 19.6}]}
                """.formatted(customerId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0]").value(org.hamcrest.Matchers.containsString("TVA")));
    }
}
