package com.mamitiana.efacture.platform;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PlatformController {

    private final PlatformService service;

    public PlatformController(PlatformService service) {
        this.service = service;
    }

    @GetMapping("/api/invoices/{id}/submission")
    public SubmissionResponse latest(@PathVariable Long id) {
        return service.latest(id);
    }

    @PreAuthorize("hasRole('MANAGER')")
    @PostMapping("/api/invoices/{id}/submission")
    @ResponseStatus(HttpStatus.CREATED)
    public SubmissionResponse submit(@PathVariable Long id) {
        return service.submit(id);
    }

    /** Following payments is part of the accountant's job. */
    @PreAuthorize("hasAnyRole('MANAGER', 'ACCOUNTANT')")
    @PostMapping("/api/invoices/{id}/submission/payment")
    public SubmissionResponse markPaid(@PathVariable Long id) {
        return service.markPaid(id);
    }

    @GetMapping("/api/submissions/latest-statuses")
    public Map<Long, PlatformStatus> latestStatuses() {
        return service.latestStatuses();
    }
}
