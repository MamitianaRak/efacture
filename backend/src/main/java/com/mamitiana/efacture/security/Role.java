package com.mamitiana.efacture.security;

public enum Role {
    /** Business owner: manages customers and issues invoices. */
    MANAGER,
    /** Read access, plus credit notes. */
    ACCOUNTANT
}
