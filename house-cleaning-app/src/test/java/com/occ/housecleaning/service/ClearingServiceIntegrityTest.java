package com.occ.housecleaning.service;

import com.occ.housecleaning.config.MemberSeeder;
import com.occ.housecleaning.dto.NovationStatus;
import com.occ.housecleaning.dto.RejectionReason;
import com.occ.housecleaning.dto.TradeRequest;
import com.occ.housecleaning.exception.TradeRejectedException;
import com.occ.housecleaning.model.RawTradeAudit;
import com.occ.housecleaning.repository.MemberLedgerBalanceRepository;
import com.occ.housecleaning.repository.MemberLedgerEntryRepository;
import com.occ.housecleaning.repository.NovatedContractRepository;
import com.occ.housecleaning.repository.RawTradeAuditRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Real Spring context + H2: verifies the guarantees mocks cannot - locking, constraints and rollback.
 */
@SpringBootTest
class ClearingServiceIntegrityTest {

    @Autowired ClearingService clearingService;
    @Autowired RawTradeAuditRepository auditRepository;
    @Autowired NovatedContractRepository contractRepository;
    @Autowired MemberLedgerBalanceRepository balanceRepository;
    @Autowired MemberLedgerEntryRepository entryRepository;

    private static TradeRequest trade(String id, String buyer, String seller, String price, int qty) {
        return new TradeRequest(id, "CBOE", "AAPL260619C00150000", qty, new BigDecimal(price),
                buyer, seller, Instant.now().minusSeconds(5));
    }

    private static String uniqueId() {
        return "T-" + UUID.randomUUID();
    }

    private BigDecimal cash(String memberId) {
        return balanceRepository.findByMemberId(memberId).orElseThrow().getCashBalance();
    }

    @Test
    @DisplayName("Resubmitting a novated trade is rejected, creates no extra legs and moves no extra cash")
    void duplicateSubmissionIsIdempotent() {
        String id = uniqueId();
        BigDecimal buyerBefore = cash("MEM-301");

        clearingService.novateTrade(trade(id, "MEM-301", "MEM-302", "1.00", 1));
        BigDecimal buyerAfterFirst = cash("MEM-301");

        assertThatThrownBy(() -> clearingService.novateTrade(trade(id, "MEM-301", "MEM-302", "1.00", 1)))
            .isInstanceOfSatisfying(TradeRejectedException.class,
                e -> assertThat(e.getReason()).isEqualTo(RejectionReason.DUPLICATE_TRADE));

        assertThat(contractRepository.findByExternalTradeId(id)).hasSize(2);
        assertThat(entryRepository.findByExternalTradeId(id)).hasSize(2);
        assertThat(cash("MEM-301")).isEqualByComparingTo(buyerAfterFirst);
        assertThat(buyerAfterFirst.subtract(buyerBefore)).isEqualByComparingTo("-100.00");

        // Audit keeps both facts; the endpoint-facing lookup prefers the NOVATED record.
        assertThat(auditRepository.findByExternalTradeId(id)).extracting(RawTradeAudit::getStatus)
            .containsExactlyInAnyOrder(NovationStatus.NOVATED, NovationStatus.REJECTED);
        assertThat(clearingService.getTradeAudit(id)).get()
            .extracting(RawTradeAudit::getStatus).isEqualTo(NovationStatus.NOVATED);
    }

    @Test
    @DisplayName("A rejection is audited even though the novation transaction rolls back")
    void rejectionAuditSurvivesRollback() {
        String id = uniqueId();

        assertThatThrownBy(() -> clearingService.novateTrade(trade(id, "MEM-GHOST", "MEM-302", "1.00", 1)))
            .isInstanceOf(TradeRejectedException.class);

        assertThat(auditRepository.findByExternalTradeId(id)).hasSize(1).first()
            .satisfies(a -> {
                assertThat(a.getStatus()).isEqualTo(NovationStatus.REJECTED);
                assertThat(a.getRejectionReason()).isEqualTo(RejectionReason.UNKNOWN_MEMBER);
            });
        assertThat(contractRepository.findByExternalTradeId(id)).isEmpty();
    }

