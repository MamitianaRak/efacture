package com.mamitiana.efacture.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Real logins and real JWTs against the demo accounts created at startup. */
class SecurityIntegrationTest extends IntegrationTest {

    private static final String DUE_DATE = LocalDate.now(ZoneId.of("Europe/Paris")).plusDays(30).toString();
    private static final String COMPANY = """
            {"legalName": "Atelier Dupont SARL", "siren": "404833048", "vatNumber": "FR83404833048",
             "street": "1 rue de la Paix", "postalCode": "75002", "city": "Paris", "paymentTerms": "30 jours"}
            """;

    @Autowired
    private MockMvc mvc;

    private String managerToken;
    private String accountantToken;

    @BeforeEach
    void login() throws Exception {
        managerToken = login(DemoUsers.MANAGER_EMAIL, "demo1234");
        accountantToken = login(DemoUsers.ACCOUNTANT_EMAIL, "demo1234");
    }

    private String login(String email, String password) throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"%s\", \"password\": \"%s\"}".formatted(email, password)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.token");
    }

    private static MockHttpServletRequestBuilder as(String token, MockHttpServletRequestBuilder request) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token).contentType(MediaType.APPLICATION_JSON);
    }

    /** As the manager: seller, customer and an issued invoice. Returns the invoice id. */
    private int issuedInvoice() throws Exception {
        mvc.perform(as(managerToken, put("/api/company")).content(COMPANY)).andExpect(status().isOk());
        String customer = mvc.perform(as(managerToken, post("/api/customers")).content("""
                        {"legalName": "Boulangerie Martin", "siren": "552100554", "street": "5 place du Marché",
                         "postalCode": "77000", "city": "Melun"}
                        """))
                .andReturn().getResponse().getContentAsString();
        String invoice = mvc.perform(as(managerToken, post("/api/invoices")).content("""
                        {"customerId": %d, "dueDate": "%s",
                         "lines": [{"description": "Site", "quantity": 1, "unitPrice": 1000, "vatRate": 20}]}
                        """.formatted((int) JsonPath.read(customer, "$.id"), DUE_DATE)))
                .andReturn().getResponse().getContentAsString();
        int id = JsonPath.read(invoice, "$.id");
        mvc.perform(as(managerToken, post("/api/invoices/{id}/issue", id))).andExpect(status().isOk());
        return id;
    }

    @Test
    void apiRequiresAToken() throws Exception {
        mvc.perform(get("/api/invoices"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Authentification requise ou session expirée"));
    }

    @Test
    void rejectsWrongPasswordAndUnknownEmailTheSameWay() throws Exception {
        for (String email : new String[] {DemoUsers.MANAGER_EMAIL, "inconnu@demo.fr"}) {
            mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\": \"%s\", \"password\": \"wrong\"}".formatted(email)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.detail").value("E-mail ou mot de passe incorrect"));
        }
    }

    @Test
    void rejectsATamperedToken() throws Exception {
        String tampered = managerToken.substring(0, managerToken.length() - 4) + "AAAA";

        mvc.perform(as(tampered, get("/api/invoices"))).andExpect(status().isUnauthorized());
    }

    @Test
    void tokenCarriesTheUserAndRole() throws Exception {
        mvc.perform(as(accountantToken, get("/api/auth/me")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(DemoUsers.ACCOUNTANT_EMAIL))
                .andExpect(jsonPath("$.role").value("ACCOUNTANT"));
    }

    @Test
    void accountantCanReadButNotManage() throws Exception {
        int invoiceId = issuedInvoice();

        mvc.perform(as(accountantToken, get("/api/invoices"))).andExpect(status().isOk());
        mvc.perform(as(accountantToken, get("/api/invoices/{id}/facturx", invoiceId))).andExpect(status().isOk());

        mvc.perform(as(accountantToken, put("/api/company")).content(COMPANY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Votre rôle ne permet pas cette action"));
        mvc.perform(as(accountantToken, post("/api/invoices")).content("""
                        {"customerId": 1, "lines": []}
                        """))
                .andExpect(status().isForbidden());
    }

    @Test
    void accountantIssuesCreditNotesButNotInvoices() throws Exception {
        int invoiceId = issuedInvoice();

        String creditNote = mvc.perform(as(accountantToken, post("/api/invoices/{id}/credit-note", invoiceId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        mvc.perform(as(accountantToken, post("/api/invoices/{id}/issue", (int) JsonPath.read(creditNote, "$.id"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("CREDIT_NOTE"));

        String draft = mvc.perform(as(managerToken, post("/api/invoices")).content("""
                        {"customerId": 1, "dueDate": "%s",
                         "lines": [{"description": "Site", "quantity": 1, "unitPrice": 1000, "vatRate": 20}]}
                        """.formatted(DUE_DATE)))
                .andReturn().getResponse().getContentAsString();
        mvc.perform(as(accountantToken, post("/api/invoices/{id}/issue", (int) JsonPath.read(draft, "$.id"))))
                .andExpect(status().isForbidden());
    }
}
