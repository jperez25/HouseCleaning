package com.occ.housecleaning.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TradeRequestTest {

    private static Validator validator;
    private static ObjectMapper objectMapper;

    @BeforeAll
    static void setUp() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            validator = factory.getValidator();
        }
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }

    @Test
    @DisplayName("Should successfully construct and validate a valid TradeRequest")
    void validTradeRequest() {
        TradeRequest request = new TradeRequest(
            "EXCH-2026-98123",
            "CBOE",
            "AAPL260619C00150000",
            10,
            new BigDecimal("4.50"),
            "MEM-101",
            "MEM-202",
            Instant.parse("2026-11-05T15:30:00Z")
        );

        var violations = validator.validate(request);
        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("Should deserialize sample JSON into TradeRequest")
    void deserializeJson() throws Exception {
        String json = """
            {
              "externalTradeId": "EXCH-2026-98123",
              "exchangeCode": "CBOE",
              "optionSymbol": "AAPL260619C00150000",
              "quantity": 10,
              "executionPrice": 4.50,
              "buyerMemberId": "MEM-101",
              "sellerMemberId": "MEM-202",
              "executionTimestamp": "2026-11-05T15:30:00Z"
            }
            """;

        TradeRequest request = objectMapper.readValue(json, TradeRequest.class);

        assertThat(request.externalTradeId()).isEqualTo("EXCH-2026-98123");
        assertThat(request.exchangeCode()).isEqualTo("CBOE");
        assertThat(request.optionSymbol()).isEqualTo("AAPL260619C00150000");
        assertThat(request.quantity()).isEqualTo(10);
        assertThat(request.executionPrice()).isEqualByComparingTo("4.50");
        assertThat(request.buyerMemberId()).isEqualTo("MEM-101");
        assertThat(request.sellerMemberId()).isEqualTo("MEM-202");
        assertThat(request.executionTimestamp()).isEqualTo(Instant.parse("2026-11-05T15:30:00Z"));

        var violations = validator.validate(request);
        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("Should reject self-trade when buyer equals seller")
    void rejectSelfTrade() {
        assertThatThrownBy(() -> new TradeRequest(
            "EXCH-2026-98123",
            "CBOE",
            "AAPL260619C00150000",
            10,
            new BigDecimal("4.50"),
            "MEM-101",
            "MEM-101",
            Instant.parse("2026-11-05T15:30:00Z")
        )).isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("buyerMemberId and sellerMemberId cannot be the same clearing member");
    }

    @Test
    @DisplayName("Should detect invalid option symbol format")
    void invalidOptionSymbol() {
        TradeRequest request = new TradeRequest(
            "EXCH-2026-98123",
            "CBOE",
            "INVALID_SYM",
            10,
            new BigDecimal("4.50"),
            "MEM-101",
            "MEM-202",
            Instant.parse("2026-11-05T15:30:00Z")
        );

        var violations = validator.validate(request);
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("optionSymbol");
    }
}