    @Test
    @DisplayName("A rejected trade id can be corrected and resubmitted successfully")
    void rejectedTradeCanBeResubmitted() {
        String id = uniqueId();
        assertThatThrownBy(() -> clearingService.novateTrade(trade(id, MemberSeeder.INACTIVE_MEMBER_ID, "MEM-302", "1.00", 1)))
            .isInstanceOf(TradeRejectedException.class);

        clearingService.novateTrade(trade(id, "MEM-301", "MEM-302", "1.00", 1));

        assertThat(contractRepository.findByExternalTradeId(id)).hasSize(2);
    }

    @Test
    @DisplayName("Zero-credit member cannot buy on credit and the failed trade leaves balances untouched")
    void insufficientFundsLeavesBalancesUntouched() {
        BigDecimal sellerBefore = cash("MEM-401");

        assertThatThrownBy(() -> clearingService.novateTrade(
                trade(uniqueId(), MemberSeeder.LOW_CREDIT_MEMBER_ID, "MEM-401", "1.00", 1)))
            .isInstanceOfSatisfying(TradeRejectedException.class,
                e -> assertThat(e.getReason()).isEqualTo(RejectionReason.INSUFFICIENT_FUNDS));

        assertThat(cash(MemberSeeder.LOW_CREDIT_MEMBER_ID)).isEqualByComparingTo("0");
        assertThat(cash("MEM-401")).isEqualByComparingTo(sellerBefore);
    }

    @Test
    @DisplayName("Concurrent trades on the same members lose no updates (final balance = sum of all premiums)")
    void concurrentTradesLoseNoUpdates() throws Exception {
        final int threads = 16, tradesPerThread = 5;   // 80 trades x $100 premium
        BigDecimal buyerBefore = cash("MEM-501");
        BigDecimal sellerBefore = cash("MEM-502");

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        for (int t = 0; t < threads; t++) {
            // Alternate direction so opposing lock orders are exercised (deadlock check).
            final boolean forward = t % 2 == 0;
            futures.add(pool.submit(() -> {
                start.await();
                for (int i = 0; i < tradesPerThread; i++) {
                    String buyer = forward ? "MEM-501" : "MEM-502";
                    String seller = forward ? "MEM-502" : "MEM-501";
                    clearingService.novateTrade(trade(uniqueId(), buyer, seller, "1.00", 1));
                }
                return null;
            }));
        }
        start.countDown();
        for (Future<?> f : futures) {
            f.get(60, TimeUnit.SECONDS);   // propagates any deadlock / lock-timeout failure
        }
        pool.shutdown();
        assertThat(pool.awaitTermination(5, TimeUnit.SECONDS)).isTrue();

        // Net flow cancels between the two members (equal trades each way) - totals must be conserved.
        BigDecimal netChange = cash("MEM-501").subtract(buyerBefore).add(cash("MEM-502").subtract(sellerBefore));
        assertThat(netChange).as("money is neither created nor destroyed").isEqualByComparingTo("0");
        // 8 forward threads x5 buy, 8 reverse threads x5 sell for MEM-501 => net zero, but every trade must be journaled
        long entries501 = entryRepository.findByMemberId("MEM-501", org.springframework.data.domain.Pageable.unpaged())
            .getTotalElements();
        assertThat(entries501).isGreaterThanOrEqualTo(80);
    }

    @Test
    @DisplayName("Concurrent same-direction trades accumulate exactly (no lost update)")
    void concurrentSameDirectionTradesAccumulateExactly() throws Exception {
        final int trades = 40;
        BigDecimal buyerBefore = cash("MEM-101");
        BigDecimal sellerBefore = cash("MEM-102");

        ExecutorService pool = Executors.newFixedThreadPool(8);
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < trades; i++) {
            futures.add(pool.submit(() ->
                clearingService.novateTrade(trade(uniqueId(), "MEM-101", "MEM-102", "1.00", 1))));
        }
        for (Future<?> f : futures) {
            f.get(60, TimeUnit.SECONDS);
        }
        pool.shutdown();

        BigDecimal expected = new BigDecimal("100").multiply(BigDecimal.valueOf(trades));
        assertThat(cash("MEM-101")).isEqualByComparingTo(buyerBefore.subtract(expected));
        assertThat(cash("MEM-102")).isEqualByComparingTo(sellerBefore.add(expected));
    }
}
