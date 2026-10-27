package com.mamitiana.efacture.dashboard;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DashboardController {

    private final DashboardService service;

    public DashboardController(DashboardService service) {
        this.service = service;
    }

    @GetMapping("/api/dashboard")
    public DashboardResponse dashboard(@RequestParam(required = false) Integer year) {
        return service.forYear(year);
    }
}
