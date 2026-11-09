package com.occ.housecleaning.service;

import com.occ.housecleaning.dto.ContractLeg;
import com.occ.housecleaning.dto.LegType;
import com.occ.housecleaning.dto.NovationStatus;
import com.occ.housecleaning.dto.TradeNovationResponse;
import com.occ.housecleaning.dto.TradeRequest;
import com.occ.housecleaning.dto.RejectionReason;
import com.occ.housecleaning.exception.TradeRejectedException;
import com.occ.housecleaning.model.Member;
import com.occ.housecleaning.model.MemberLedgerBalance;
import com.occ.housecleaning.repository.MemberLedgerBalanceRepository;
import com.occ.housecleaning.repository.MemberLedgerEntryRepository;
import com.occ.housecleaning.repository.MemberRepository;
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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
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

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private TradeAuditRecorder tradeAuditRecorder;

    private ClearingService clearingService;

    @BeforeEach
    void setUp() {
        clearingService = new ClearingService(
            rawTradeAuditRepository,
            novatedContractRepository,
            memberLedgerBalanceRepository,
            memberLedgerEntryRepository,
            memberRepository,
            tradeAuditRecorder,
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

        givenMember("MEM-101", true, "1000000");
        givenMember("MEM-202", true, "1000000");
        givenBalance("MEM-101", "0");
        givenBalance("MEM-202", "0");

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
            audit.getStatus() == NovationStatus.NOVATED
        ));

        // Verify novated_contracts persistence
        verify(novatedContractRepository).saveAllAndFlush(argThat(list -> {
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

    @Test
    @DisplayName("Should reject a duplicate trade with DUPLICATE_TRADE, audit the rejection and move no money")
    void shouldRejectDuplicateTrade() {
        when(novatedContractRepository.existsByExternalTradeId("EXCH-2026-98123")).thenReturn(true);

        assertThatThrownBy(() -> clearingService.novateTrade(request("MEM-101", "MEM-202", "4.50")))
            .isInstanceOfSatisfying(TradeRejectedException.class,
                e -> assertThat(e.getReason()).isEqualTo(RejectionReason.DUPLICATE_TRADE));

        verifyRejectionAudited(RejectionReason.DUPLICATE_TRADE);
        verify(novatedContractRepository, never()).saveAllAndFlush(any());
        verify(memberLedgerBalanceRepository, never()).save(any());
        verify(memberLedgerEntryRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should reject an unregistered member with UNKNOWN_MEMBER")
    void shouldRejectUnknownMember() {
        when(memberRepository.findById("MEM-101")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> clearingService.novateTrade(request("MEM-101", "MEM-202", "4.50")))
            .isInstanceOfSatisfying(TradeRejectedException.class,
                e -> assertThat(e.getReason()).isEqualTo(RejectionReason.UNKNOWN_MEMBER));

        verifyRejectionAudited(RejectionReason.UNKNOWN_MEMBER);
        verify(memberLedgerBalanceRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should reject an inactive member with INACTIVE_MEMBER")
    void shouldRejectInactiveMember() {
        givenMember("MEM-101", true, "1000000");
        givenMember("MEM-202", false, "1000000");

        assertThatThrownBy(() -> clearingService.novateTrade(request("MEM-101", "MEM-202", "4.50")))
            .isInstanceOfSatisfying(TradeRejectedException.class,
                e -> assertThat(e.getReason()).isEqualTo(RejectionReason.INACTIVE_MEMBER));

        verifyRejectionAudited(RejectionReason.INACTIVE_MEMBER);
    }

    @Test
    @DisplayName("Should reject when buyer premium would exceed cash plus credit limit")
    void shouldRejectInsufficientFunds() {
        givenMember("MEM-101", true, "1000");   // premium is 4500
        givenMember("MEM-202", true, "1000000");
        givenBalance("MEM-101", "0");
        givenBalance("MEM-202", "0");

        assertThatThrownBy(() -> clearingService.novateTrade(request("MEM-101", "MEM-202", "4.50")))
            .isInstanceOfSatisfying(TradeRejectedException.class,
                e -> assertThat(e.getReason()).isEqualTo(RejectionReason.INSUFFICIENT_FUNDS));

        verifyRejectionAudited(RejectionReason.INSUFFICIENT_FUNDS);
        verify(novatedContractRepository, never()).saveAllAndFlush(any());
        verify(memberLedgerBalanceRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should allow a buyer to dip below zero exactly down to their credit limit")
    void shouldAllowBuyerUpToCreditLimit() {
        givenMember("MEM-101", true, "4500");   // premium is 4500, balance 0 -> exactly -limit
        givenMember("MEM-202", true, "0");
        givenBalance("MEM-101", "0");
        givenBalance("MEM-202", "0");

        TradeNovationResponse response = clearingService.novateTrade(request("MEM-101", "MEM-202", "4.50"));

        assertThat(response.status()).isEqualTo(NovationStatus.NOVATED);
    }

    private TradeRequest request(String buyer, String seller, String price) {
        return new TradeRequest("EXCH-2026-98123", "CBOE", "AAPL260619C00150000", 10,
            new BigDecimal(price), buyer, seller, Instant.parse("2026-11-05T15:30:00Z"));
    }

    private void givenMember(String id, boolean active, String creditLimit) {
        lenient().when(memberRepository.findById(id)).thenReturn(Optional.of(
            Member.builder().memberId(id).name(id).active(active).creditLimit(new BigDecimal(creditLimit)).build()));
    }

    private void givenBalance(String id, String cash) {
        lenient().when(memberLedgerBalanceRepository.findByMemberIdForUpdate(id)).thenReturn(Optional.of(
            MemberLedgerBalance.builder().memberId(id).cashBalance(new BigDecimal(cash))
                .marginBalance(BigDecimal.ZERO).updatedAt(Instant.now()).build()));
    }

    private void verifyRejectionAudited(RejectionReason reason) {
        verify(tradeAuditRecorder).recordRejection(argThat(audit ->
            audit.getStatus() == NovationStatus.REJECTED && audit.getRejectionReason() == reason));
        verify(rawTradeAuditRepository, never()).save(any());
    }
}
