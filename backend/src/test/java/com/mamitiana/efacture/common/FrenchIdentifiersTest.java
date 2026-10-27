package com.mamitiana.efacture.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class FrenchIdentifiersTest {

    // Public SIRENs from the official company register.
    private static final String VALID_SIREN = "404833048";

    @Test
    void acceptsSirenWithValidLuhnKey() {
        assertThat(FrenchIdentifiers.isValidSiren(VALID_SIREN)).isTrue();
        assertThat(FrenchIdentifiers.isValidSiren("552100554")).isTrue();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"404833049", "40483304", "4048330480", "40483304A"})
    void rejectsMalformedOrWrongKeySiren(String siren) {
        assertThat(FrenchIdentifiers.isValidSiren(siren)).isFalse();
    }

    @Test
    void computesVatNumberFromSiren() {
        assertThat(FrenchIdentifiers.vatNumberFromSiren(VALID_SIREN)).isEqualTo("FR83404833048");
    }

    @Test
    void acceptsVatNumberWhoseKeyMatchesSiren() {
        assertThat(FrenchIdentifiers.isValidFrenchVatNumber("FR83404833048")).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"FR84404833048", "FR83404833049", "DE83404833048", "FR8340483304"})
    void rejectsVatNumberWithWrongKeyOrFormat(String vatNumber) {
        assertThat(FrenchIdentifiers.isValidFrenchVatNumber(vatNumber)).isFalse();
    }
}
