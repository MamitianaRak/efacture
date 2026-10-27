package com.mamitiana.efacture.common;

/** Checks for French company identifiers. */
public final class FrenchIdentifiers {

    private FrenchIdentifiers() {
    }

    /** A SIREN is 9 digits whose Luhn checksum is valid. */
    public static boolean isValidSiren(String siren) {
        if (siren == null || !siren.matches("\\d{9}")) {
            return false;
        }
        int sum = 0;
        for (int i = 0; i < 9; i++) {
            int digit = siren.charAt(8 - i) - '0';
            if (i % 2 == 1) {
                digit *= 2;
                if (digit > 9) {
                    digit -= 9;
                }
            }
            sum += digit;
        }
        return sum % 10 == 0;
    }

    /** The French VAT number is FR + a 2-digit key + the SIREN, with key = (12 + 3 * (SIREN mod 97)) mod 97. */
    public static boolean isValidFrenchVatNumber(String vatNumber) {
        if (vatNumber == null || !vatNumber.matches("FR\\d{11}")) {
            return false;
        }
        String siren = vatNumber.substring(4);
        int key = Integer.parseInt(vatNumber.substring(2, 4));
        return isValidSiren(siren) && key == vatKey(siren);
    }

    public static String vatNumberFromSiren(String siren) {
        return "FR%02d%s".formatted(vatKey(siren), siren);
    }

    private static int vatKey(String siren) {
        return (int) ((12 + 3 * (Long.parseLong(siren) % 97)) % 97);
    }
}
