package com.occ.housecleaning.service;

import com.occ.housecleaning.dto.MarginCallStatus;
import com.occ.housecleaning.dto.TradeRequest;
import com.occ.housecleaning.model.MarginCall;
import com.occ.housecleaning.model.MemberLedgerBalance;
import com.occ.housecleaning.model.Position;
import com.occ.housecleaning.repository.MarginCallRepository;
import com.occ.housecleaning.repository.MemberLedgerBalanceRepository;
import com.occ.housecleaning.repository.PositionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Pure unit tests: the volatility factor is a lambda-style test double, so margin maths is fully controlled. */
@ExtendWith(MockitoExtension.class)
class RiskServiceTest {

    private static final String SYMBOL = "AAPL260619C00150000";

    @Mock PositionRepository positionRepository;
    @Mock MarginCallRepository marginCallRepository;
    @Mock MemberLedgerBalanceRepository balanceRepository;

    private BigDecimal factor = new BigDecimal("0.10");
    private final List<Position> stored = new ArrayList<>();
    private RiskService riskService;

    @BeforeEach
    void setUp() {
        VolatilityProvider provider = new VolatilityProvider() {
            @Override public BigDecimal factor(String optionSymbol) { return factor; }
            @Override public Runnable applyShock(String optionSymbol, BigDecimal f) { factor = f; return () -> { }; }
        };
        riskService = new RiskService(positionRepository, marginCallRepository, balanceRepository, provider);

        // Tiny in-memory position store so refreshMargin sees what adjustPosition saved.
        lenient().when(positionRepository.findByMemberIdAndOptionSymbol(any(), any())).thenAnswer(inv ->
            stored.stream().filter(p -> p.getMemberId().equals(inv.getArgument(0))
                && p.getOptionSymbol().equals(inv.getArgument(1))).findFirst());
        lenient().when(positionRepository.save(any(Position.class))).thenAnswer(inv -> {
            Position p = inv.getArgument(0);
            if (!stored.contains(p)) stored.add(p);
            return p;
        });
        lenient().when(positionRepository.findByMemberIdOrderByOptionSymbolAsc(any())).thenAnswer(inv ->
            stored.stream().filter(p -> p.getMemberId().equals(inv.getArgument(0))).toList());
        lenient().when(marginCallRepository.findFirstByMemberIdAndStatus(any(), any())).thenReturn(Optional.empty());
    }

    private static TradeRequest trade(int qty, String price) {
        return new TradeRequest("T-1", "CBOE", SYMBOL, qty, new BigDecimal(price), "BUYER", "SELLER",
            Instant.parse("2026-11-05T15:30:00Z"));
    }

    private static MemberLedgerBalance balance(String id, String cash) {
        return MemberLedgerBalance.builder().memberId(id).cashBalance(new BigDecimal(cash))
            .marginBalance(BigDecimal.ZERO).updatedAt(Instant.now()).build();
    }

    @Test
    @DisplayName("Buyer goes long and seller goes short by the traded quantity")
    void positionsMoveInOppositeDirections() {
        riskService.onNovation(trade(10, "4.50"), balance("BUYER", "0"), balance("SELLER", "0"), Instant.now());

        assertThat(stored).extracting(Position::getMemberId, Position::getNetQuantity)
            .containsExactlyInAnyOrder(org.assertj.core.groups.Tuple.tuple("BUYER", 10L),
                org.assertj.core.groups.Tuple.tuple("SELLER", -10L));
    }

    @Test
    @DisplayName("Margin = |netQty| x 100 x last price x volatility factor, for long and short alike")
    void marginUsesAbsoluteQuantity() {
        MemberLedgerBalance buyer = balance("BUYER", "1000000");
        MemberLedgerBalance seller = balance("SELLER", "1000000");

        riskService.onNovation(trade(10, "4.50"), buyer, seller, Instant.now());

        // 10 x 100 x 4.50 x 0.10 = 450
        assertThat(buyer.getMarginBalance()).isEqualByComparingTo("450.0000");
        assertThat(seller.getMarginBalance()).isEqualByComparingTo("450.0000");
    }

