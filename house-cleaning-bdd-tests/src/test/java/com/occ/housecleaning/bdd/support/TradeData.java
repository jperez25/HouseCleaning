package com.occ.housecleaning.bdd.support;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Test-data factory shared by the Cucumber steps and (via Java interop) the Karate features.
 * Produces plain Maps so both frameworks can serialize them without any app/DTO dependency.
 */
public final class TradeData {

    private static final String[] ROOTS = {"AAPL", "MSFT", "GOOGL", "AMZN", "NVDA", "TSLA", "SPY", "QQQ"};
    private static final String[] EXCHANGES = {"CBOE", "BOX", "MIAX", "AMEX", "PHLX"};
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyMMdd");

    private TradeData() {
    }

    public static String baseUrl() {
        String url = System.getProperty("test.base.url");
        if (url == null || url.isBlank()) {
            url = System.getenv("TEST_BASE_URL");
        }
        return (url == null || url.isBlank()) ? "http://localhost:8080" : url;
    }

    /** A valid, random trade between the two given members. */
    public static Map<String, Object> validTrade(String buyer, String seller) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        Map<String, Object> t = new LinkedHashMap<>();
        t.put("externalTradeId", "EXCH-" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase());
        t.put("exchangeCode", EXCHANGES[r.nextInt(EXCHANGES.length)]);
        t.put("optionSymbol", String.format("%s%s%c%08d", ROOTS[r.nextInt(ROOTS.length)],
                LocalDate.now().plusDays(r.nextInt(1, 365)).format(DATE), r.nextBoolean() ? 'C' : 'P',
                r.nextInt(10, 1000) * 1000));
        t.put("quantity", r.nextInt(1, 501));
        t.put("executionPrice", BigDecimal.valueOf(r.nextInt(5, 15001), 2));
        t.put("buyerMemberId", buyer);
        t.put("sellerMemberId", seller);
        t.put("executionTimestamp", Instant.now().minus(r.nextInt(0, 86400), ChronoUnit.SECONDS).toString());
        return t;
    }

    public static Map<String, Object> validTrade() {
        return validTrade("MEM-101", "MEM-102");
    }
}
