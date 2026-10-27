package com.mamitiana.efacture.common;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Valid French intra-community VAT number, or null. */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = FrenchVatNumber.Validator.class)
public @interface FrenchVatNumber {

    String message() default "numéro de TVA invalide (format FR + clé + SIREN)";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<FrenchVatNumber, String> {
        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            return value == null || FrenchIdentifiers.isValidFrenchVatNumber(value);
        }
    }
}
