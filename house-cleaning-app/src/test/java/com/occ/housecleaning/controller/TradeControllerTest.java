package com.occ.housecleaning.controller;

import com.occ.housecleaning.dto.ContractLeg;
import com.occ.housecleaning.dto.LegType;
import com.occ.housecleaning.dto.NovationStatus;
import com.occ.housecleaning.dto.RejectionReason;
import com.occ.housecleaning.exception.TradeRejectedException;
import com.occ.housecleaning.dto.TradeNovationResponse;
import com.occ.housecleaning.dto.TradeRequest;
import com.occ.housecleaning.service.ClearingService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TradeController.class)
class TradeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ClearingService clearingService;

    @Test
    @DisplayName("POST /api/v1/trades/novate should return 200 OK with novation response when request is valid")
    void validTradeRequestReturns200() throws Exception {
        ContractLeg buyerLeg = new ContractLeg(
            "LEG-001", "EXCH-2026-98123", LegType.BUYER_LEG,
            "MEM-101", "OCC", "AAPL260619C00150000",
            10, new BigDecimal("4.50"), Instant.parse("2026-11-05T15:30:00Z"),
            NovationStatus.NOVATED
        );
        ContractLeg sellerLeg = new ContractLeg(
            "LEG-002", "EXCH-2026-98123", LegType.SELLER_LEG,
            "OCC", "MEM-202", "AAPL260619C00150000",
            10, new BigDecimal("4.50"), Instant.parse("2026-11-05T15:30:00Z"),
            NovationStatus.NOVATED
        );
        TradeNovationResponse novationResponse = new TradeNovationResponse(
            "EXCH-2026-98123",
            NovationStatus.NOVATED,
            Instant.parse("2026-11-05T15:30:01Z"),
            buyerLeg,
            sellerLeg
        );

        when(clearingService.novateTrade(any(TradeRequest.class))).thenReturn(novationResponse);

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
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.externalTradeId").value("EXCH-2026-98123"))
            .andExpect(jsonPath("$.status").value("NOVATED"))
            .andExpect(jsonPath("$.buyerLeg.buyerMemberId").value("MEM-101"))
            .andExpect(jsonPath("$.buyerLeg.sellerMemberId").value("OCC"))
            .andExpect(jsonPath("$.sellerLeg.buyerMemberId").value("OCC"))
            .andExpect(jsonPath("$.sellerLeg.sellerMemberId").value("MEM-202"));
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
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("Bad Request"))
            .andExpect(jsonPath("$.message").value("Request validation failed"))
            .andExpect(jsonPath("$.validationErrors.externalTradeId").value("externalTradeId is required"));
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

    @Test
    @DisplayName("Unexpected service errors should return a generic 500 response")
    void unexpectedServiceErrorReturnsGeneric500() throws Exception {
        when(clearingService.novateTrade(any(TradeRequest.class)))
                .thenThrow(new IllegalStateException("internal database details"));

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
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.message").value("An unexpected error occurred"));
    }

    @Test
    @DisplayName("Missing trade audit should return a structured 404 response")
    void missingTradeAuditReturns404() throws Exception {
        when(clearingService.getTradeAudit("missing-trade")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/trades/missing-trade/audit"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error").value("Not Found"))
            .andExpect(jsonPath("$.message").value("Trade audit not found"))
            .andExpect(jsonPath("$.path").value("/api/v1/trades/missing-trade/audit"));
    }

    private static final String VALID_JSON = """
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

    @Test
    @DisplayName("Duplicate trade should return 409 Conflict with DUPLICATE_TRADE code")
    void duplicateTradeReturns409() throws Exception {
        when(clearingService.novateTrade(any(TradeRequest.class)))
                .thenThrow(new TradeRejectedException(RejectionReason.DUPLICATE_TRADE, "already novated"));

        mockMvc.perform(post("/api/v1/trades/novate").contentType(MediaType.APPLICATION_JSON).content(VALID_JSON))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("DUPLICATE_TRADE"))
            .andExpect(jsonPath("$.message").value("already novated"));
    }

    @Test
    @DisplayName("Unknown member and insufficient funds should return 422 with their business codes")
    void rejectedTradesReturn422() throws Exception {
        when(clearingService.novateTrade(any(TradeRequest.class)))
                .thenThrow(new TradeRejectedException(RejectionReason.UNKNOWN_MEMBER, "not registered"))
                .thenThrow(new TradeRejectedException(RejectionReason.INSUFFICIENT_FUNDS, "too poor"));

        mockMvc.perform(post("/api/v1/trades/novate").contentType(MediaType.APPLICATION_JSON).content(VALID_JSON))
            .andExpect(status().isUnprocessableContent())
            .andExpect(jsonPath("$.code").value("UNKNOWN_MEMBER"));
        mockMvc.perform(post("/api/v1/trades/novate").contentType(MediaType.APPLICATION_JSON).content(VALID_JSON))
            .andExpect(status().isUnprocessableContent())
            .andExpect(jsonPath("$.code").value("INSUFFICIENT_FUNDS"));
    }
}
