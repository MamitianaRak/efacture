package com.mamitiana.efacture.company;

import com.mamitiana.efacture.common.FrenchVatNumber;
import com.mamitiana.efacture.common.Siren;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CompanyRequest(
        @NotBlank @Size(max = 200) String legalName,
        @NotBlank @Siren String siren,
        @NotBlank @FrenchVatNumber String vatNumber,
        @NotBlank @Size(max = 200) String street,
        @NotBlank @Pattern(regexp = "\\d{5}", message = "code postal à 5 chiffres") String postalCode,
        @NotBlank @Size(max = 100) String city,
        @Email String email,
        @Size(max = 34) String iban,
        @NotBlank @Size(max = 500) String paymentTerms) {
}
