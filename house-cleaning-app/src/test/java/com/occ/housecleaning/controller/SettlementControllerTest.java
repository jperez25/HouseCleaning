package com.occ.housecleaning.controller;

import com.occ.housecleaning.dto.ExerciseResponse;
import com.occ.housecleaning.dto.NovationStatus;
import com.occ.housecleaning.dto.SettlementResponse;
import com.occ.housecleaning.model.NovatedContract;
import com.occ.housecleaning.model.Position;
import com.occ.housecleaning.service.ExerciseService;
import com.occ.housecleaning.service.QueryService;
import com.occ.housecleaning.service.SettlementService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
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

@WebMvcTest(SettlementController.class)
class SettlementControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean SettlementService settlementService;
    @MockitoBean ExerciseService exerciseService;
    @MockitoBean QueryService queryService;

    @Test
    @DisplayName("Settle returns the outcome, including a FAILED outcome, with 200")
    void settleReturnsOutcome() throws Exception {
        when(settlementService.settleTrade("T-1")).thenReturn(
            new SettlementResponse("T-1", NovationStatus.FAILED, Instant.now(), List.of(), List.of()));

        mockMvc.perform(post("/api/v1/trades/T-1/settle"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("FAILED"));
    }

    @Test
    @DisplayName("Settle maps 404 (unknown trade) and 409 (already settled)")
    void settleErrors() throws Exception {
        when(settlementService.settleTrade("NOPE"))
            .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "not novated"));
        when(settlementService.settleTrade("DONE"))
            .thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "already settled"));

        mockMvc.perform(post("/api/v1/trades/NOPE/settle")).andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/trades/DONE/settle")).andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Exercise validates its body before calling the service")
    void exerciseValidation() throws Exception {
        for (String body : List.of("{}",
                "{\"memberId\":\"M\",\"optionSymbol\":\"bad\",\"quantity\":1,\"settlementPrice\":160}",
                "{\"memberId\":\"M\",\"optionSymbol\":\"AAPL260619C00150000\",\"quantity\":0,\"settlementPrice\":160}",
                "{\"memberId\":\"M\",\"optionSymbol\":\"AAPL260619C00150000\",\"quantity\":1,\"settlementPrice\":0}")) {
            mockMvc.perform(post("/api/v1/options/exercise").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        }
        verify(exerciseService, never()).exercise(any());
    }

    @Test
    @DisplayName("Exercise returns the payout and assignments")
    void exerciseOk() throws Exception {
        when(exerciseService.exercise(any())).thenReturn(new ExerciseResponse("EXR-1", "M", "AAPL260619C00150000", 2,
            new BigDecimal("150"), new BigDecimal("160"), new BigDecimal("10"), new BigDecimal("2000"),
            Instant.now(), List.of()));

        mockMvc.perform(post("/api/v1/options/exercise").contentType(MediaType.APPLICATION_JSON)
                .content("{\"memberId\":\"M\",\"optionSymbol\":\"AAPL260619C00150000\",\"quantity\":2,\"settlementPrice\":160}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalPayout").value(2000));
    }

    @Test
    @DisplayName("Trade log passes filters through and clamps page parameters")
    void tradeLogFiltersAndClamping() throws Exception {
        when(queryService.tradeLog(any(), any(), any(), any(Pageable.class)))
            .thenReturn(new PageImpl<NovatedContract>(List.of(), PageRequest.of(0, 200), 0));

        mockMvc.perform(get("/api/v1/trades/log?memberId=MEM-1&status=SETTLED&page=-5&size=9999"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(0));

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(queryService).tradeLog(eq("MEM-1"), isNull(), eq(NovationStatus.SETTLED), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isZero();
        assertThat(pageable.getValue().getPageSize()).isEqualTo(MemberLedgerController.MAX_PAGE_SIZE);
    }

    @Test
    @DisplayName("An invalid enum filter is a 400, not a 500")
    void invalidEnumFilter() throws Exception {
        mockMvc.perform(get("/api/v1/trades/log?status=BOGUS")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/settlement/exceptions?status=BOGUS")).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Positions endpoint hides flat positions by default")
    void positionsDefaultHidesFlat() throws Exception {
        Position p = Position.builder().memberId("MEM-1").optionSymbol("AAPL260619C00150000").netQuantity(-3)
            .lastPrice(new BigDecimal("4.50")).updatedAt(Instant.now()).build();
        when(queryService.positions(any(), any(), eq(false), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(p), PageRequest.of(0, 50), 1));

        mockMvc.perform(get("/api/v1/positions"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].netQuantity").value(-3))
            .andExpect(jsonPath("$.content[0].id").doesNotExist());
    }
}
