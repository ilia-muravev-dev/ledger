package dev.iliamuravev.ledger.domain;

import java.util.Currency;
import java.util.regex.Pattern;

public final class CurrencyCodes {

    private static final Pattern ISO_4217 = Pattern.compile("[A-Z]{3}");

    private CurrencyCodes() {
    }

    public static Currency parse(String code) {
        if (code == null || !ISO_4217.matcher(code).matches()) {
            throw new InvalidRequestException("Currency must be a three-letter ISO 4217 code, got: " + code);
        }
        try {
            return Currency.getInstance(code);
        } catch (IllegalArgumentException e) {
            throw new InvalidRequestException("Unknown ISO 4217 currency: " + code);
        }
    }
}
