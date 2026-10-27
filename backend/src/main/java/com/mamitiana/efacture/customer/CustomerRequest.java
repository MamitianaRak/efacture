package com.mamitiana.efacture.customer;

import com.mamitiana.efacture.common.FrenchVatNumber;
import com.mamitiana.efacture.common.Siren;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CustomerRequest(
        @NotBlank @Size(max = 200) String legalName,
        @Siren String siren,
        @FrenchVatNumber String vatNumber,
        @NotBlank @Size(max = 200) String street,
        @NotBlank @Pattern(regexp = "\\d{5}", message = "code postal à 5 chiffres") String postalCode,
        @NotBlank @Size(max = 100) String city,
        @Email String email) {
}
