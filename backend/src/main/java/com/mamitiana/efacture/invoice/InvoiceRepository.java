package com.mamitiana.efacture.invoice;

import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    @EntityGraph(attributePaths = "customer")
    List<Invoice> findAllByOrderByCreatedAtDesc();

    @EntityGraph(attributePaths = "customer")
    List<Invoice> findByStatusOrderByCreatedAtDesc(InvoiceStatus status);

    boolean existsByOriginalInvoiceId(Long originalInvoiceId);
}
