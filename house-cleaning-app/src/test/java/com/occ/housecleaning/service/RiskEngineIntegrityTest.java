package com.occ.housecleaning.service;

import com.occ.housecleaning.dto.MarginCallStatus;
import com.occ.housecleaning.dto.NovationStatus;
import com.occ.housecleaning.dto.TradeRequest;
import com.occ.housecleaning.model.MarginCall;
import com.occ.housecleaning.model.Member;
import com.occ.housecleaning.model.MemberLedgerBalance;
import com.occ.housecleaning.model.Position;
import com.occ.housecleaning.repository.MarginCallRepository;
import com.occ.housecleaning.repository.MemberLedgerBalanceRepository;
import com.occ.housecleaning.repository.MemberRepository;
import com.occ.housecleaning.repository.NovatedContractRepository;
import com.occ.housecleaning.repository.PositionRepository;
import com.occ.housecleaning.repository.RawTradeAuditRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

/**
 * Real Spring context + H2. Each test uses its own members and a symbol-specific volatility factor,
 * so tests stay independent of the shared seeded members and of each other.
 */
@SpringBootTest
class RiskEngineIntegrityTest {

    @Autowired ClearingService clearingService;
    @Autowired RiskService riskService;
    @Autowired MemberRepository memberRepository;
    @Autowired MemberLedgerBalanceRepository balanceRepository;
    @Autowired PositionRepository positionRepository;
    @Autowired NovatedContractRepository contractRepository;
    @Autowired RawTradeAuditRepository auditRepository;
    @MockitoSpyBean MarginCallRepository marginCallRepository;

    private String newMember(String cash) {
        String id = "RISK-" + UUID.randomUUID().toString().substring(0, 8);
        memberRepository.save(Member.builder().memberId(id).name(id).active(true)
            .creditLimit(new BigDecimal("1000000000")).build());
        balanceRepository.save(MemberLedgerBalance.builder().memberId(id).cashBalance(new BigDecimal(cash))
            .marginBalance(BigDecimal.ZERO).updatedAt(Instant.now()).build());
        return id;
    }

    private static TradeRequest trade(String symbol, String buyer, String seller, int qty, String price) {
        return new TradeRequest("T-" + UUID.randomUUID(), "CBOE", symbol, qty, new BigDecimal(price),
            buyer, seller, Instant.now().minusSeconds(5));
    }

    private MemberLedgerBalance balance(String id) {
        return balanceRepository.findByMemberId(id).orElseThrow();
    }

    private List<MarginCall> calls(String memberId) {
        return marginCallRepository.findByMemberId(memberId, org.springframework.data.domain.Pageable.unpaged())
            .getContent();
    }

    @Test
    @DisplayName("Novation records positions, margin and a margin call for the member whose cash cannot cover it")
    void novationUpdatesPositionsMarginAndRaisesCall() {
        String symbol = "RISKA260619C00100000";
        riskService.applyShock(symbol, new BigDecimal("0.10"));
        String buyer = newMember("0");
        String seller = newMember("0");

        clearingService.novateTrade(trade(symbol, buyer, seller, 10, "4.50"));

        assertThat(positionRepository.findByMemberIdAndOptionSymbol(buyer, symbol)).get()
            .extracting(Position::getNetQuantity).isEqualTo(10L);
        assertThat(positionRepository.findByMemberIdAndOptionSymbol(seller, symbol)).get()
            .extracting(Position::getNetQuantity).isEqualTo(-10L);

        // premium 10 x 100 x 4.50 = 4500 ; margin 4500 x 0.10 = 450
        assertThat(balance(buyer).getMarginBalance()).isEqualByComparingTo("450");
        assertThat(balance(seller).getMarginBalance()).isEqualByComparingTo("450");

        // Buyer paid the premium: cash -4500 < margin 450 -> call. Seller received it: cash 4500 >= 450 -> none.
        assertThat(calls(buyer)).singleElement().satisfies(c -> {
            assertThat(c.getStatus()).isEqualTo(MarginCallStatus.OPEN);
            assertThat(c.getShortfall()).isEqualByComparingTo("4950");
        });
        assertThat(calls(seller)).isEmpty();
    }

    @Test
    @DisplayName("A volatility shock recomputes margin immediately, opens a call, and closes it when the shock eases")
    void shockOpensAndClosesMarginCall() {
        String symbol = "RISKB260619C00100000";
        riskService.applyShock(symbol, new BigDecimal("0.10"));
        String buyer = newMember("1000000");
        String seller = newMember("0");
        clearingService.novateTrade(trade(symbol, buyer, seller, 10, "4.50"));
        assertThat(calls(seller)).isEmpty(); // cash 4500, margin 450

        var shock = riskService.applyShock(symbol, new BigDecimal("2.00"));
        assertThat(shock.membersRecalculated()).isGreaterThanOrEqualTo(2);

        // margin 4500 x 2.00 = 9000 > seller cash 4500 -> call; buyer cash 995500 still covers it
        assertThat(balance(seller).getMarginBalance()).isEqualByComparingTo("9000");
        assertThat(calls(seller)).singleElement().satisfies(c -> {
            assertThat(c.getStatus()).isEqualTo(MarginCallStatus.OPEN);
            assertThat(c.getShortfall()).isEqualByComparingTo("4500");
        });
        assertThat(calls(buyer)).isEmpty();

        riskService.applyShock(symbol, new BigDecimal("0.10"));

        assertThat(calls(seller)).singleElement().satisfies(c -> {
            assertThat(c.getStatus()).isEqualTo(MarginCallStatus.CLOSED);
            assertThat(c.getClosedAt()).isNotNull();
        });
    }

    @Test
    @DisplayName("A failure in the risk step rolls back the whole novation: legs, audit, cash, positions, margin")
    void riskFailureRollsBackNovation() {
        String symbol = "RISKC260619C00100000";
        riskService.applyShock(symbol, new BigDecimal("0.10"));
        String buyer = newMember("0");
        String seller = newMember("0");
        TradeRequest request = trade(symbol, buyer, seller, 10, "4.50");

        // Buyer's cash (-4500) is below margin, so the risk step tries to save a margin call.
        doThrow(new IllegalStateException("simulated margin-call failure"))
            .when(marginCallRepository).save(any(MarginCall.class));

        assertThatThrownBy(() -> clearingService.novateTrade(request))
            .isInstanceOf(RuntimeException.class).hasMessageContaining("simulated margin-call failure");

        assertThat(contractRepository.findByExternalTradeId(request.externalTradeId())).isEmpty();
        assertThat(auditRepository.findByExternalTradeId(request.externalTradeId()))
            .noneMatch(a -> a.getStatus() == NovationStatus.NOVATED);
        assertThat(balance(buyer).getCashBalance()).isEqualByComparingTo("0");
        assertThat(balance(seller).getCashBalance()).isEqualByComparingTo("0");
        assertThat(balance(buyer).getMarginBalance()).isEqualByComparingTo("0");
        assertThat(positionRepository.findByMemberIdOrderByOptionSymbolAsc(buyer)).isEmpty();
        assertThat(positionRepository.findByMemberIdOrderByOptionSymbolAsc(seller)).isEmpty();
    }
}
