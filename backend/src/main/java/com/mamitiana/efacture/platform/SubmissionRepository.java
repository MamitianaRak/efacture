package com.mamitiana.efacture.platform;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface SubmissionRepository extends JpaRepository<Submission, Long> {

    Optional<Submission> findByExternalId(String externalId);

    Optional<Submission> findFirstByInvoiceIdOrderBySubmittedAtDescIdDesc(Long invoiceId);

    boolean existsByInvoiceIdAndStatusNotIn(Long invoiceId, java.util.Collection<PlatformStatus> statuses);

    /** Latest status of every submitted invoice, for the invoice list. */
    @Query("""
            select s.invoice.id as invoiceId, s.status as status from Submission s
            where s.id = (select max(s2.id) from Submission s2 where s2.invoice = s.invoice)
            """)
    List<LatestStatus> findLatestStatuses();

    interface LatestStatus {
        Long getInvoiceId();

        PlatformStatus getStatus();
    }
}
