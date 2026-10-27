package com.mamitiana.efacture.invoice;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Draft content. Drafts may be incomplete: completeness is checked when the invoice is issued. */
public record InvoiceRequest(
        @NotNull Long customerId,
        LocalDate dueDate,
        @NotNull @Size(max = 200) List<@Valid @NotNull Line> lines) {

    public record Line(
            @NotBlank @Size(max = 500) String description,
            @NotNull @Positive @Digits(integer = 9, fraction = 3) BigDecimal quantity,
            @NotNull @PositiveOrZero @Digits(integer = 12, fraction = 2) BigDecimal unitPrice,
            @NotNull BigDecimal vatRate) {

        @AssertTrue(message = "taux de TVA non autorisé (20, 10, 5.5, 2.1 ou 0)")
        public boolean isVatRateAllowed() {
            return vatRate == null || VatRates.isAllowed(vatRate);
        }

        InvoiceLine toEntity() {
            return new InvoiceLine(description, quantity, unitPrice, vatRate);
        }
    }
}
