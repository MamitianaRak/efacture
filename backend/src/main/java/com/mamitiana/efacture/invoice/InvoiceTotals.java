package com.mamitiana.efacture.invoice;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Invoice amounts. VAT is computed once per rate on the sum of that rate's line amounts (EN 16931 method),
 * not line by line, so the totals match what a Factur-X validator recomputes.
 */
public record InvoiceTotals(BigDecimal totalNet, BigDecimal totalVat, BigDecimal totalGross, List<VatBreakdown> vatBreakdown) {

    public record VatBreakdown(BigDecimal rate, BigDecimal taxableAmount, BigDecimal vatAmount) {
    }

    public static InvoiceTotals of(Collection<InvoiceLine> lines) {
        Map<BigDecimal, BigDecimal> netByRate = lines.stream().collect(Collectors.groupingBy(
                line -> line.getVatRate().stripTrailingZeros(),
                TreeMap::new,
                Collectors.reducing(BigDecimal.ZERO, InvoiceLine::netAmount, BigDecimal::add)));

        List<VatBreakdown> breakdown = netByRate.entrySet().stream()
                .map(e -> new VatBreakdown(e.getKey(), e.getValue(),
                        e.getValue().multiply(e.getKey()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)))
                .toList();

        BigDecimal net = sum(breakdown.stream().map(VatBreakdown::taxableAmount).toList());
        BigDecimal vat = sum(breakdown.stream().map(VatBreakdown::vatAmount).toList());
        return new InvoiceTotals(net, vat, net.add(vat), breakdown);
    }

    private static BigDecimal sum(List<BigDecimal> amounts) {
        return amounts.stream().reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
    }
}
