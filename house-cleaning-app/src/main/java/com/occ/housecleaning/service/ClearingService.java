package com.occ.housecleaning.service;

import com.occ.housecleaning.dto.ContractLeg;
import com.occ.housecleaning.dto.LegType;
import com.occ.housecleaning.dto.NovationStatus;
import com.occ.housecleaning.dto.RejectionReason;
import com.occ.housecleaning.dto.TradeNovationResponse;
import com.occ.housecleaning.dto.TradeRequest;
import com.occ.housecleaning.exception.TradeRejectedException;
import com.occ.housecleaning.model.Member;
import com.occ.housecleaning.model.MemberLedgerBalance;
import com.occ.housecleaning.model.MemberLedgerEntry;
import com.occ.housecleaning.model.NovatedContract;
import com.occ.housecleaning.model.RawTradeAudit;
import com.occ.housecleaning.repository.MemberLedgerBalanceRepository;
import com.occ.housecleaning.repository.MemberLedgerEntryRepository;
import com.occ.housecleaning.repository.MemberRepository;
import com.occ.housecleaning.repository.NovatedContractRepository;
import com.occ.housecleaning.repository.RawTradeAuditRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
public class ClearingService {

    private static final Logger log = LoggerFactory.getLogger(ClearingService.class);
    public static final String OCC_PARTICIPANT_ID = "OCC";
    public static final BigDecimal CONTRACT_MULTIPLIER = BigDecimal.valueOf(100);

    private final RawTradeAuditRepository rawTradeAuditRepository;
    private final NovatedContractRepository novatedContractRepository;
    private final MemberLedgerBalanceRepository memberLedgerBalanceRepository;
    private final MemberLedgerEntryRepository memberLedgerEntryRepository;
    private final MemberRepository memberRepository;
    private final TradeAuditRecorder tradeAuditRecorder;
    private final RiskService riskService;
    private final ObjectMapper objectMapper;

    public ClearingService(
            RawTradeAuditRepository rawTradeAuditRepository,
            NovatedContractRepository novatedContractRepository,
            MemberLedgerBalanceRepository memberLedgerBalanceRepository,
            MemberLedgerEntryRepository memberLedgerEntryRepository,
            MemberRepository memberRepository,
            TradeAuditRecorder tradeAuditRecorder,
            RiskService riskService,
            ObjectMapper objectMapper) {
        this.rawTradeAuditRepository = rawTradeAuditRepository;
        this.novatedContractRepository = novatedContractRepository;
        this.memberLedgerBalanceRepository = memberLedgerBalanceRepository;
        this.memberLedgerEntryRepository = memberLedgerEntryRepository;
        this.memberRepository = memberRepository;
        this.tradeAuditRecorder = tradeAuditRecorder;
        this.riskService = riskService;
        this.objectMapper = objectMapper;
    }

