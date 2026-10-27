package com.mamitiana.efacture.supplier;

import jakarta.persistence.Embeddable;
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** A line as printed on the supplier's invoice. Every field may be missing on a hard-to-read document. */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class LineValue {

    private String description;
    private BigDecimal quantity;
    private BigDecimal unitPrice;
    private BigDecimal vatRate;
    private BigDecimal netAmount;
}
