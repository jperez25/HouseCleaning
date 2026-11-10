package com.occ.housecleaning.controller;

import com.occ.housecleaning.dto.MarginCallStatus;
import com.occ.housecleaning.dto.VolatilityShockResponse;
import com.occ.housecleaning.model.MarginCall;
import com.occ.housecleaning.model.MemberLedgerBalance;
import com.occ.housecleaning.model.Position;
import com.occ.housecleaning.service.ClearingService;
import com.occ.housecleaning.service.RiskService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RiskController.class)
class RiskControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean ClearingService clearingService;
    @MockitoBean RiskService riskService;

    @Test
    @DisplayName("GET margin returns balances, positions and shortfall")
    void marginReturnsPositionsAndShortfall() throws Exception {
        when(clearingService.getMemberBalance("MEM-101")).thenReturn(Optional.of(
            MemberLedgerBalance.builder().memberId("MEM-101").cashBalance(new BigDecimal("100"))
                .marginBalance(new BigDecimal("450")).updatedAt(Instant.now()).version(3L).build()));
        Position position = Position.builder().memberId("MEM-101").optionSymbol("AAPL260619C00150000")
            .netQuantity(10).lastPrice(new BigDecimal("4.50")).updatedAt(Instant.now()).build();
        when(riskService.getPositions("MEM-101")).thenReturn(List.of(position));
        when(riskService.factorFor("AAPL260619C00150000")).thenReturn(new BigDecimal("0.10"));
        when(riskService.marginFor(position)).thenReturn(new BigDecimal("450"));

        mockMvc.perform(get("/api/v1/members/MEM-101/margin"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.marginBalance").value(450))
            .andExpect(jsonPath("$.shortfall").value(350))
            .andExpect(jsonPath("$.positions[0].netQuantity").value(10))
            .andExpect(jsonPath("$.positions[0].volatilityFactor").value(0.10))
            .andExpect(jsonPath("$.version").doesNotExist());
    }

    @Test
    @DisplayName("Shortfall is floored at zero when cash covers margin")
    void shortfallFlooredAtZero() throws Exception {
        when(clearingService.getMemberBalance("MEM-101")).thenReturn(Optional.of(
            MemberLedgerBalance.builder().memberId("MEM-101").cashBalance(new BigDecimal("1000"))
                .marginBalance(new BigDecimal("450")).updatedAt(Instant.now()).build()));
        when(riskService.getPositions("MEM-101")).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/members/MEM-101/margin"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.shortfall").value(0));
    }

    @Test
    @DisplayName("GET margin for an unknown member returns 404")
    void marginUnknownMember() throws Exception {
        when(clearingService.getMemberBalance("MEM-NOPE")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/members/MEM-NOPE/margin")).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET margin-calls returns a page of calls")
    void marginCallsPage() throws Exception {
        when(clearingService.memberExists("MEM-101")).thenReturn(true);
        MarginCall call = MarginCall.builder().id(1L).memberId("MEM-101").marginRequired(new BigDecimal("450"))
            .cashAvailable(BigDecimal.ZERO).shortfall(new BigDecimal("450")).status(MarginCallStatus.OPEN)
            .createdAt(Instant.now()).updatedAt(Instant.now()).build();
        when(riskService.getMarginCalls(eq("MEM-101"), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(call), PageRequest.of(0, 50), 1));

        mockMvc.perform(get("/api/v1/members/MEM-101/margin-calls"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].status").value("OPEN"))
            .andExpect(jsonPath("$.content[0].shortfall").value(450))
            .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @DisplayName("GET margin-calls for an unknown member returns 404")
    void marginCallsUnknownMember() throws Exception {
        when(clearingService.memberExists("MEM-NOPE")).thenReturn(false);

        mockMvc.perform(get("/api/v1/members/MEM-NOPE/margin-calls")).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST volatility applies a global shock when no symbol is given")
    void globalShock() throws Exception {
        when(riskService.applyShock(isNull(), eq(new BigDecimal("0.25"))))
            .thenReturn(new VolatilityShockResponse(null, new BigDecimal("0.25"), 4));

        mockMvc.perform(post("/api/v1/risk/volatility").contentType(MediaType.APPLICATION_JSON)
                .content("{\"factor\":0.25}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.factor").value(0.25))
            .andExpect(jsonPath("$.membersRecalculated").value(4));
    }

    @Test
    @DisplayName("POST volatility rejects a missing, non-positive or oversized factor and a malformed symbol")
    void invalidShockRejected() throws Exception {
        for (String body : List.of("{}", "{\"factor\":0}", "{\"factor\":-1}", "{\"factor\":11}",
                "{\"factor\":0.2,\"symbol\":\"bad\"}")) {
            mockMvc.perform(post("/api/v1/risk/volatility").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        }
        verify(riskService, never()).applyShock(any(), any());
    }
}
