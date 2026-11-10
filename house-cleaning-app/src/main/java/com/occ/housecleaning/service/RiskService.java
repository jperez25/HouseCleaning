package com.occ.housecleaning.service;

import com.occ.housecleaning.dto.MarginCallStatus;
import com.occ.housecleaning.dto.TradeRequest;
import com.occ.housecleaning.dto.VolatilityShockResponse;
import com.occ.housecleaning.model.MarginCall;
import com.occ.housecleaning.model.MemberLedgerBalance;
import com.occ.housecleaning.model.Position;
import com.occ.housecleaning.repository.MarginCallRepository;
import com.occ.housecleaning.repository.MemberLedgerBalanceRepository;
import com.occ.housecleaning.repository.PositionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Tracks net positions, derives margin from them and raises margin calls.
 * <p>
 * Margin per position is {@code |netQuantity| x contract multiplier x last price x volatility factor}; a member's
 * {@code marginBalance} is the sum over their positions. A margin call is open while {@code marginBalance}
 * exceeds the member's cash balance.
 * <p>
 * Every mutating method joins the caller's transaction, so when called from novation a failure here rolls
 * back the trade, ledger movements, positions, margin and margin call together.
 */
@Service
public class RiskService {

    private static final Logger log = LoggerFactory.getLogger(RiskService.class);
    private static final int MONEY_SCALE = 4;

    private final PositionRepository positionRepository;
    private final MarginCallRepository marginCallRepository;
    private final MemberLedgerBalanceRepository balanceRepository;
    private final VolatilityProvider volatilityProvider;

    public RiskService(PositionRepository positionRepository,
                       MarginCallRepository marginCallRepository,
                       MemberLedgerBalanceRepository balanceRepository,
                       VolatilityProvider volatilityProvider) {
        this.positionRepository = positionRepository;
        this.marginCallRepository = marginCallRepository;
        this.balanceRepository = balanceRepository;
        this.volatilityProvider = volatilityProvider;
    }

    /**
     * Applies a novated trade to both members' positions and refreshes their margin and margin calls.
     * The caller must already hold the row locks on both balances, which serializes position writes per member.
     */
    @Transactional
    public void onNovation(TradeRequest trade, MemberLedgerBalance buyerBalance,
                           MemberLedgerBalance sellerBalance, Instant now) {
        adjustPosition(trade.buyerMemberId(), trade.optionSymbol(), trade.quantity(), trade.executionPrice(), now);
        adjustPosition(trade.sellerMemberId(), trade.optionSymbol(), -trade.quantity(), trade.executionPrice(), now);
        refreshMargin(buyerBalance, now);
        refreshMargin(sellerBalance, now);
    }

    /**
     * Sets a volatility shock (global when {@code optionSymbol} is null) and immediately recomputes margin
     * for every member holding a position. Balances are locked in sorted order, like novation, to avoid deadlocks.
     * If the transaction rolls back the in-memory shock is restored.
     */
    @Transactional
    public VolatilityShockResponse applyShock(String optionSymbol, BigDecimal factor) {
        Runnable undo = volatilityProvider.applyShock(optionSymbol, factor);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    undo.run();
                }
            }
        });

        Instant now = Instant.now();
        List<String> memberIds = positionRepository.findDistinctMemberIds();
        for (String memberId : memberIds) {
            MemberLedgerBalance balance = balanceRepository.findByMemberIdForUpdate(memberId)
                    .orElseThrow(() -> new IllegalStateException("Ledger balance missing for member " + memberId));
            refreshMargin(balance, now);
        }
        log.info("Volatility shock {} applied for {}; recalculated margin for {} members",
                factor, optionSymbol == null ? "ALL symbols" : optionSymbol, memberIds.size());
        return new VolatilityShockResponse(optionSymbol, factor, memberIds.size());
    }

    @Transactional(readOnly = true)
    public List<Position> getPositions(String memberId) {
        return positionRepository.findByMemberIdOrderByOptionSymbolAsc(memberId);
    }

    @Transactional(readOnly = true)
    public Page<MarginCall> getMarginCalls(String memberId, Pageable pageable) {
        return marginCallRepository.findByMemberId(memberId, pageable);
    }

    public BigDecimal factorFor(String optionSymbol) {
        return volatilityProvider.factor(optionSymbol);
    }

    /** Margin required by a single position at the current volatility factor. */
    public BigDecimal marginFor(Position position) {
        return BigDecimal.valueOf(Math.abs(position.getNetQuantity()))
                .multiply(ClearingService.CONTRACT_MULTIPLIER)
                .multiply(position.getLastPrice())
                .multiply(volatilityProvider.factor(position.getOptionSymbol()))
                .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private void adjustPosition(String memberId, String optionSymbol, long delta, BigDecimal price, Instant now) {
        Position position = positionRepository.findByMemberIdAndOptionSymbol(memberId, optionSymbol)
                .orElseGet(() -> Position.builder().memberId(memberId).optionSymbol(optionSymbol).netQuantity(0).build());
        position.setNetQuantity(position.getNetQuantity() + delta);
        position.setLastPrice(price);
        position.setUpdatedAt(now);
        positionRepository.save(position);
    }

    /** Recomputes margin and margin calls for balances the caller has already locked (e.g. after an exercise). */
    @Transactional
    public void refreshMargins(Iterable<MemberLedgerBalance> balances, Instant now) {
        balances.forEach(balance -> refreshMargin(balance, now));
    }

    private void refreshMargin(MemberLedgerBalance balance, Instant now) {
        BigDecimal margin = positionRepository.findByMemberIdOrderByOptionSymbolAsc(balance.getMemberId()).stream()
                .map(this::marginFor)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        balance.setMarginBalance(margin);
        balance.setUpdatedAt(now);
        balanceRepository.save(balance);
        evaluateMarginCall(balance, now);
    }

    private void evaluateMarginCall(MemberLedgerBalance balance, Instant now) {
        BigDecimal shortfall = balance.getMarginBalance().subtract(balance.getCashBalance());
        Optional<MarginCall> open = marginCallRepository
                .findFirstByMemberIdAndStatus(balance.getMemberId(), MarginCallStatus.OPEN);

        if (shortfall.signum() > 0) {
            MarginCall call = open.orElseGet(() -> MarginCall.builder()
                    .memberId(balance.getMemberId()).status(MarginCallStatus.OPEN).createdAt(now).build());
            call.setMarginRequired(balance.getMarginBalance());
            call.setCashAvailable(balance.getCashBalance());
            call.setShortfall(shortfall);
            call.setUpdatedAt(now);
            marginCallRepository.save(call);
            if (open.isEmpty()) {
                log.warn("Margin call raised for {}: margin {} exceeds cash {}",
                        balance.getMemberId(), balance.getMarginBalance(), balance.getCashBalance());
            }
        } else {
            open.ifPresent(call -> {
                call.setStatus(MarginCallStatus.CLOSED);
                call.setClosedAt(now);
                call.setUpdatedAt(now);
                marginCallRepository.save(call);
                log.info("Margin call {} closed for {}", call.getId(), balance.getMemberId());
            });
        }
    }
}
