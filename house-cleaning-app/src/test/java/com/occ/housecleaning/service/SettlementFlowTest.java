package com.occ.housecleaning.service;

import com.occ.housecleaning.dto.NovationStatus;
import com.occ.housecleaning.dto.SettlementExceptionStatus;
import com.occ.housecleaning.dto.SettlementFailureReason;
import com.occ.housecleaning.dto.SettlementResponse;
import com.occ.housecleaning.model.NovatedContract;
import com.occ.housecleaning.model.Position;
import com.occ.housecleaning.repository.NovatedContractRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SettlementFlowTest extends AbstractFlowTest {

    @Autowired SettlementService settlementService;
    @Autowired QueryService queryService;
    @Autowired NovatedContractRepository contractRepository;

    @Test
    @DisplayName("NOVATED -> SETTLED, and SETTLED is terminal (second attempt is a 409)")
    void settleHappyPathAndTerminalState() {
        String symbol = "SETA260619C00100000";
        String tradeId = trade(symbol, newMember(), newMember(), 1, "1.00");
        assertThat(contractRepository.findByExternalTradeId(tradeId)).extracting(NovatedContract::getStatus)
            .containsOnly(NovationStatus.NOVATED);

        SettlementResponse response = settlementService.settleTrade(tradeId);

        assertThat(response.status()).isEqualTo(NovationStatus.SETTLED);
        assertThat(response.legs()).hasSize(2).allMatch(l -> l.status() == NovationStatus.SETTLED);
        assertThat(response.exceptions()).isEmpty();
        assertThat(contractRepository.findByExternalTradeId(tradeId))
            .allSatisfy(l -> assertThat(l.getSettledAt()).isNotNull());

        assertThatThrownBy(() -> settlementService.settleTrade(tradeId))
            .isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode().value()).isEqualTo(409));
    }

    @Test
    @DisplayName("Settling a trade that was never novated is a 404")
    void unknownTrade() {
        assertThatThrownBy(() -> settlementService.settleTrade("T-DOES-NOT-EXIST"))
            .isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode().value()).isEqualTo(404));
    }

    @Test
    @DisplayName("An inactive member fails settlement; retries do not duplicate the exception; success resolves it")
    void inactiveMemberFailsThenRetrySucceeds() {
        String symbol = "SETB260619C00100000";
        String buyer = newMember();
        String seller = newMember();
        String tradeId = trade(symbol, buyer, seller, 1, "1.00");
        setActive(seller, false);

        SettlementResponse failed = settlementService.settleTrade(tradeId);
        SettlementResponse failedAgain = settlementService.settleTrade(tradeId);

        assertThat(failed.status()).isEqualTo(NovationStatus.FAILED);
        assertThat(failed.legs()).allMatch(l -> l.status() == NovationStatus.FAILED);
        assertThat(failedAgain.exceptions()).hasSize(1);
        assertThat(failedAgain.exceptions().getFirst()).satisfies(e -> {
            assertThat(e.memberId()).isEqualTo(seller);
            assertThat(e.reason()).isEqualTo(SettlementFailureReason.INACTIVE_MEMBER);
            assertThat(e.status()).isEqualTo(SettlementExceptionStatus.OPEN);
        });

        setActive(seller, true);
        SettlementResponse settled = settlementService.settleTrade(tradeId);

        assertThat(settled.status()).isEqualTo(NovationStatus.SETTLED);
        assertThat(settled.exceptions()).singleElement().satisfies(e -> {
            assertThat(e.status()).isEqualTo(SettlementExceptionStatus.RESOLVED);
            assertThat(e.resolvedAt()).isNotNull();
        });
    }

    @Test
    @DisplayName("Cash below minus the credit limit fails settlement with INSUFFICIENT_FUNDS")
    void insufficientFundsFails() {
        String symbol = "SETC260619C00100000";
        String buyer = newMember("0", "1000");
        String tradeId = trade(symbol, buyer, newMember(), 1, "4.50"); // buyer cash -450, within the 1000 limit
        setCreditLimit(buyer, "100");

        SettlementResponse response = settlementService.settleTrade(tradeId);

        assertThat(response.status()).isEqualTo(NovationStatus.FAILED);
        assertThat(response.exceptions()).singleElement().satisfies(e -> {
            assertThat(e.memberId()).isEqualTo(buyer);
            assertThat(e.reason()).isEqualTo(SettlementFailureReason.INSUFFICIENT_FUNDS);
        });
    }

    @Test
    @DisplayName("Trade log, positions and exception queries filter and paginate")
    void queriesFilterAndPaginate() {
        String symbol = "SETD260619C00100000";
        String buyer = newMember();
        String seller = newMember();
        String t1 = trade(symbol, buyer, seller, 1, "1.00");
        String t2 = trade(symbol, buyer, seller, 1, "1.00");
        trade(symbol, buyer, seller, 1, "1.00");
        settlementService.settleTrade(t1);
        setActive(seller, false);
        settlementService.settleTrade(t2); // fails: the seller is now inactive

        var sort = Sort.by(Sort.Order.desc("novatedAt"), Sort.Order.asc("legId"));

        // Trade log: the buyer is the clearing member on one leg of each of the 3 trades.
        var page0 = queryService.tradeLog(buyer, symbol, null, PageRequest.of(0, 2, sort));
        assertThat(page0.getTotalElements()).isEqualTo(3);
        assertThat(page0.getContent()).hasSize(2);
        assertThat(queryService.tradeLog(buyer, symbol, null, PageRequest.of(1, 2, sort)).getContent()).hasSize(1);
        assertThat(queryService.tradeLog(buyer, symbol, NovationStatus.SETTLED, PageRequest.of(0, 10, sort))
            .getContent()).extracting(NovatedContract::getExternalTradeId).containsExactly(t1);
        assertThat(queryService.tradeLog(buyer, symbol, NovationStatus.FAILED, PageRequest.of(0, 10, sort))
            .getContent()).extracting(NovatedContract::getExternalTradeId).containsExactly(t2);
        assertThat(queryService.tradeLog(buyer, symbol, NovationStatus.NOVATED, PageRequest.of(0, 10, sort))
            .getTotalElements()).isEqualTo(1);

        // Positions: long 3 for the buyer, short 3 for the seller; filters narrow to one row.
        assertThat(queryService.positions(buyer, symbol, false, PageRequest.of(0, 10)).getContent())
            .extracting(Position::getNetQuantity).containsExactly(3L);
        assertThat(queryService.positions(null, symbol, false, PageRequest.of(0, 10)).getTotalElements()).isEqualTo(2);

        // Exceptions: one OPEN for the seller on t2, none resolved.
        var open = queryService.settlementExceptions(SettlementExceptionStatus.OPEN, seller, PageRequest.of(0, 10));
        assertThat(open.getContent()).singleElement().satisfies(e -> {
            assertThat(e.getExternalTradeId()).isEqualTo(t2);
            assertThat(e.getReason()).isEqualTo(SettlementFailureReason.INACTIVE_MEMBER);
        });
        assertThat(queryService.settlementExceptions(SettlementExceptionStatus.RESOLVED, seller,
            PageRequest.of(0, 10))).isEmpty();
    }
}
