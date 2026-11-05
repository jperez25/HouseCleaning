package com.occ.housecleaning.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TradeController.class)
class TradeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("POST /api/v1/trades/novate should return 200 OK when request body is valid")
    void validTradeRequestReturns200() throws Exception {
        String validJson = """
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

        mockMvc.perform(post("/api/v1/trades/novate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validJson))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /api/v1/trades/novate should return 400 Bad Request when validation fails")
    void invalidTradeRequestReturns400() throws Exception {
        String invalidJson = """
            {
              "externalTradeId": "",
              "exchangeCode": "CBOE",
              "optionSymbol": "INVALID_SYMBOL",
              "quantity": -5,
              "executionPrice": -4.50,
              "buyerMemberId": "MEM-101",
              "sellerMemberId": "MEM-202",
              "executionTimestamp": "2026-11-05T15:30:00Z"
            }
            """;

        mockMvc.perform(post("/api/v1/trades/novate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidJson))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/v1/trades/novate should return 400 Bad Request on self-trade")
    void selfTradeReturns400() throws Exception {
        String selfTradeJson = """
            {
              "externalTradeId": "EXCH-2026-98123",
              "exchangeCode": "CBOE",
              "optionSymbol": "AAPL260619C00150000",
              "quantity": 10,
              "executionPrice": 4.50,
              "buyerMemberId": "MEM-101",
              "sellerMemberId": "MEM-101",
              "executionTimestamp": "2026-11-05T15:30:00Z"
            }
            """;

        mockMvc.perform(post("/api/v1/trades/novate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(selfTradeJson))
            .andExpect(status().isBadRequest());
    }
}
