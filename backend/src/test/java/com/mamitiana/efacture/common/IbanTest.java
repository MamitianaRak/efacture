package com.mamitiana.efacture.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class IbanTest {

    @Test
    void acceptsValidIbansWrittenAnyWay() {
        assertThat(Iban.isValid("FR7630006000011234567890189")).isTrue();
        assertThat(Iban.isValid("fr76 3000 6000 0112 3456 7890 189")).isTrue();
        assertThat(Iban.isValid("DE89370400440532013000")).isTrue();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
            "FR7630006000011234567890188", // one digit misread
            "FR7630006000011234567890",    // truncated
            "7630006000011234567890189"})  // no country code
    void rejectsInvalidIbans(String iban) {
        assertThat(Iban.isValid(iban)).isFalse();
    }

    @Test
    void normalizesSpacesAndCase() {
        assertThat(Iban.normalize(" fr76 3000 6000 ")).isEqualTo("FR7630006000");
    }
}
