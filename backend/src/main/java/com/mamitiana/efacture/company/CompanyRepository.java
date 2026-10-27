package com.mamitiana.efacture.company;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompanyRepository extends JpaRepository<Company, Long> {

    default Optional<Company> findSingleton() {
        return findById(Company.SINGLETON_ID);
    }
}
