package com.mamitiana.efacture.supplier;

import jakarta.validation.constraints.NotBlank;
import java.io.IOException;
import java.security.Principal;
import java.util.List;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Processing supplier invoices is shared between the manager and the accountant. */
@RestController
@RequestMapping("/api/supplier-invoices")
@PreAuthorize("hasAnyRole('MANAGER', 'ACCOUNTANT')")
public class SupplierInvoiceController {

    private final SupplierInvoiceService service;

    public SupplierInvoiceController(SupplierInvoiceService service) {
        this.service = service;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public SupplierInvoiceResponse receive(@RequestParam("file") MultipartFile file, Principal user) throws IOException {
        return service.receive(file.getOriginalFilename(), file.getContentType(), file.getBytes(), user.getName());
    }

    @GetMapping
    public List<SupplierInvoiceSummary> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    public SupplierInvoiceResponse get(@PathVariable Long id) {
        return service.get(id);
    }

    @GetMapping("/{id}/file")
    public ResponseEntity<byte[]> file(@PathVariable Long id) {
        SupplierInvoiceService.StoredFile file = service.file(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename(file.name()).build().toString())
                .body(file.content());
    }

    @PutMapping("/{id}")
    public SupplierInvoiceResponse correct(@PathVariable Long id, @RequestBody ExtractedInvoice corrected, Principal user) {
        return service.correct(id, corrected, user.getName());
    }

    @PostMapping("/{id}/validate")
    public SupplierInvoiceResponse validate(@PathVariable Long id, Principal user) {
        return service.validate(id, user.getName());
    }

    public record RejectRequest(@NotBlank String reason) {
    }

    @PostMapping("/{id}/reject")
    public SupplierInvoiceResponse reject(@PathVariable Long id, @RequestBody @jakarta.validation.Valid RejectRequest request,
                                          Principal user) {
        return service.reject(id, user.getName(), request.reason());
    }
}
