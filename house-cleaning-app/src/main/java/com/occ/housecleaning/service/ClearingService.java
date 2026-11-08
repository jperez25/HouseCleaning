package com.occ.housecleaning.service;

import com.occ.housecleaning.dto.ContractLeg;
import com.occ.housecleaning.dto.LegType;
import com.occ.housecleaning.dto.NovationStatus;
import com.occ.housecleaning.dto.TradeNovationResponse;
import com.occ.housecleaning.dto.TradeRequest;
import com.occ.housecleaning.model.MemberLedgerBalance;
import com.occ.housecleaning.model.MemberLedgerEntry;
import com.occ.housecleaning.model.NovatedContract;
import com.occ.housecleaning.model.RawTradeAudit;
import com.occ.housecleaning.repository.MemberLedgerBalanceRepository;
import com.occ.housecleaning.repository.MemberLedgerEntryRepository;
import com.occ.housecleaning.repository.NovatedContractRepository;
import com.occ.housecleaning.repository.RawTradeAuditRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

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
    private final ObjectMapper objectMapper;

    public ClearingService(
            RawTradeAuditRepository rawTradeAuditRepository,
            NovatedContractRepository novatedContractRepository,
            MemberLedgerBalanceRepository memberLedgerBalanceRepository,
            MemberLedgerEntryRepository memberLedgerEntryRepository,
            ObjectMapper objectMapper) {
        this.rawTradeAuditRepository = rawTradeAuditRepository;
        this.novatedContractRepository = novatedContractRepository;
        this.memberLedgerBalanceRepository = memberLedgerBalanceRepository;
        this.memberLedgerEntryRepository = memberLedgerEntryRepository;
        this.objectMapper = objectMapper != null ? objectMapper : new JsonMapper();
    }

    /**
     * Novates an inbound trade execution by interposing OCC as the central counterparty:
     * - Records the raw payload in raw_trades_audit for regulatory compliance and audit trails.
     * - Inserts Leg 1 and Leg 2 into novated_contracts linking them to their clearing members.
     * - Updates buyer and seller cash ledger balances immediately for option premium exchange.
     *
     * @param tradeRequest incoming trade execution request
     * @return trade novation response containing the two novated contract legs
     */
    @Transactional
    public TradeNovationResponse novateTrade(TradeRequest tradeRequest) {
        Objects.requireNonNull(tradeRequest, "tradeRequest cannot be null");
        log.info("Novating trade: {}", tradeRequest.externalTradeId());

        Instant now = Instant.now();

        // 1. Audit inbound raw payload for regulatory compliance
        String rawPayload;
        try {
            rawPayload = objectMapper.writeValueAsString(tradeRequest);
        } catch (Exception e) {
            log.warn("Failed to serialize tradeRequest with Jackson, falling back to toString()", e);
            rawPayload = tradeRequest.toString();
        }

        RawTradeAudit auditRecord = RawTradeAudit.builder()
                .externalTradeId(tradeRequest.externalTradeId())
                .exchangeCode(tradeRequest.exchangeCode())
                .rawPayload(rawPayload)
                .receivedAt(now)
                .status("NOVATED")
                .build();
        rawTradeAuditRepository.save(auditRecord);

        // 2. Generate Leg 1 (Buyer-facing) and Leg 2 (Seller-facing)
        String buyerLegId = "LEG-" + UUID.randomUUID();
        String sellerLegId = "LEG-" + UUID.randomUUID();

        ContractLeg buyerLeg = new ContractLeg(
                buyerLegId,
                tradeRequest.externalTradeId(),
                LegType.BUYER_LEG,
                tradeRequest.buyerMemberId(),
                OCC_PARTICIPANT_ID,
                tradeRequest.optionSymbol(),
                tradeRequest.quantity(),
                tradeRequest.executionPrice(),
                tradeRequest.executionTimestamp(),
                NovationStatus.NOVATED
        );

        ContractLeg sellerLeg = new ContractLeg(
                sellerLegId,
                tradeRequest.externalTradeId(),
                LegType.SELLER_LEG,
                OCC_PARTICIPANT_ID,
                tradeRequest.sellerMemberId(),
                tradeRequest.optionSymbol(),
                tradeRequest.quantity(),
                tradeRequest.executionPrice(),
                tradeRequest.executionTimestamp(),
                NovationStatus.NOVATED
        );

        // Persist Leg 1 and Leg 2 into novated_contracts
        NovatedContract buyerContract = NovatedContract.builder()
                .legId(buyerLegId)
                .externalTradeId(tradeRequest.externalTradeId())
                .legType(LegType.BUYER_LEG)
                .clearingMemberId(tradeRequest.buyerMemberId())
                .counterpartyId(OCC_PARTICIPANT_ID)
                .buyerMemberId(tradeRequest.buyerMemberId())
                .sellerMemberId(OCC_PARTICIPANT_ID)
                .optionSymbol(tradeRequest.optionSymbol())
                .quantity(tradeRequest.quantity())
                .executionPrice(tradeRequest.executionPrice())
                .executionTimestamp(tradeRequest.executionTimestamp())
                .novatedAt(now)
                .status(NovationStatus.NOVATED)
                .build();

        NovatedContract sellerContract = NovatedContract.builder()
                .legId(sellerLegId)
                .externalTradeId(tradeRequest.externalTradeId())
                .legType(LegType.SELLER_LEG)
                .clearingMemberId(tradeRequest.sellerMemberId())
                .counterpartyId(OCC_PARTICIPANT_ID)
                .buyerMemberId(OCC_PARTICIPANT_ID)
                .sellerMemberId(tradeRequest.sellerMemberId())
                .optionSymbol(tradeRequest.optionSymbol())
                .quantity(tradeRequest.quantity())
                .executionPrice(tradeRequest.executionPrice())
                .executionTimestamp(tradeRequest.executionTimestamp())
                .novatedAt(now)
                .status(NovationStatus.NOVATED)
                .build();

        novatedContractRepository.saveAll(List.of(buyerContract, sellerContract));

        // 3. Settle immediate cash premium across member ledger balances
        BigDecimal totalPremium = tradeRequest.executionPrice()
                .multiply(BigDecimal.valueOf(tradeRequest.quantity()))
                .multiply(CONTRACT_MULTIPLIER);

        updateMemberBalance(tradeRequest.buyerMemberId(), tradeRequest.externalTradeId(), totalPremium.negate(), "DEBIT", "Option premium paid", now);
        updateMemberBalance(tradeRequest.sellerMemberId(), tradeRequest.externalTradeId(), totalPremium, "CREDIT", "Option premium received", now);

        log.info("Trade {} novated successfully into Leg 1 (OCC -> {}) and Leg 2 ({} -> OCC) with premium ${}",
                tradeRequest.externalTradeId(),
                buyerLeg.buyerMemberId(),
                sellerLeg.sellerMemberId(),
                totalPremium);

        return new TradeNovationResponse(
                tradeRequest.externalTradeId(),
                NovationStatus.NOVATED,
                now,
                buyerLeg,
                sellerLeg
        );
    }

    private void updateMemberBalance(String memberId, String tradeId, BigDecimal netCashChange, String entryType, String description, Instant now) {
        MemberLedgerBalance ledgerBalance = memberLedgerBalanceRepository.findByMemberId(memberId)
                .orElseGet(() -> MemberLedgerBalance.builder()
                        .memberId(memberId)
                        .cashBalance(BigDecimal.ZERO)
                        .marginBalance(BigDecimal.ZERO)
                        .updatedAt(now)
                        .build());

        BigDecimal newCashBalance = ledgerBalance.getCashBalance().add(netCashChange);
        ledgerBalance.setCashBalance(newCashBalance);
        ledgerBalance.setUpdatedAt(now);
        memberLedgerBalanceRepository.save(ledgerBalance);

        MemberLedgerEntry entry = MemberLedgerEntry.builder()
                .memberId(memberId)
                .externalTradeId(tradeId)
                .entryType(entryType)
                .amount(netCashChange.abs())
                .balanceAfter(newCashBalance)
                .description(description + " for trade " + tradeId)
                .createdAt(now)
                .build();
        memberLedgerEntryRepository.save(entry);
    }

    public Optional<RawTradeAudit> getTradeAudit(String externalTradeId) {
        return rawTradeAuditRepository.findByExternalTradeId(externalTradeId);
    }

    public List<NovatedContract> getNovatedContracts(String externalTradeId) {
        return novatedContractRepository.findByExternalTradeId(externalTradeId);
    }

    public Optional<MemberLedgerBalance> getMemberBalance(String memberId) {
        return memberLedgerBalanceRepository.findByMemberId(memberId);
    }

    public List<MemberLedgerEntry> getMemberLedgerEntries(String memberId) {
        return memberLedgerEntryRepository.findByMemberId(memberId);
    }
}
