package com.mamitiana.efacture.customer;

import com.mamitiana.efacture.common.Address;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String legalName;

    /** Required to issue a B2B invoice under the e-invoicing reform; optional while drafting. */
    private String siren;

    private String vatNumber;

    @Embedded
    private Address address;

    private String email;

    private Instant createdAt = Instant.now();
}
