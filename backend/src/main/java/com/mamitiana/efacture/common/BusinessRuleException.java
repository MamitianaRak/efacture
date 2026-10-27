package com.mamitiana.efacture.common;

/** A well-formed request forbidden by a business rule, such as editing an issued invoice. */
public class BusinessRuleException extends RuntimeException {

    public BusinessRuleException(String message) {
        super(message);
    }
}
