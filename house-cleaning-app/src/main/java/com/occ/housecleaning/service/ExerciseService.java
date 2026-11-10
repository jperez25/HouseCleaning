package com.occ.housecleaning.service;

import com.occ.housecleaning.dto.AssignmentResponse;
import com.occ.housecleaning.dto.ExerciseRequest;
import com.occ.housecleaning.dto.ExerciseResponse;
import com.occ.housecleaning.model.Member;
import com.occ.housecleaning.model.MemberLedgerBalance;
import com.occ.housecleaning.model.MemberLedgerEntry;
import com.occ.housecleaning.model.OptionAssignment;
import com.occ.housecleaning.model.OptionExercise;
import com.occ.housecleaning.model.Position;
import com.occ.housecleaning.repository.MemberLedgerBalanceRepository;
import com.occ.housecleaning.repository.MemberLedgerEntryRepository;
import com.occ.housecleaning.repository.MemberRepository;
import com.occ.housecleaning.repository.OptionAssignmentRepository;
import com.occ.housecleaning.repository.OptionExerciseRepository;
import com.occ.housecleaning.repository.PositionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;

/**
 * Cash-settled exercise of a long option position with pro-rata assignment to short holders.
 * <p>
 * Payout = intrinsic value x quantity x contract multiplier, paid by the assigned short holders to the exerciser.
 * Positions, cash, ledger entries, the exercise record, assignments, margin and margin calls all change in one
 * transaction. An assignee whose cash falls short is not blocked (OCC must honour the exercise); the existing
 * margin-call mechanism picks the shortfall up.
 */
@Service
public class ExerciseService {

    private static final Logger log = LoggerFactory.getLogger(ExerciseService.class);
    private static final int MONEY_SCALE = 4;

    private final MemberRepository memberRepository;
    private final MemberLedgerBalanceRepository balanceRepository;
    private final MemberLedgerEntryRepository entryRepository;
    private final PositionRepository positionRepository;
    private final OptionExerciseRepository exerciseRepository;
    private final OptionAssignmentRepository assignmentRepository;
    private final RiskService riskService;

    public ExerciseService(MemberRepository memberRepository,
                           MemberLedgerBalanceRepository balanceRepository,
                           MemberLedgerEntryRepository entryRepository,
                           PositionRepository positionRepository,
                           OptionExerciseRepository exerciseRepository,
                           OptionAssignmentRepository assignmentRepository,
                           RiskService riskService) {
        this.memberRepository = memberRepository;
        this.balanceRepository = balanceRepository;
        this.entryRepository = entryRepository;
        this.positionRepository = positionRepository;
        this.exerciseRepository = exerciseRepository;
        this.assignmentRepository = assignmentRepository;
        this.riskService = riskService;
    }

