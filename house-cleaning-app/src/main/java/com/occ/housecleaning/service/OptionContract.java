package com.occ.housecleaning.service;

import java.math.BigDecimal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parsed OCC option symbol, e.g. {@code AAPL260619C00150000} = AAPL, 2026-06-19, call, strike 150.000.
 */
public record OptionContract(String root, String expiry, boolean call, BigDecimal strike) {

    private static final Pattern FORMAT = Pattern.compile("^([A-Z]{1,6})(\\d{6})([CP])(\\d{8})$");

    public static OptionContract parse(String symbol) {
        Matcher m = FORMAT.matcher(symbol == null ? "" : symbol);
        if (!m.matches()) {
            throw new IllegalArgumentException("Not an OCC option symbol: " + symbol);
        }
        return new OptionContract(m.group(1), m.group(2), "C".equals(m.group(3)),
                new BigDecimal(m.group(4)).movePointLeft(3));
    }

    /** Value of exercising at the given underlying price, per share: never negative. */
    public BigDecimal intrinsicValue(BigDecimal underlyingPrice) {
        BigDecimal value = call ? underlyingPrice.subtract(strike) : strike.subtract(underlyingPrice);
        return value.max(BigDecimal.ZERO);
    }
}
