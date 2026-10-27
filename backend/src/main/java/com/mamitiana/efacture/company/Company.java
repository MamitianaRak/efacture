package com.mamitiana.efacture.company;

import com.mamitiana.efacture.common.Address;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** The seller. A single row (id = 1): one instance serves one issuing company. */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class Company {

    public static final long SINGLETON_ID = 1L;

    @Id
    private Long id = SINGLETON_ID;

    private String legalName;

    private String siren;

    private String vatNumber;

    @Embedded
    private Address address;

    private String email;

    private String iban;

    /** Payment terms printed on every invoice: deadline, late penalties, 40 EUR recovery fee. */
    private String paymentTerms;
}