    @Transactional
    public ExerciseResponse exercise(ExerciseRequest request) {
        final String memberId = request.memberId();
        final String symbol = request.optionSymbol();
        final long quantity = request.quantity();

        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Clearing member " + memberId + " is not registered"));
        if (!member.isActive()) {
            throw unprocessable("Clearing member " + memberId + " is not active");
        }

        OptionContract contract = OptionContract.parse(symbol);
        BigDecimal intrinsic = contract.intrinsicValue(request.settlementPrice());
        if (intrinsic.signum() == 0) {
            throw unprocessable("Option " + symbol + " is out of the money at " + request.settlementPrice()
                    + " (strike " + contract.strike() + "); exercise rejected");
        }

        // Lock the exerciser and every current short holder in sorted order, like novation does.
        Set<String> lockIds = new TreeSet<>();
        lockIds.add(memberId);
        positionRepository.findByOptionSymbolAndNetQuantityLessThanOrderByMemberIdAsc(symbol, 0)
                .forEach(p -> lockIds.add(p.getMemberId()));
        Map<String, MemberLedgerBalance> balances = new TreeMap<>();
        for (String id : lockIds) {
            balances.put(id, balanceRepository.findByMemberIdForUpdate(id)
                    .orElseThrow(() -> new IllegalStateException("Ledger balance missing for member " + id)));
        }

        // With the locks held positions cannot change underneath us, but a new short holder may have
        // appeared between the first read and the locks; refuse rather than lock out of order.
        List<Position> shorts = positionRepository
                .findByOptionSymbolAndNetQuantityLessThanOrderByMemberIdAsc(symbol, 0);
        if (shorts.stream().anyMatch(p -> !balances.containsKey(p.getMemberId()))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Positions in " + symbol + " changed during exercise; retry");
        }

        Position longPosition = positionRepository.findByMemberIdAndOptionSymbol(memberId, symbol)
                .filter(p -> p.getNetQuantity() >= quantity)
                .orElseThrow(() -> unprocessable("Member " + memberId + " does not hold a long position of "
                        + quantity + " in " + symbol));

        Map<String, Long> allocation = allocateProRata(shorts, quantity);

        Instant now = Instant.now();
        String exerciseId = "EXR-" + UUID.randomUUID();
        BigDecimal totalPayout = money(intrinsic, quantity);

        longPosition.setNetQuantity(longPosition.getNetQuantity() - quantity);
        longPosition.setUpdatedAt(now);
        positionRepository.save(longPosition);

        List<OptionAssignment> assignments = new ArrayList<>();
        for (Position shortPosition : shorts) {
            long assigned = allocation.getOrDefault(shortPosition.getMemberId(), 0L);
            if (assigned == 0) {
                continue;
            }
            BigDecimal amount = money(intrinsic, assigned);
            shortPosition.setNetQuantity(shortPosition.getNetQuantity() + assigned);
            shortPosition.setUpdatedAt(now);
            positionRepository.save(shortPosition);

            postCash(balances.get(shortPosition.getMemberId()), exerciseId, amount.negate(), "DEBIT",
                    "Assigned exercise payout", now);
            assignments.add(assignmentRepository.save(OptionAssignment.builder()
                    .exerciseId(exerciseId).memberId(shortPosition.getMemberId())
                    .quantity(assigned).amount(amount).build()));
        }
        postCash(balances.get(memberId), exerciseId, totalPayout, "CREDIT", "Exercise payout received", now);

        exerciseRepository.save(OptionExercise.builder()
                .exerciseId(exerciseId).memberId(memberId).optionSymbol(symbol).quantity(request.quantity())
                .strike(contract.strike()).settlementPrice(request.settlementPrice())
                .intrinsicValue(intrinsic).totalPayout(totalPayout).createdAt(now).build());

        riskService.refreshMargins(balances.values(), now);

        log.info("Exercise {}: member {} exercised {} x {} at {}, payout {} assigned to {} member(s)",
                exerciseId, memberId, quantity, symbol, request.settlementPrice(), totalPayout, assignments.size());

        return new ExerciseResponse(exerciseId, memberId, symbol, request.quantity(), contract.strike(),
                request.settlementPrice(), intrinsic, totalPayout, now,
                assignments.stream().map(a -> new AssignmentResponse(a.getMemberId(), a.getQuantity(), a.getAmount()))
                        .toList());
    }

    /**
     * Splits {@code quantity} across short holders in proportion to their short size. Each gets the floor of
     * its exact share; leftover contracts go to the largest fractional remainders, ties broken by member id,
     * so the result is deterministic. Never assigns more than a holder is short.
     */
    static Map<String, Long> allocateProRata(List<Position> shorts, long quantity) {
        long totalShort = shorts.stream().mapToLong(p -> -p.getNetQuantity()).sum();
        if (totalShort < quantity) {
            throw new IllegalStateException("Short interest " + totalShort + " is below exercised quantity " + quantity);
        }
        Map<String, Long> result = new TreeMap<>();
        Map<String, Long> remainders = new TreeMap<>();
        long assigned = 0;
        for (Position p : shorts) {
            long weighted = quantity * -p.getNetQuantity();
            long share = weighted / totalShort;
            result.put(p.getMemberId(), share);
            remainders.put(p.getMemberId(), weighted % totalShort);
            assigned += share;
        }
        List<String> byRemainder = remainders.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder())
                        .thenComparing(Map.Entry.comparingByKey()))
                .map(Map.Entry::getKey).toList();
        for (int i = 0; assigned < quantity; i++, assigned++) {
            result.merge(byRemainder.get(i), 1L, Long::sum);
        }
        return result;
    }

    private static BigDecimal money(BigDecimal perShare, long contracts) {
        return perShare.multiply(BigDecimal.valueOf(contracts)).multiply(ClearingService.CONTRACT_MULTIPLIER)
                .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private void postCash(MemberLedgerBalance balance, String exerciseId, BigDecimal change,
                          String entryType, String description, Instant now) {
        BigDecimal newCash = balance.getCashBalance().add(change);
        balance.setCashBalance(newCash);
        balance.setUpdatedAt(now);
        balanceRepository.save(balance);
        entryRepository.save(MemberLedgerEntry.builder()
                .memberId(balance.getMemberId()).externalTradeId(exerciseId).entryType(entryType)
                .amount(change.abs()).balanceAfter(newCash)
                .description(description + " for " + exerciseId).createdAt(now).build());
    }

    private static ResponseStatusException unprocessable(String message) {
        return new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, message);
    }
}