    /**
     * Novates an inbound trade execution by interposing OCC as the central counterparty.
     * <p>
     * Business rules, evaluated before any state changes:
     * <ol>
     *   <li>The external trade id must not already be novated (409 DUPLICATE_TRADE).</li>
     *   <li>Buyer and seller must be registered (UNKNOWN_MEMBER) and active (INACTIVE_MEMBER), both 422.</li>
     *   <li>The buyer's cash balance after paying the premium may not fall below minus their credit limit
     *       (INSUFFICIENT_FUNDS, 422). This check runs under a row lock, so it is safe under concurrency.</li>
     * </ol>
     * Every rejection is audited with status REJECTED in a separate transaction so it survives rollback.
     * On success the audit record, both contract legs, both ledger movements, the position updates, the margin
     * recalculation and any margin call commit atomically.
     */
    @Transactional
    public TradeNovationResponse novateTrade(TradeRequest tradeRequest) {
        Objects.requireNonNull(tradeRequest, "tradeRequest cannot be null");
        final String tradeId = tradeRequest.externalTradeId();
        log.info("Novating trade: {}", tradeId);

        Instant now = Instant.now();

        if (novatedContractRepository.existsByExternalTradeId(tradeId)) {
            throw reject(tradeRequest, RejectionReason.DUPLICATE_TRADE, now,
                    "Trade " + tradeId + " has already been novated");
        }

        Member buyer = requireActiveMember(tradeRequest, tradeRequest.buyerMemberId(), now);
        Member seller = requireActiveMember(tradeRequest, tradeRequest.sellerMemberId(), now);

        BigDecimal totalPremium = tradeRequest.executionPrice()
                .multiply(BigDecimal.valueOf(tradeRequest.quantity()))
                .multiply(CONTRACT_MULTIPLIER);

        // Lock both balances in a fixed (sorted) order so opposing trades cannot deadlock.
        MemberLedgerBalance buyerBalance;
        MemberLedgerBalance sellerBalance;
        if (buyer.getMemberId().compareTo(seller.getMemberId()) <= 0) {
            buyerBalance = lockBalance(buyer.getMemberId());
            sellerBalance = lockBalance(seller.getMemberId());
        } else {
            sellerBalance = lockBalance(seller.getMemberId());
            buyerBalance = lockBalance(buyer.getMemberId());
        }

        BigDecimal buyerCashAfter = buyerBalance.getCashBalance().subtract(totalPremium);
        if (buyerCashAfter.compareTo(buyer.getCreditLimit().negate()) < 0) {
            throw reject(tradeRequest, RejectionReason.INSUFFICIENT_FUNDS, now,
                    "Member " + buyer.getMemberId() + " has insufficient funds for premium " + totalPremium);
        }

        rawTradeAuditRepository.save(buildAudit(tradeRequest, NovationStatus.NOVATED, null, now));

        String buyerLegId = "LEG-" + UUID.randomUUID();
        String sellerLegId = "LEG-" + UUID.randomUUID();

        ContractLeg buyerLeg = new ContractLeg(
                buyerLegId, tradeId, LegType.BUYER_LEG,
                tradeRequest.buyerMemberId(), OCC_PARTICIPANT_ID,
                tradeRequest.optionSymbol(), tradeRequest.quantity(), tradeRequest.executionPrice(),
                tradeRequest.executionTimestamp(), NovationStatus.NOVATED);

        ContractLeg sellerLeg = new ContractLeg(
                sellerLegId, tradeId, LegType.SELLER_LEG,
                OCC_PARTICIPANT_ID, tradeRequest.sellerMemberId(),
                tradeRequest.optionSymbol(), tradeRequest.quantity(), tradeRequest.executionPrice(),
                tradeRequest.executionTimestamp(), NovationStatus.NOVATED);

        NovatedContract buyerContract = toContract(buyerLeg, tradeRequest.buyerMemberId(), now);
        NovatedContract sellerContract = toContract(sellerLeg, tradeRequest.sellerMemberId(), now);

        try {
            // Flush now so the (external_trade_id, leg_type) unique constraint fires inside this method.
            novatedContractRepository.saveAllAndFlush(List.of(buyerContract, sellerContract));
        } catch (DataIntegrityViolationException e) {
            throw reject(tradeRequest, RejectionReason.DUPLICATE_TRADE, now,
                    "Trade " + tradeId + " has already been novated");
        }

        applyLedgerMovement(buyerBalance, tradeId, totalPremium.negate(), "DEBIT", "Option premium paid", now);
        applyLedgerMovement(sellerBalance, tradeId, totalPremium, "CREDIT", "Option premium received", now);

        // Same transaction: if position, margin or margin-call handling fails, the whole novation rolls back.
        riskService.onNovation(tradeRequest, buyerBalance, sellerBalance, now);

        log.info("Trade {} novated successfully into Leg 1 (OCC -> {}) and Leg 2 ({} -> OCC) with premium ${}",
                tradeId, buyerLeg.buyerMemberId(), sellerLeg.sellerMemberId(), totalPremium);

        return new TradeNovationResponse(tradeId, NovationStatus.NOVATED, now, buyerLeg, sellerLeg);
    }

