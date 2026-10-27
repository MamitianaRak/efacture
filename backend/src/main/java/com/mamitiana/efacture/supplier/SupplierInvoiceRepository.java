package com.mamitiana.efacture.supplier;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface SupplierInvoiceRepository extends JpaRepository<SupplierInvoice, Long> {

    List<SupplierInvoice> findAllByOrderByReceivedAtDesc();

    /** Same supplier, same invoice number, not rejected: the classic duplicate, sent twice or by two channels. */
    @Query("""
            select count(s) > 0 from SupplierInvoice s
            where s.supplierSiren = :siren and s.invoiceNumber = :number
              and s.status <> com.mamitiana.efacture.supplier.SupplierInvoice.Status.REJECTED
              and (:excludedId is null or s.id <> :excludedId)
            """)
    boolean existsDuplicate(String siren, String number, Long excludedId);
}
