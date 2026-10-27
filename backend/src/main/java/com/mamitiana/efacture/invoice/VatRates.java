package com.mamitiana.efacture.invoice;

import java.math.BigDecimal;
import java.util.List;

/** VAT rates applicable in mainland France. */
public final class VatRates {

    public static final List<BigDecimal> ALLOWED = List.of(
            new BigDecimal("20"), new BigDecimal("10"), new BigDecimal("5.5"), new BigDecimal("2.1"), BigDecimal.ZERO);

    private VatRates() {
    }

    public static boolean isAllowed(BigDecimal rate) {
        return rate != null && ALLOWED.stream().anyMatch(allowed -> allowed.compareTo(rate) == 0);
    }
}
