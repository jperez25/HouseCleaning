package com.occ.housecleaning.service;

import com.occ.housecleaning.dto.ContractLeg;
import com.occ.housecleaning.dto.LegType;
import com.occ.housecleaning.dto.NovationStatus;
import com.occ.housecleaning.dto.TradeNovationResponse;
import com.occ.housecleaning.dto.TradeRequest;
import com.occ.housecleaning.model.MemberLedgerBalance;
import com.occ.housecleaning.repository.MemberLedgerBalanceRepository;
import com.occ.housecleaning.repository.MemberLedgerEntryRepository;
import com.occ.housecleaning.repository.NovatedContractRepository;
import com.occ.housecleaning.repository.RawTradeAuditRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClearingServiceTest {

    @Mock
    private RawTradeAuditRepository rawTradeAuditRepository;

    @Mock
    private NovatedContractRepository novatedContractRepository;

    @Mock
    private MemberLedgerBalanceRepository memberLedgerBalanceRepository;

    @Mock
    private MemberLedgerEntryRepository memberLedgerEntryRepository;

    private ClearingService clearingService;

    @BeforeEach
    void setUp() {
        clearingService = new ClearingService(
            rawTradeAuditRepository,
            novatedContractRepository,
            memberLedgerBalanceRepository,
            memberLedgerEntryRepository,
            new JsonMapper()
        );
    }

    @Test
    @DisplayName("Should successfully novate a trade into two opposing contract legs, audit payload, and update balances")
    void shouldNovateTradeIntoTwoLegs() {
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

        when(memberLedgerBalanceRepository.findByMemberId("MEM-101")).thenReturn(Optional.empty());
        when(memberLedgerBalanceRepository.findByMemberId("MEM-202")).thenReturn(Optional.empty());

        TradeNovationResponse response = clearingService.novateTrade(request);

        assertThat(response).isNotNull();
        assertThat(response.externalTradeId()).isEqualTo("EXCH-2026-98123");
        assertThat(response.status()).isEqualTo(NovationStatus.NOVATED);
        assertThat(response.novatedAt()).isNotNull();

        // Contract Leg 1: OCC becomes the Seller to Buyer (Member A)
        ContractLeg buyerLeg = response.buyerLeg();
        assertThat(buyerLeg).isNotNull();
        assertThat(buyerLeg.legId()).startsWith("LEG-");
        assertThat(buyerLeg.externalTradeId()).isEqualTo("EXCH-2026-98123");
        assertThat(buyerLeg.legType()).isEqualTo(LegType.BUYER_LEG);
        assertThat(buyerLeg.buyerMemberId()).isEqualTo("MEM-101");
        assertThat(buyerLeg.sellerMemberId()).isEqualTo("OCC");
        assertThat(buyerLeg.optionSymbol()).isEqualTo("AAPL260619C00150000");
        assertThat(buyerLeg.quantity()).isEqualTo(10);
        assertThat(buyerLeg.executionPrice()).isEqualByComparingTo("4.50");
        assertThat(buyerLeg.status()).isEqualTo(NovationStatus.NOVATED);

        // Contract Leg 2: OCC becomes the Buyer to Seller (Member B)
        ContractLeg sellerLeg = response.sellerLeg();
        assertThat(sellerLeg).isNotNull();
        assertThat(sellerLeg.legId()).startsWith("LEG-");
        assertThat(sellerLeg.externalTradeId()).isEqualTo("EXCH-2026-98123");
        assertThat(sellerLeg.legType()).isEqualTo(LegType.SELLER_LEG);
        assertThat(sellerLeg.buyerMemberId()).isEqualTo("OCC");
        assertThat(sellerLeg.sellerMemberId()).isEqualTo("MEM-202");
        assertThat(sellerLeg.optionSymbol()).isEqualTo("AAPL260619C00150000");
        assertThat(sellerLeg.quantity()).isEqualTo(10);
        assertThat(sellerLeg.executionPrice()).isEqualByComparingTo("4.50");
        assertThat(sellerLeg.status()).isEqualTo(NovationStatus.NOVATED);

        // Leg IDs must be distinct
        assertThat(buyerLeg.legId()).isNotEqualTo(sellerLeg.legId());

        // Verify raw_trades_audit persistence
        verify(rawTradeAuditRepository).save(argThat(audit ->
            audit.getExternalTradeId().equals("EXCH-2026-98123") &&
            audit.getExchangeCode().equals("CBOE") &&
            audit.getRawPayload().contains("EXCH-2026-98123") &&
            audit.getStatus().equals("NOVATED")
        ));

        // Verify novated_contracts persistence
        verify(novatedContractRepository).saveAll(argThat(list -> {
            var it = list.iterator();
            return it.hasNext();
        }));

        // Verify member balances updated: 10 * 4.50 * 100 = 4500.00
        verify(memberLedgerBalanceRepository).save(argThat(bal ->
            bal.getMemberId().equals("MEM-101") &&
            bal.getCashBalance().compareTo(new BigDecimal("-4500.00")) == 0
        ));
        verify(memberLedgerBalanceRepository).save(argThat(bal ->
            bal.getMemberId().equals("MEM-202") &&
            bal.getCashBalance().compareTo(new BigDecimal("4500.00")) == 0
        ));

        // Verify member ledger entries (2 journal entries: 1 debit, 1 credit)
        verify(memberLedgerEntryRepository, times(2)).save(any());
    }

    @Test
    @DisplayName("Should reject null TradeRequest")
    void shouldRejectNullRequest() {
        assertThatThrownBy(() -> clearingService.novateTrade(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("tradeRequest cannot be null");
    }
}
