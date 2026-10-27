package com.mamitiana.efacture.invoice;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/invoices")
public class InvoiceController {

    private final InvoiceService service;

    public InvoiceController(InvoiceService service) {
        this.service = service;
    }

    @GetMapping
    public List<InvoiceSummary> list(@RequestParam(required = false) InvoiceStatus status) {
        return service.list(status);
    }

    @GetMapping("/{id}")
    public InvoiceResponse get(@PathVariable Long id) {
        return service.get(id);
    }

    @PreAuthorize("hasRole('MANAGER')")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public InvoiceResponse createDraft(@Valid @RequestBody InvoiceRequest request) {
        return service.createDraft(request);
    }

    @PreAuthorize("hasRole('MANAGER')")
    @PutMapping("/{id}")
    public InvoiceResponse updateDraft(@PathVariable Long id, @Valid @RequestBody InvoiceRequest request) {
        return service.updateDraft(id, request);
    }

    @PreAuthorize("hasRole('MANAGER')")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteDraft(@PathVariable Long id) {
        service.deleteDraft(id);
    }

    /** The accountant may issue credit notes only; invoices are issued by the manager. */
    @PreAuthorize("hasRole('MANAGER') or @invoiceAccess.isCreditNote(#id)")
    @PostMapping("/{id}/issue")
    public InvoiceResponse issue(@PathVariable Long id) {
        return service.issue(id);
    }

    @GetMapping("/{id}/facturx")
    public ResponseEntity<byte[]> downloadFacturX(@PathVariable Long id) {
        InvoiceService.FacturXFile file = service.exportFacturX(id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(file.filename()).build().toString())
                .body(file.content());
    }

    @PreAuthorize("hasAnyRole('MANAGER', 'ACCOUNTANT')")
    @PostMapping("/{id}/credit-note")
    @ResponseStatus(HttpStatus.CREATED)
    public InvoiceResponse createCreditNote(@PathVariable Long id) {
        return service.createCreditNote(id);
    }
}