    private Member requireActiveMember(TradeRequest request, String memberId, Instant now) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> reject(request, RejectionReason.UNKNOWN_MEMBER, now,
                        "Clearing member " + memberId + " is not registered"));
        if (!member.isActive()) {
            throw reject(request, RejectionReason.INACTIVE_MEMBER, now,
                    "Clearing member " + memberId + " is not active");
        }
        return member;
    }

    private MemberLedgerBalance lockBalance(String memberId) {
        return memberLedgerBalanceRepository.findByMemberIdForUpdate(memberId)
                .orElseThrow(() -> new IllegalStateException("Ledger balance missing for member " + memberId));
    }

    private TradeRejectedException reject(TradeRequest request, RejectionReason reason, Instant now, String message) {
        log.warn("Rejecting trade {}: {} - {}", request.externalTradeId(), reason, message);
        tradeAuditRecorder.recordRejection(buildAudit(request, NovationStatus.REJECTED, reason, now));
        return new TradeRejectedException(reason, message);
    }

    private RawTradeAudit buildAudit(TradeRequest request, NovationStatus status, RejectionReason reason, Instant now) {
        String rawPayload;
        try {
            rawPayload = objectMapper.writeValueAsString(request);
        } catch (Exception e) {
            log.warn("Failed to serialize tradeRequest with Jackson, falling back to toString()", e);
            rawPayload = request.toString();
        }
        return RawTradeAudit.builder()
                .externalTradeId(request.externalTradeId())
                .exchangeCode(request.exchangeCode())
                .rawPayload(rawPayload)
                .receivedAt(now)
                .status(status)
                .rejectionReason(reason)
                .build();
    }

    private NovatedContract toContract(ContractLeg leg, String clearingMemberId, Instant novatedAt) {
        return NovatedContract.builder()
                .legId(leg.legId())
                .externalTradeId(leg.externalTradeId())
                .legType(leg.legType())
                .clearingMemberId(clearingMemberId)
                .counterpartyId(OCC_PARTICIPANT_ID)
                .buyerMemberId(leg.buyerMemberId())
                .sellerMemberId(leg.sellerMemberId())
                .optionSymbol(leg.optionSymbol())
                .quantity(leg.quantity())
                .executionPrice(leg.executionPrice())
                .executionTimestamp(leg.executionTimestamp())
                .novatedAt(novatedAt)
                .status(NovationStatus.NOVATED)
                .build();
    }

    private void applyLedgerMovement(MemberLedgerBalance balance, String tradeId, BigDecimal netCashChange,
                                     String entryType, String description, Instant now) {
        BigDecimal newCashBalance = balance.getCashBalance().add(netCashChange);
        balance.setCashBalance(newCashBalance);
        balance.setUpdatedAt(now);
        memberLedgerBalanceRepository.save(balance);

        memberLedgerEntryRepository.save(MemberLedgerEntry.builder()
                .memberId(balance.getMemberId())
                .externalTradeId(tradeId)
                .entryType(entryType)
                .amount(netCashChange.abs())
                .balanceAfter(newCashBalance)
                .description(description + " for trade " + tradeId)
                .createdAt(now)
                .build());
    }

    /** Returns the NOVATED audit if the trade was novated, otherwise the most recent (rejected) attempt. */
    public Optional<RawTradeAudit> getTradeAudit(String externalTradeId) {
        return rawTradeAuditRepository
                .findFirstByExternalTradeIdAndStatusOrderByIdDesc(externalTradeId, NovationStatus.NOVATED)
                .or(() -> rawTradeAuditRepository.findFirstByExternalTradeIdOrderByIdDesc(externalTradeId));
    }

    public List<NovatedContract> getNovatedContracts(String externalTradeId) {
        return novatedContractRepository.findByExternalTradeId(externalTradeId);
    }

    public Optional<MemberLedgerBalance> getMemberBalance(String memberId) {
        return memberLedgerBalanceRepository.findByMemberId(memberId);
    }

    public boolean memberExists(String memberId) {
        return memberRepository.existsById(memberId);
    }

    public Page<MemberLedgerEntry> getMemberLedgerEntries(String memberId, Pageable pageable) {
        return memberLedgerEntryRepository.findByMemberId(memberId, pageable);
    }
}
