package com.mamitiana.efacture.invoice;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import java.math.BigDecimal;
import java.math.RoundingMode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class InvoiceLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_id")
    private Invoice invoice;

    private int position;

    private String description;

    private BigDecimal quantity;

    private BigDecimal unitPrice;

    private BigDecimal vatRate;

    public InvoiceLine(String description, BigDecimal quantity, BigDecimal unitPrice, BigDecimal vatRate) {
        this.description = description;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.vatRate = vatRate;
    }

    /** Line net amount, rounded to the cent. */
    public BigDecimal netAmount() {
        return quantity.multiply(unitPrice).setScale(2, RoundingMode.HALF_UP);
    }

    InvoiceLine copy() {
        return new InvoiceLine(description, quantity, unitPrice, vatRate);
    }
}
