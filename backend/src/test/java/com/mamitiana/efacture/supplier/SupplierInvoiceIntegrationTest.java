package com.mamitiana.efacture.supplier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.mamitiana.efacture.IntegrationTest;
import com.mamitiana.efacture.ai.LlmLane;
import com.mamitiana.efacture.ai.LlmResult;
import com.mamitiana.efacture.ai.LlmUnavailableException;
import com.mamitiana.efacture.facturx.FacturXGenerator;
import com.mamitiana.efacture.invoice.InvoiceFixtures;
import java.time.Duration;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WithMockUser(username = "comptable@demo.fr", roles = "ACCOUNTANT")
class SupplierInvoiceIntegrationTest extends IntegrationTest {

    @Autowired
    private MockMvc mvc;

    /** The model is scripted here; the real reading is covered by ReadersTest and the evaluation set. */
    @MockitoBean
    private AiInvoiceReader aiReader;

    private static MockMultipartFile pdf(String name, byte[] content) {
        return new MockMultipartFile("file", name, "application/pdf", content);
    }

    private String upload(MockMultipartFile file) throws Exception {
        return mvc.perform(multipart("/api/supplier-invoices").file(file))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void facturXIsReadWithoutAModelAndASecondCopyIsFlaggedAsDuplicate() throws Exception {
        byte[] facturX = new FacturXGenerator().generate(InvoiceFixtures.issuedInvoice(), InvoiceFixtures.seller());

        String first = upload(pdf("F-2026-00001.pdf", facturX));
        org.assertj.core.api.Assertions.assertThat((String) JsonPath.read(first, "$.source")).isEqualTo("FACTURX");
        org.assertj.core.api.Assertions.assertThat((String) JsonPath.read(first, "$.status")).isEqualTo("CHECKED");
        org.assertj.core.api.Assertions.assertThat((Object) JsonPath.read(first, "$.extraction")).isNull();

        String second = upload(pdf("copie.pdf", facturX));
        org.assertj.core.api.Assertions.assertThat((String) JsonPath.read(second, "$.status")).isEqualTo("NEEDS_REVIEW");
        org.assertj.core.api.Assertions.assertThat(JsonPath.<java.util.List<String>>read(second, "$.issues[*].code")).contains("DUPLICATE");
    }

    @Test
    void misreadIbanIsCaughtCorrectedThenValidated() throws Exception {
        ExtractedInvoice misread = withIban(InvoiceChecksTest.valid(), "FR7630006000011234567890188");
        when(aiReader.read(any(), eq("application/pdf")))
                .thenReturn(new AiInvoiceReader.Reading(
                        new LlmResult<>(misread, "gemini", "gemini-3.5-flash", Duration.ofMillis(1800), 1), ""));

        String body = upload(pdf("facture-imprimerie.pdf", TestPdfs.sampleInvoice()));
        int id = JsonPath.read(body, "$.id");
        mvc.perform(get("/api/supplier-invoices/{id}", id))
                .andExpect(jsonPath("$.source").value("AI"))
                .andExpect(jsonPath("$.status").value("NEEDS_REVIEW"))
                .andExpect(jsonPath("$.extraction.model").value("gemini-3.5-flash"))
                .andExpect(jsonPath("$.issues[*].code").value(Matchers.contains("INVALID_IBAN")));

        // Validation is refused while an error remains.
        mvc.perform(post("/api/supplier-invoices/{id}/validate", id)).andExpect(status().isConflict());

        // A person fixes the IBAN: checks run again and pass.
        mvc.perform(put("/api/supplier-invoices/{id}", id).contentType(MediaType.APPLICATION_JSON)
                        .content(json(InvoiceChecksTest.valid())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CHECKED"))
                .andExpect(jsonPath("$.issues.length()").value(0));

        mvc.perform(post("/api/supplier-invoices/{id}/validate", id))
                .andExpect(jsonPath("$.status").value("VALIDATED"))
                .andExpect(jsonPath("$.reviewedBy").value("comptable@demo.fr"));

        mvc.perform(get("/api/supplier-invoices/{id}/file", id))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF));
    }

    @Test
    void fileIsKeptForManualEntryWhenNoModelIsAvailable() throws Exception {
        when(aiReader.read(any(), any())).thenThrow(new LlmUnavailableException(LlmLane.DOCUMENT, null));

        String body = upload(pdf("scan.pdf", TestPdfs.sampleInvoice()));

        org.assertj.core.api.Assertions.assertThat((String) JsonPath.read(body, "$.source")).isEqualTo("MANUAL");
        org.assertj.core.api.Assertions.assertThat((String) JsonPath.read(body, "$.status")).isEqualTo("NEEDS_REVIEW");
    }

    @Test
    void refusesUnsupportedFiles() throws Exception {
        mvc.perform(multipart("/api/supplier-invoices")
                        .file(new MockMultipartFile("file", "facture.docx", "application/msword", new byte[] {1, 2, 3})))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(Matchers.containsString("PDF")));
    }

    private static ExtractedInvoice withIban(ExtractedInvoice v, String iban) {
        return new ExtractedInvoice(v.supplierName(), v.supplierSiren(), v.supplierVatNumber(), iban, v.paymentMeans(), v.invoiceNumber(),
                v.issueDate(), v.dueDate(), v.currency(), v.lines(), v.totalNet(), v.totalVat(), v.totalGross());
    }

    private static String json(ExtractedInvoice v) {
        return """
                {"supplierName": "%s", "supplierSiren": "%s", "supplierVatNumber": "%s", "supplierIban": "%s", "paymentMeans": "TRANSFER",
                 "invoiceNumber": "%s", "issueDate": "%s", "dueDate": "%s", "currency": "EUR",
                 "lines": [{"description": "Flyers A5", "quantity": 1, "unitPrice": 250.00, "vatRate": 20, "netAmount": 250.00},
                           {"description": "Cartes de visite", "quantity": 2, "unitPrice": 45.00, "vatRate": 20, "netAmount": 90.00}],
                 "totalNet": 340.00, "totalVat": 68.00, "totalGross": 408.00}
                """.formatted(v.supplierName(), v.supplierSiren(), v.supplierVatNumber(), v.supplierIban(),
                v.invoiceNumber(), v.issueDate(), v.dueDate());
    }
}
