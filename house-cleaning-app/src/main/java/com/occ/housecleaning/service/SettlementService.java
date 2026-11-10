package com.occ.housecleaning.service;

import com.occ.housecleaning.dto.ContractLeg;
import com.occ.housecleaning.dto.LegType;
import com.occ.housecleaning.dto.NovationStatus;
import com.occ.housecleaning.dto.SettlementExceptionStatus;
import com.occ.housecleaning.dto.SettlementFailureReason;
import com.occ.housecleaning.dto.SettlementResponse;
import com.occ.housecleaning.model.Member;
import com.occ.housecleaning.model.MemberLedgerBalance;
import com.occ.housecleaning.model.NovatedContract;
import com.occ.housecleaning.model.SettlementExceptionRecord;
import com.occ.housecleaning.repository.MemberLedgerBalanceRepository;
import com.occ.housecleaning.repository.MemberRepository;
import com.occ.housecleaning.repository.NovatedContractRepository;
import com.occ.housecleaning.repository.SettlementExceptionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Moves a novated trade through its settlement lifecycle.
 * <p>
 * Premium cash already moved at novation, so settlement is the final confirmation that both clearing members
 * can still stand behind the trade: each must be active and have cash no lower than minus their credit limit.
 * <ul>
 *   <li>All checks pass: both legs NOVATED/FAILED -> SETTLED, and any open exceptions for the trade are RESOLVED.</li>
 *   <li>A check fails: both legs -> FAILED and one OPEN exception is recorded per failing member and reason.
 *       The attempt is returned as a FAILED outcome (HTTP 200), and may be retried.</li>
 *   <li>Already SETTLED: 409, since SETTLED is terminal.</li>
 * </ul>
 */
@Service
public class SettlementService {

    private static final Logger log = LoggerFactory.getLogger(SettlementService.class);

    private final NovatedContractRepository contractRepository;
    private final MemberRepository memberRepository;
    private final MemberLedgerBalanceRepository balanceRepository;
    private final SettlementExceptionRepository exceptionRepository;

    public SettlementService(NovatedContractRepository contractRepository,
                             MemberRepository memberRepository,
                             MemberLedgerBalanceRepository balanceRepository,
                             SettlementExceptionRepository exceptionRepository) {
        this.contractRepository = contractRepository;
        this.memberRepository = memberRepository;
        this.balanceRepository = balanceRepository;
        this.exceptionRepository = exceptionRepository;
    }

    @Transactional
    public SettlementResponse settleTrade(String externalTradeId) {
        List<NovatedContract> legs = contractRepository.findByExternalTradeIdForUpdate(externalTradeId);
        if (legs.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Trade " + externalTradeId + " has not been novated");
        }
        if (legs.stream().allMatch(l -> l.getStatus() == NovationStatus.SETTLED)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Trade " + externalTradeId + " is already settled");
        }

        String buyerId = memberOf(legs, LegType.BUYER_LEG);
        String sellerId = memberOf(legs, LegType.SELLER_LEG);

        // Same sorted lock order as novation, so settlement cannot deadlock with trades.
        MemberLedgerBalance first = lockBalance(buyerId.compareTo(sellerId) <= 0 ? buyerId : sellerId);
        MemberLedgerBalance second = lockBalance(buyerId.compareTo(sellerId) <= 0 ? sellerId : buyerId);
        MemberLedgerBalance buyerBalance = first.getMemberId().equals(buyerId) ? first : second;
        MemberLedgerBalance sellerBalance = first.getMemberId().equals(buyerId) ? second : first;

        Instant now = Instant.now();
        List<SettlementExceptionRecord> failures = new ArrayList<>();
        check(externalTradeId, buyerId, buyerBalance, now, failures);
        check(externalTradeId, sellerId, sellerBalance, now, failures);

        if (failures.isEmpty()) {
            legs.forEach(l -> {
                l.setStatus(NovationStatus.SETTLED);
                l.setSettledAt(now);
            });
            exceptionRepository.findByExternalTradeIdAndStatus(externalTradeId, SettlementExceptionStatus.OPEN)
                    .forEach(e -> {
                        e.setStatus(SettlementExceptionStatus.RESOLVED);
                        e.setResolvedAt(now);
                        exceptionRepository.save(e);
                    });
            log.info("Trade {} settled", externalTradeId);
        } else {
            legs.forEach(l -> l.setStatus(NovationStatus.FAILED));
            recordFailures(externalTradeId, failures);
            log.warn("Settlement of trade {} failed with {} exception(s)", externalTradeId, failures.size());
        }
        contractRepository.saveAll(legs);

        NovationStatus outcome = failures.isEmpty() ? NovationStatus.SETTLED : NovationStatus.FAILED;
        return new SettlementResponse(externalTradeId, outcome, now,
                legs.stream().map(SettlementService::toLeg).toList(),
                exceptionRepository.findByExternalTradeIdOrderByIdAsc(externalTradeId).stream()
                        .map(SettlementMapper::toResponse).toList());
    }

    private void check(String tradeId, String memberId, MemberLedgerBalance balance, Instant now,
                       List<SettlementExceptionRecord> failures) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalStateException("Clearing member missing: " + memberId));
        if (!member.isActive()) {
            failures.add(failure(tradeId, memberId, SettlementFailureReason.INACTIVE_MEMBER,
                    "Clearing member " + memberId + " is not active", now));
        } else if (balance.getCashBalance().compareTo(member.getCreditLimit().negate()) < 0) {
            failures.add(failure(tradeId, memberId, SettlementFailureReason.INSUFFICIENT_FUNDS,
                    "Cash balance " + balance.getCashBalance() + " is below minus the credit limit "
                            + member.getCreditLimit(), now));
        }
    }

    /** Skips a failure that is already OPEN for the same trade, member and reason, so retries don't pile up. */
    private void recordFailures(String tradeId, List<SettlementExceptionRecord> failures) {
        List<SettlementExceptionRecord> open =
                exceptionRepository.findByExternalTradeIdAndStatus(tradeId, SettlementExceptionStatus.OPEN);
        failures.stream()
                .filter(f -> open.stream().noneMatch(o ->
                        o.getMemberId().equals(f.getMemberId()) && o.getReason() == f.getReason()))
                .forEach(exceptionRepository::save);
    }

    private static SettlementExceptionRecord failure(String tradeId, String memberId,
                                                     SettlementFailureReason reason, String detail, Instant now) {
        return SettlementExceptionRecord.builder().externalTradeId(tradeId).memberId(memberId)
                .reason(reason).detail(detail).status(SettlementExceptionStatus.OPEN).createdAt(now).build();
    }

    private static String memberOf(List<NovatedContract> legs, LegType type) {
        return legs.stream().filter(l -> l.getLegType() == type).findFirst()
                .orElseThrow(() -> new IllegalStateException("Trade is missing its " + type))
                .getClearingMemberId();
    }

    private MemberLedgerBalance lockBalance(String memberId) {
        return balanceRepository.findByMemberIdForUpdate(memberId)
                .orElseThrow(() -> new IllegalStateException("Ledger balance missing for member " + memberId));
    }

    private static ContractLeg toLeg(NovatedContract c) {
        return new ContractLeg(c.getLegId(), c.getExternalTradeId(), c.getLegType(), c.getBuyerMemberId(),
                c.getSellerMemberId(), c.getOptionSymbol(), c.getQuantity(), c.getExecutionPrice(),
                c.getExecutionTimestamp(), c.getStatus());
    }
}
