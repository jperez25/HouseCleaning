package com.occ.housecleaning.bdd.support;

import com.occ.housecleaning.bdd.models.TradeRequest;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Factory and random data generator for producing valid {@link TradeRequest} instances
 * for automated testing, load testing, and simulation scenarios.
 */
public class TradeRequestGenerator {

    private static final String[] ROOT_SYMBOLS = {
        "AAPL", "MSFT", "GOOGL", "AMZN", "NVDA", "TSLA", "META", "SPY", "QQQ", "AMD", "NFLX", "JPM"
    };

    private static final String[] EXCHANGES = {
        "CBOE", "BOX", "MIAX", "AMEX", "PHLX", "ARCA", "BATS", "ISE", "EDGO", "PEARL"
    };

    private static final String[] CLEARING_MEMBERS = {
        "MEM-101", "MEM-102", "MEM-201", "MEM-202", "MEM-301", "MEM-302", "MEM-401", "MEM-402", "MEM-501", "MEM-502"
    };

    private static final com.fasterxml.jackson.databind.ObjectMapper MAPPER =
            new com.fasterxml.jackson.databind.ObjectMapper()
                    .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
                    .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyMMdd");

    /**
     * Generates a single valid {@link TradeRequest} with randomized data.
     *
     * @return a valid, random trade request
     */
    public static TradeRequest createRandomTradeRequest() {
        return builder().build();
    }

    /**
     * Generator helper to create a valid random TradeRequest.
     */
    public static TradeRequest createEmptyTradeRequest() {
        return new TradeRequest(
                "", "", "", -1, BigDecimal.ZERO, "BUYER1", "SELLER1", java.time.Instant.now()
        );
    }

    /**
     * Generates a batch of valid {@link TradeRequest}s with randomized data.
     *
     * @param count the number of requests to generate
     * @return a list of random trade requests
     */
    public static List<TradeRequest> createRandomTradeRequests(int count) {
        if (count < 0) {
            throw new IllegalArgumentException("Count must be greater than or equal to zero");
        }
        List<TradeRequest> requests = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            requests.add(createRandomTradeRequest());
        }
        return requests;
    }

    /**
     * Creates a builder initialized with valid random values for all fields.
     *
     * @return a new builder pre-populated with randomized valid values
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Generates a random external trade ID (e.g. EXCH-A1B2C3D4E5).
     */
    public static String randomExternalTradeId() {
        return "EXCH-" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
    }

    /**
     * Picks a random exchange code from standard options exchanges.
     */
    public static String randomExchangeCode() {
        return EXCHANGES[ThreadLocalRandom.current().nextInt(EXCHANGES.length)];
    }

    /**
     * Generates a compliant OCC option symbol (e.g. AAPL260619C00150000).
     */
    public static String randomOptionSymbol() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        String root = ROOT_SYMBOLS[random.nextInt(ROOT_SYMBOLS.length)];

        // Expiration date in the future (between 1 and 365 days)
        LocalDate expDate = LocalDate.now().plusDays(random.nextInt(1, 365));
        String formattedDate = expDate.format(DATE_FORMATTER);

        char type = random.nextBoolean() ? 'C' : 'P';

        // Strike price between $10 and $1000 in whole dollars
        int strikeInDollars = random.nextInt(10, 1000);
        int strikeValue = strikeInDollars * 1000; // 3 decimal places * 1000

        return String.format("%s%s%c%08d", root, formattedDate, type, strikeValue);
    }

    /**
     * Generates a random positive contract quantity (e.g., 1 to 500).
     */
    public static int randomQuantity() {
        return ThreadLocalRandom.current().nextInt(1, 501);
    }

    /**
     * Generates a random execution price between $0.05 and $150.00 with 2 decimal places.
     */
    public static BigDecimal randomExecutionPrice() {
        int cents = ThreadLocalRandom.current().nextInt(5, 15001);
        return BigDecimal.valueOf(cents).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    /**
     * Returns a pair of two distinct clearing member IDs.
     */
    public static String[] randomDistinctMembers() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int buyerIdx = random.nextInt(CLEARING_MEMBERS.length);
        int sellerIdx;
        do {
            sellerIdx = random.nextInt(CLEARING_MEMBERS.length);
        } while (sellerIdx == buyerIdx);

        return new String[] { CLEARING_MEMBERS[buyerIdx], CLEARING_MEMBERS[sellerIdx] };
    }

    /**
     * Generates an execution timestamp within the last 24 hours.
     */
    public static Instant randomExecutionTimestamp() {
        int secondsAgo = ThreadLocalRandom.current().nextInt(0, 86400);
        return Instant.now().minus(secondsAgo, ChronoUnit.SECONDS);
    }

    /**
     * Fluent builder for customizable {@link TradeRequest} creation with randomized defaults.
     */
    public static class Builder {
        private String externalTradeId;
        private String exchangeCode;
        private String optionSymbol;
        private Integer quantity;
        private BigDecimal executionPrice;
        private String buyerMemberId;
        private String sellerMemberId;
        private Instant executionTimestamp;

        public Builder() {
            String[] members = randomDistinctMembers();
            this.externalTradeId = randomExternalTradeId();
            this.exchangeCode = randomExchangeCode();
            this.optionSymbol = randomOptionSymbol();
            this.quantity = randomQuantity();
            this.executionPrice = randomExecutionPrice();
            this.buyerMemberId = members[0];
            this.sellerMemberId = members[1];
            this.executionTimestamp = randomExecutionTimestamp();
        }

        public Builder withExternalTradeId(String externalTradeId) {
            this.externalTradeId = externalTradeId;
            return this;
        }

        public Builder withExchangeCode(String exchangeCode) {
            this.exchangeCode = exchangeCode;
            return this;
        }

        public Builder withOptionSymbol(String optionSymbol) {
            this.optionSymbol = optionSymbol;
            return this;
        }

        public Builder withQuantity(Integer quantity) {
            this.quantity = quantity;
            return this;
        }

        public Builder withExecutionPrice(BigDecimal executionPrice) {
            this.executionPrice = executionPrice;
            return this;
        }

        public Builder withExecutionPrice(double executionPrice) {
            this.executionPrice = BigDecimal.valueOf(executionPrice).setScale(2, RoundingMode.HALF_UP);
            return this;
        }

        public Builder withBuyerMemberId(String buyerMemberId) {
            this.buyerMemberId = buyerMemberId;
            return this;
        }

        public Builder withSellerMemberId(String sellerMemberId) {
            this.sellerMemberId = sellerMemberId;
            return this;
        }

        public Builder withMembers(String buyerMemberId, String sellerMemberId) {
            this.buyerMemberId = buyerMemberId;
            this.sellerMemberId = sellerMemberId;
            return this;
        }

        public Builder withExecutionTimestamp(Instant executionTimestamp) {
            this.executionTimestamp = executionTimestamp;
            return this;
        }

        /**
         * Builds the request as a JSON-friendly Map (ISO-8601 timestamp). Used by Karate, which cannot
         * serialize Java records on its own; Cucumber uses {@link #build()}.
         */
        public java.util.Map<String, Object> buildMap() {
            return MAPPER.convertValue(build(), new com.fasterxml.jackson.core.type.TypeReference<>() { });
        }

        public TradeRequest build() {
            return new TradeRequest(
                externalTradeId,
                exchangeCode,
                optionSymbol,
                quantity,
                executionPrice,
                buyerMemberId,
                sellerMemberId,
                executionTimestamp
            );
        }
    }
}
