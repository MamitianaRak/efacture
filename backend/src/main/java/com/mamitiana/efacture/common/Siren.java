package com.mamitiana.efacture.common;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Valid SIREN, or null. */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = Siren.Validator.class)
public @interface Siren {

    String message() default "SIREN invalide (9 chiffres avec une clé de contrôle correcte)";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<Siren, String> {
        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            return value == null || FrenchIdentifiers.isValidSiren(value);
        }
    }
}
