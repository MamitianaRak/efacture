package com.mamitiana.efacture.dashboard;

import com.mamitiana.efacture.dashboard.DashboardResponse.ActionItem;
import com.mamitiana.efacture.dashboard.DashboardResponse.ActionType;
import com.mamitiana.efacture.dashboard.DashboardResponse.Amount;
import com.mamitiana.efacture.dashboard.DashboardResponse.MonthlyRevenue;
import com.mamitiana.efacture.dashboard.DashboardResponse.OverdueInvoice;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-only figures computed in SQL: aggregations over all invoices are cheaper and clearer
 * in the database than by loading entities.
 *
 * An invoice is "receivable" when it is issued, not cancelled by an issued credit note, and its latest
 * platform status is neither paid (ENCAISSEE) nor refused by the buyer (REFUSEE).
 */
@Service
@Transactional(readOnly = true)
public class DashboardService {

    /** Latest platform status per invoice, and invoices cancelled by an issued credit note. */
    private static final String COMMON = """
            WITH latest AS (
                SELECT DISTINCT ON (invoice_id) invoice_id, status, status_reason
                FROM platform_submission
                ORDER BY invoice_id, submitted_at DESC, id DESC
            ),
            credited AS (
                SELECT original_invoice_id AS invoice_id FROM invoice WHERE type = 'CREDIT_NOTE' AND status = 'ISSUED'
            ),
            receivable AS (
                SELECT i.id, i.number, i.due_date, i.total_gross, c.legal_name AS customer_name
                FROM invoice i
                JOIN customer c ON c.id = i.customer_id
                LEFT JOIN latest l ON l.invoice_id = i.id
                WHERE i.type = 'INVOICE' AND i.status = 'ISSUED'
                  AND i.id NOT IN (SELECT invoice_id FROM credited)
                  AND (l.status IS NULL OR l.status NOT IN ('ENCAISSEE', 'REFUSEE'))
            )
            """;

    private final JdbcClient jdbc;
    private final Clock clock;

    public DashboardService(JdbcClient jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public DashboardResponse forYear(Integer requestedYear) {
        LocalDate today = LocalDate.now(clock);
        int year = requestedYear != null ? requestedYear : today.getYear();
        List<MonthlyRevenue> monthly = monthlyRevenue(year);

        return new DashboardResponse(
                year,
                monthly.stream().map(MonthlyRevenue::revenueNet).reduce(BigDecimal.ZERO, BigDecimal::add),
                collected(year),
                amount("", today),
                amount("WHERE due_date < :today", today),
                monthly,
                overdueInvoices(today),
                actions(),
                jdbc.sql("SELECT count(*) FROM invoice WHERE status = 'DRAFT'").query(Integer.class).single());
    }

    /** Net revenue per month: issued invoices minus issued credit notes, by issue date. */
    private List<MonthlyRevenue> monthlyRevenue(int year) {
        Map<Integer, BigDecimal> byMonth = jdbc.sql("""
                        SELECT extract(MONTH FROM issue_date)::int AS month,
                               sum(CASE WHEN type = 'INVOICE' THEN total_net ELSE -total_net END) AS revenue
                        FROM invoice
                        WHERE status = 'ISSUED' AND extract(YEAR FROM issue_date) = :year
                        GROUP BY 1
                        """)
                .param("year", year)
                .query((rs, n) -> Map.entry(rs.getInt("month"), rs.getBigDecimal("revenue")))
                .list().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        // Every month is present, with zero when nothing was invoiced, so the chart needs no gap handling.
        return IntStream.rangeClosed(1, 12)
                .mapToObj(m -> new MonthlyRevenue(m, byMonth.getOrDefault(m, BigDecimal.ZERO)))
                .toList();
    }

    private BigDecimal collected(int year) {
        return jdbc.sql(COMMON + """
                        SELECT coalesce(sum(i.total_gross), 0)
                        FROM invoice i JOIN latest l ON l.invoice_id = i.id
                        WHERE l.status = 'ENCAISSEE' AND extract(YEAR FROM i.issue_date) = :year
                        """)
                .param("year", year)
                .query(BigDecimal.class)
                .single();
    }

    /** Count and total of receivable invoices, optionally narrowed by a WHERE clause on the receivable set. */
    private Amount amount(String where, LocalDate today) {
        return jdbc.sql(COMMON + "SELECT count(*) AS n, coalesce(sum(total_gross), 0) AS total FROM receivable " + where)
                .param("today", today)
                .query((rs, n) -> new Amount(rs.getInt("n"), rs.getBigDecimal("total")))
                .single();
    }

    private List<OverdueInvoice> overdueInvoices(LocalDate today) {
        return jdbc.sql(COMMON + """
                        SELECT id, number, customer_name, due_date, (CAST(:today AS date) - due_date) AS days_late, total_gross
                        FROM receivable
                        WHERE due_date < :today
                        ORDER BY due_date, id
                        LIMIT 10
                        """)
                .param("today", today)
                .query((rs, n) -> new OverdueInvoice(rs.getLong("id"), rs.getString("number"),
                        rs.getString("customer_name"), rs.getObject("due_date", LocalDate.class),
                        rs.getInt("days_late"), rs.getBigDecimal("total_gross")))
                .list();
    }

    private List<ActionItem> actions() {
        return jdbc.sql(COMMON + """
                        SELECT CASE WHEN l.status = 'REFUSEE' THEN 'REFUSED_WITHOUT_CREDIT_NOTE'
                                    ELSE 'REJECTED_SUBMISSION' END AS type,
                               i.id, i.number, c.legal_name, l.status_reason
                        FROM invoice i
                        JOIN customer c ON c.id = i.customer_id
                        JOIN latest l ON l.invoice_id = i.id
                        WHERE (l.status = 'REFUSEE' AND i.id NOT IN (SELECT invoice_id FROM credited))
                           OR l.status = 'REJETEE'
                        ORDER BY i.issue_date, i.id
                        """)
                .query((rs, n) -> new ActionItem(ActionType.valueOf(rs.getString("type")), rs.getLong("id"),
                        rs.getString("number"), rs.getString("legal_name"), rs.getString("status_reason")))
                .list();
    }
}
