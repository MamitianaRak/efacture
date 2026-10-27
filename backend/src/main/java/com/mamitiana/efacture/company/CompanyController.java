package com.mamitiana.efacture.company;

import com.mamitiana.efacture.common.Address;
import com.mamitiana.efacture.common.NotFoundException;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/company")
public class CompanyController {

    private final CompanyRepository companies;

    public CompanyController(CompanyRepository companies) {
        this.companies = companies;
    }

    @GetMapping
    public CompanyResponse get() {
        return companies.findSingleton()
                .map(CompanyResponse::from)
                .orElseThrow(() -> new NotFoundException("L'entreprise émettrice n'est pas encore renseignée"));
    }

    @PreAuthorize("hasRole('MANAGER')")
    @PutMapping
    @Transactional
    public CompanyResponse save(@Valid @RequestBody CompanyRequest request) {
        Company company = companies.findSingleton().orElseGet(Company::new);
        company.setLegalName(request.legalName());
        company.setSiren(request.siren());
        company.setVatNumber(request.vatNumber());
        company.setAddress(new Address(request.street(), request.postalCode(), request.city(), "FR"));
        company.setEmail(request.email());
        company.setIban(request.iban());
        company.setPaymentTerms(request.paymentTerms());
        return CompanyResponse.from(companies.save(company));
    }
}
