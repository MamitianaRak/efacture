package com.mamitiana.efacture.dashboard;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.mamitiana.efacture.IntegrationTest;
import com.mamitiana.efacture.platform.SimulatedPlatform;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@WithMockUser(roles = "MANAGER")
class DashboardIntegrationTest extends IntegrationTest {


    @Autowired
    private MockMvc mvc;
    @Autowired
    private SimulatedPlatform platform;

    private int customerId;

    @AfterEach
    void resetScenario() {
        platform.setRates(0.1, 0.2);
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request) {
        return request.contentType(MediaType.APPLICATION_JSON);
    }

    private int draft(int unitPrice) throws Exception {
        String body = mvc.perform(json(post("/api/invoices")).content("""
                        {"customerId": %d, "dueDate": "%s",
                         "lines": [{"description": "Prestation", "quantity": 1, "unitPrice": %d, "vatRate": 20}]}
                        """.formatted(customerId, TODAY.plusDays(30), unitPrice)))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private int issued(int unitPrice) throws Exception {
        int id = draft(unitPrice);
        mvc.perform(post("/api/invoices/{id}/issue", id)).andExpect(status().isOk());
        return id;
    }

    private void submit(int invoiceId) throws Exception {
        mvc.perform(post("/api/invoices/{id}/submission", invoiceId)).andExpect(status().isCreated());
    }

    @Test
    void computesTheYearFiguresAndWhatNeedsAttention() throws Exception {
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

        int paid = issued(1000);       // 1 200 TTC, approved then paid
        int late = issued(500);        //   600 TTC, never submitted, due 10 days ago
        int refused = issued(200);     //   240 TTC, refused by the buyer
        int cancelled = issued(300);   //   360 TTC, cancelled by a credit note
        int rejected = issued(100);    //   120 TTC, technically rejected by the platform
        draft(50);                     //   a draft, outside every figure but the draft count

        String creditNote = mvc.perform(post("/api/invoices/{id}/credit-note", cancelled))
                .andReturn().getResponse().getContentAsString();
        mvc.perform(post("/api/invoices/{id}/issue", (int) JsonPath.read(creditNote, "$.id")));

        // Due dates cannot precede issuance through the API: move this one back to simulate time passing.
        jdbc.sql("UPDATE invoice SET due_date = :due WHERE id = :id")
                .param("due", TODAY.minusDays(10)).param("id", late).update();

        platform.setRates(0, 0);
        submit(paid);
        platform.tick();
        platform.tick();
        mvc.perform(post("/api/invoices/{id}/submission/payment", paid)).andExpect(status().isOk());

        platform.setRates(0, 1);
        submit(refused);
        platform.tick();
        platform.tick();

        platform.setRates(1, 0);
        submit(rejected);
        platform.tick();

        mvc.perform(get("/api/dashboard").with(
                        org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                                .user("comptable@demo.fr").roles("ACCOUNTANT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.year").value(TODAY.getYear()))
                // 1000 + 500 + 200 + 300 - 300 (credit note) + 100
                .andExpect(jsonPath("$.revenueNet").value(1800.0))
                .andExpect(jsonPath("$.monthlyRevenue.length()").value(12))
                .andExpect(jsonPath("$.monthlyRevenue[%d].revenueNet".formatted(TODAY.getMonthValue() - 1)).value(1800.0))
                .andExpect(jsonPath("$.collectedGross").value(1200.0))
                // Still to collect: the late invoice and the rejected one (it can be submitted again).
                .andExpect(jsonPath("$.receivable.count").value(2))
                .andExpect(jsonPath("$.receivable.totalGross").value(720.0))
                .andExpect(jsonPath("$.overdue.count").value(1))
                .andExpect(jsonPath("$.overdue.totalGross").value(600.0))
                .andExpect(jsonPath("$.overdueInvoices[0].id").value(late))
                .andExpect(jsonPath("$.overdueInvoices[0].daysLate").value(10))
                .andExpect(jsonPath("$.actions[*].type")
                        .value(containsInAnyOrder("REFUSED_WITHOUT_CREDIT_NOTE", "REJECTED_SUBMISSION")))
                .andExpect(jsonPath("$.actions[?(@.type == 'REFUSED_WITHOUT_CREDIT_NOTE')].invoiceId").value(contains(refused)))
                .andExpect(jsonPath("$.draftCount").value(1));
    }

    @Test
    void emptyYearIsAllZeros() throws Exception {
        mvc.perform(get("/api/dashboard").param("year", "2020"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revenueNet").value(0))
                .andExpect(jsonPath("$.receivable.count").value(0))
                .andExpect(jsonPath("$.actions.length()").value(0));
    }
}
