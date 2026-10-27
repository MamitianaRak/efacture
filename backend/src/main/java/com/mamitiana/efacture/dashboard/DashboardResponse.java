package com.mamitiana.efacture.dashboard;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record DashboardResponse(
        int year,
        BigDecimal revenueNet,
        BigDecimal collectedGross,
        Amount receivable,
        Amount overdue,
        List<MonthlyRevenue> monthlyRevenue,
        List<OverdueInvoice> overdueInvoices,
        List<ActionItem> actions,
        int draftCount) {

    public record Amount(int count, BigDecimal totalGross) {
    }

    public record MonthlyRevenue(int month, BigDecimal revenueNet) {
    }

    public record OverdueInvoice(Long id, String number, String customerName, LocalDate dueDate, int daysLate,
                                 BigDecimal totalGross) {
    }

    public enum ActionType {
        /** Refused by the buyer and not yet cancelled by a credit note. */
        REFUSED_WITHOUT_CREDIT_NOTE,
        /** Technically rejected by the platform: to fix and submit again. */
        REJECTED_SUBMISSION,
        /** The platform stayed unreachable after every retry: submit again. */
        DELIVERY_FAILED
    }

    public record ActionItem(ActionType type, Long invoiceId, String number, String customerName, String reason) {
    }
}