    @Test
    @DisplayName("Offsetting trades net the position down and reduce margin")
    void offsettingTradeReducesMargin() {
        MemberLedgerBalance buyer = balance("BUYER", "1000000");
        MemberLedgerBalance seller = balance("SELLER", "1000000");
        riskService.onNovation(trade(10, "4.50"), buyer, seller, Instant.now());

        // BUYER now sells 4 back to SELLER.
        TradeRequest reverse = new TradeRequest("T-2", "CBOE", SYMBOL, 4, new BigDecimal("4.50"),
            "SELLER", "BUYER", Instant.parse("2026-11-05T15:31:00Z"));
        riskService.onNovation(reverse, seller, buyer, Instant.now());

        // net 6 for both -> 6 x 100 x 4.50 x 0.10 = 270
        assertThat(buyer.getMarginBalance()).isEqualByComparingTo("270.0000");
        assertThat(seller.getMarginBalance()).isEqualByComparingTo("270.0000");
    }

    @Test
    @DisplayName("Margin call is raised when margin exceeds cash")
    void marginCallRaisedWhenMarginExceedsCash() {
        riskService.onNovation(trade(10, "4.50"), balance("BUYER", "449.9999"), balance("SELLER", "1000000"), Instant.now());

        ArgumentCaptor<MarginCall> captor = ArgumentCaptor.forClass(MarginCall.class);
        verify(marginCallRepository).save(captor.capture());
        MarginCall call = captor.getValue();
        assertThat(call.getMemberId()).isEqualTo("BUYER");
        assertThat(call.getStatus()).isEqualTo(MarginCallStatus.OPEN);
        assertThat(call.getShortfall()).isEqualByComparingTo("0.0001");
    }

    @Test
    @DisplayName("No margin call when margin equals cash (call only when margin EXCEEDS cash)")
    void noMarginCallAtExactBoundary() {
        riskService.onNovation(trade(10, "4.50"), balance("BUYER", "450"), balance("SELLER", "450"), Instant.now());

        verify(marginCallRepository, never()).save(any());
    }

    @Test
    @DisplayName("An existing open call is updated, not duplicated")
    void openCallIsUpdated() {
        MarginCall open = MarginCall.builder().id(7L).memberId("BUYER").status(MarginCallStatus.OPEN)
            .createdAt(Instant.parse("2026-11-05T10:00:00Z")).build();
        when(marginCallRepository.findFirstByMemberIdAndStatus("BUYER", MarginCallStatus.OPEN))
            .thenReturn(Optional.of(open));

        riskService.onNovation(trade(10, "4.50"), balance("BUYER", "0"), balance("SELLER", "1000000"), Instant.now());

        verify(marginCallRepository).save(open);
        assertThat(open.getId()).isEqualTo(7L);
        assertThat(open.getShortfall()).isEqualByComparingTo("450");
    }

    @Test
    @DisplayName("An open call is closed once cash covers margin")
    void openCallClosedWhenCovered() {
        MarginCall open = MarginCall.builder().id(7L).memberId("BUYER").status(MarginCallStatus.OPEN)
            .createdAt(Instant.parse("2026-11-05T10:00:00Z")).build();
        when(marginCallRepository.findFirstByMemberIdAndStatus("BUYER", MarginCallStatus.OPEN))
            .thenReturn(Optional.of(open));

        riskService.onNovation(trade(10, "4.50"), balance("BUYER", "1000"), balance("SELLER", "1000"), Instant.now());

        assertThat(open.getStatus()).isEqualTo(MarginCallStatus.CLOSED);
        assertThat(open.getClosedAt()).isNotNull();
    }

    @Test
    @DisplayName("A higher volatility factor raises margin proportionally")
    void controllableVolatilityScalesMargin() {
        factor = new BigDecimal("0.30");
        MemberLedgerBalance buyer = balance("BUYER", "1000000");

        riskService.onNovation(trade(10, "4.50"), buyer, balance("SELLER", "1000000"), Instant.now());

        assertThat(buyer.getMarginBalance()).isEqualByComparingTo("1350.0000");
    }
}
