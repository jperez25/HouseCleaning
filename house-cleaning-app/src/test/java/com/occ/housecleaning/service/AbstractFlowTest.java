package com.occ.housecleaning.service;

import com.occ.housecleaning.dto.TradeRequest;
import com.occ.housecleaning.model.Member;
import com.occ.housecleaning.model.MemberLedgerBalance;
import com.occ.housecleaning.repository.MemberLedgerBalanceRepository;
import com.occ.housecleaning.repository.MemberRepository;
import com.occ.housecleaning.repository.PositionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Real Spring context + H2. Every test creates its own members and uses its own option symbol (with a
 * symbol-specific volatility factor), so tests stay independent of the shared seeded data and of each other.
 */
@SpringBootTest
abstract class AbstractFlowTest {

    @Autowired ClearingService clearingService;
    @Autowired RiskService riskService;
    @Autowired MemberRepository memberRepository;
    @Autowired MemberLedgerBalanceRepository balanceRepository;
    @Autowired PositionRepository positionRepository;

    protected String newMember(String cash, String creditLimit) {
        String id = "FLOW-" + UUID.randomUUID().toString().substring(0, 8);
        memberRepository.save(Member.builder().memberId(id).name(id).active(true)
            .creditLimit(new BigDecimal(creditLimit)).build());
        balanceRepository.save(MemberLedgerBalance.builder().memberId(id).cashBalance(new BigDecimal(cash))
            .marginBalance(BigDecimal.ZERO).updatedAt(Instant.now()).build());
        return id;
    }

    protected String newMember() {
        return newMember("1000000", "1000000000");
    }

    /** Novates a trade and returns its external id. */
    protected String trade(String symbol, String buyer, String seller, int qty, String price) {
        String id = "T-" + UUID.randomUUID();
        clearingService.novateTrade(new TradeRequest(id, "CBOE", symbol, qty, new BigDecimal(price),
            buyer, seller, Instant.now().minusSeconds(5)));
        return id;
    }

    protected void fixVolatility(String symbol) {
        riskService.applyShock(symbol, new BigDecimal("0.10"));
    }

    protected MemberLedgerBalance balance(String id) {
        return balanceRepository.findByMemberId(id).orElseThrow();
    }

    protected long netQty(String member, String symbol) {
        return positionRepository.findByMemberIdAndOptionSymbol(member, symbol).orElseThrow().getNetQuantity();
    }

    protected void setActive(String memberId, boolean active) {
        Member m = memberRepository.findById(memberId).orElseThrow();
        m.setActive(active);
        memberRepository.save(m);
    }

    protected void setCreditLimit(String memberId, String limit) {
        Member m = memberRepository.findById(memberId).orElseThrow();
        m.setCreditLimit(new BigDecimal(limit));
        memberRepository.save(m);
    }
}
