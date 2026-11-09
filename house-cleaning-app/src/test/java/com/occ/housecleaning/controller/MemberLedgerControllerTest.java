package com.occ.housecleaning.controller;

import com.occ.housecleaning.model.MemberLedgerBalance;
import com.occ.housecleaning.model.MemberLedgerEntry;
import com.occ.housecleaning.service.ClearingService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.argThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MemberLedgerController.class)
class MemberLedgerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ClearingService clearingService;

    @Test
    @DisplayName("Balance endpoint returns a DTO, not the entity (no version field leaks)")
    void balanceReturnsDto() throws Exception {
        when(clearingService.getMemberBalance("MEM-101")).thenReturn(Optional.of(
            MemberLedgerBalance.builder().memberId("MEM-101").cashBalance(new BigDecimal("10.00"))
                .marginBalance(BigDecimal.ZERO).updatedAt(Instant.now()).version(7L).build()));

        mockMvc.perform(get("/api/v1/members/MEM-101/balance"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.memberId").value("MEM-101"))
            .andExpect(jsonPath("$.cashBalance").value(10.00))
            .andExpect(jsonPath("$.version").doesNotExist());
    }

    @Test
    @DisplayName("Ledger for an unknown member returns 404")
    void ledgerUnknownMemberReturns404() throws Exception {
        when(clearingService.memberExists("MEM-NOPE")).thenReturn(false);

        mockMvc.perform(get("/api/v1/members/MEM-NOPE/ledger"))
            .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Ledger is paginated and out-of-range page parameters are clamped")
    void ledgerPaginatesAndClamps() throws Exception {
        when(clearingService.memberExists("MEM-101")).thenReturn(true);
        MemberLedgerEntry entry = MemberLedgerEntry.builder().id(1L).memberId("MEM-101")
            .externalTradeId("T-1").entryType("DEBIT").amount(BigDecimal.TEN)
            .balanceAfter(BigDecimal.ONE).description("d").createdAt(Instant.now()).build();
        when(clearingService.getMemberLedgerEntries(eq("MEM-101"), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(entry), PageRequest.of(0, 200), 1));

        mockMvc.perform(get("/api/v1/members/MEM-101/ledger?page=-5&size=100000"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].externalTradeId").value("T-1"))
            .andExpect(jsonPath("$.totalElements").value(1));

        verify(clearingService).getMemberLedgerEntries(eq("MEM-101"),
            argThat(p -> p.getPageNumber() == 0 && p.getPageSize() == 200));
    }
}
