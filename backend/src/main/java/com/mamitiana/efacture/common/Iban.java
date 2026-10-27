package com.mamitiana.efacture.common;

import java.math.BigInteger;

/** IBAN normalisation and ISO 13616 checksum (mod 97). */
public final class Iban {

    private static final BigInteger NINETY_SEVEN = BigInteger.valueOf(97);

    private Iban() {
    }

    /** Uppercase, without spaces: "fr76 3000 6000 ..." becomes "FR7630006000...". */
    public static String normalize(String iban) {
        return iban == null ? null : iban.replaceAll("\\s+", "").toUpperCase();
    }

    public static boolean isValid(String iban) {
        String value = normalize(iban);
        if (value == null || !value.matches("[A-Z]{2}\\d{2}[A-Z0-9]{11,30}")) {
            return false;
        }
        String rearranged = value.substring(4) + value.substring(0, 4);
        StringBuilder digits = new StringBuilder();
        for (char c : rearranged.toCharArray()) {
            digits.append(Character.isDigit(c) ? String.valueOf(c) : String.valueOf(c - 'A' + 10));
        }
        return new BigInteger(digits.toString()).mod(NINETY_SEVEN).intValue() == 1;
    }
}
