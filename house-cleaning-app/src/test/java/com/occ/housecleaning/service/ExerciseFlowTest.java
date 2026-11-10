package com.occ.housecleaning.service;

import com.occ.housecleaning.dto.AssignmentResponse;
import com.occ.housecleaning.dto.ExerciseRequest;
import com.occ.housecleaning.dto.ExerciseResponse;
import com.occ.housecleaning.model.OptionExercise;
import com.occ.housecleaning.model.OptionAssignment;
import com.occ.housecleaning.repository.MemberLedgerEntryRepository;
import com.occ.housecleaning.repository.OptionAssignmentRepository;
import com.occ.housecleaning.repository.OptionExerciseRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

class ExerciseFlowTest extends AbstractFlowTest {

    @Autowired ExerciseService exerciseService;
    @Autowired OptionAssignmentRepository assignmentRepository;
    @Autowired MemberLedgerEntryRepository entryRepository;
    @MockitoSpyBean OptionExerciseRepository exerciseRepository;

    private static ExerciseRequest request(String member, String symbol, int qty, String price) {
        return new ExerciseRequest(member, symbol, qty, new BigDecimal(price));
    }

    private static int status(Throwable t) {
        return ((ResponseStatusException) t).getStatusCode().value();
    }

    @Test
    @DisplayName("Call exercise pays intrinsic x qty x 100, assigned pro-rata, moving positions, cash and margin")
    void exerciseAssignsProRata() {
        String symbol = "EXRA260619C00150000"; // call, strike 150
        fixVolatility(symbol);
        String longHolder = newMember("0", "1000000000");
        String shortA = newMember("1000000", "1000000000");
        String shortB = newMember("1000000", "1000000000");
        trade(symbol, longHolder, shortA, 6, "1.00");
        trade(symbol, longHolder, shortB, 4, "1.00");
        var cashLong = balance(longHolder).getCashBalance();
        var cashA = balance(shortA).getCashBalance();
        var cashB = balance(shortB).getCashBalance();

        ExerciseResponse response = exerciseService.exercise(request(longHolder, symbol, 5, "160"));

        // intrinsic 160-150 = 10 ; payout 10 x 5 x 100 = 5000 ; A: 5x6/10 = 3 ; B: 5x4/10 = 2
        assertThat(response.strike()).isEqualByComparingTo("150");
        assertThat(response.intrinsicValue()).isEqualByComparingTo("10");
        assertThat(response.totalPayout()).isEqualByComparingTo("5000");
        assertThat(response.assignments()).extracting(AssignmentResponse::memberId, AssignmentResponse::quantity)
            .containsExactlyInAnyOrder(org.assertj.core.groups.Tuple.tuple(shortA, 3L),
                org.assertj.core.groups.Tuple.tuple(shortB, 2L));
        assertThat(response.assignments().stream().map(AssignmentResponse::amount).reduce(BigDecimal.ZERO, BigDecimal::add))
            .isEqualByComparingTo("5000");

        assertThat(netQty(longHolder, symbol)).isEqualTo(5);
        assertThat(netQty(shortA, symbol)).isEqualTo(-3);
        assertThat(netQty(shortB, symbol)).isEqualTo(-2);
        assertThat(balance(longHolder).getCashBalance().subtract(cashLong)).isEqualByComparingTo("5000");
        assertThat(balance(shortA).getCashBalance().subtract(cashA)).isEqualByComparingTo("-3000");
        assertThat(balance(shortB).getCashBalance().subtract(cashB)).isEqualByComparingTo("-2000");

        // Margin was recalculated on the reduced positions: 5 x 100 x 1.00 x 0.10 = 50
        assertThat(balance(longHolder).getMarginBalance()).isEqualByComparingTo("50");
        assertThat(entryRepository.findByExternalTradeId(response.exerciseId())).hasSize(3);
        assertThat(assignmentRepository.findByExerciseIdOrderByIdAsc(response.exerciseId()))
            .extracting(OptionAssignment::getQuantity).containsExactlyInAnyOrder(3L, 2L);
        assertThat(exerciseRepository.findById(response.exerciseId())).get()
            .extracting(OptionExercise::getTotalPayout).isEqualTo(response.totalPayout());
    }

    @Test
    @DisplayName("Put exercise pays strike minus price")
    void putExercise() {
        String symbol = "EXRB260619P00150000"; // put, strike 150
        fixVolatility(symbol);
        String longHolder = newMember();
        String shortHolder = newMember();
        trade(symbol, longHolder, shortHolder, 2, "1.00");

        ExerciseResponse response = exerciseService.exercise(request(longHolder, symbol, 2, "140"));

        assertThat(response.totalPayout()).isEqualByComparingTo("2000"); // (150-140) x 2 x 100
        assertThat(netQty(shortHolder, symbol)).isZero();
    }

    @Test
    @DisplayName("Out-of-the-money, oversized, unknown and inactive exercises are rejected and change nothing")
    void rejectionsChangeNothing() {
        String symbol = "EXRC260619C00150000";
        fixVolatility(symbol);
        String longHolder = newMember();
        String shortHolder = newMember();
        trade(symbol, longHolder, shortHolder, 3, "1.00");
        var cash = balance(longHolder).getCashBalance();

        assertThatThrownBy(() -> exerciseService.exercise(request(longHolder, symbol, 1, "140")))
            .satisfies(t -> assertThat(status(t)).isEqualTo(422)).hasMessageContaining("out of the money");
        assertThatThrownBy(() -> exerciseService.exercise(request(longHolder, symbol, 1, "150")))
            .satisfies(t -> assertThat(status(t)).isEqualTo(422)); // at the strike: no intrinsic value
        assertThatThrownBy(() -> exerciseService.exercise(request(longHolder, symbol, 4, "160")))
            .satisfies(t -> assertThat(status(t)).isEqualTo(422)).hasMessageContaining("long position");
        assertThatThrownBy(() -> exerciseService.exercise(request(shortHolder, symbol, 1, "160")))
            .satisfies(t -> assertThat(status(t)).isEqualTo(422)); // a short holder cannot exercise
        assertThatThrownBy(() -> exerciseService.exercise(request("NOPE-MEMBER", symbol, 1, "160")))
            .satisfies(t -> assertThat(status(t)).isEqualTo(404));
        setActive(longHolder, false);
        assertThatThrownBy(() -> exerciseService.exercise(request(longHolder, symbol, 1, "160")))
            .satisfies(t -> assertThat(status(t)).isEqualTo(422)).hasMessageContaining("not active");

        assertThat(netQty(longHolder, symbol)).isEqualTo(3);
        assertThat(netQty(shortHolder, symbol)).isEqualTo(-3);
        assertThat(balance(longHolder).getCashBalance()).isEqualByComparingTo(cash);
    }

    @Test
    @DisplayName("A failure while recording the exercise rolls back positions, cash, ledger and assignments")
    void failureRollsBackEverything() {
        String symbol = "EXRD260619C00150000";
        fixVolatility(symbol);
        String longHolder = newMember();
        String shortHolder = newMember();
        trade(symbol, longHolder, shortHolder, 3, "1.00");
        var longCash = balance(longHolder).getCashBalance();
        var shortCash = balance(shortHolder).getCashBalance();
        var marginBefore = balance(longHolder).getMarginBalance();
        long assignmentsBefore = assignmentRepository.count();

        doThrow(new IllegalStateException("simulated exercise-record failure"))
            .when(exerciseRepository).save(any(OptionExercise.class));

        assertThatThrownBy(() -> exerciseService.exercise(request(longHolder, symbol, 2, "160")))
            .hasMessageContaining("simulated exercise-record failure");

        assertThat(netQty(longHolder, symbol)).isEqualTo(3);
        assertThat(netQty(shortHolder, symbol)).isEqualTo(-3);
        assertThat(balance(longHolder).getCashBalance()).isEqualByComparingTo(longCash);
        assertThat(balance(shortHolder).getCashBalance()).isEqualByComparingTo(shortCash);
        assertThat(balance(longHolder).getMarginBalance()).isEqualByComparingTo(marginBefore);
        assertThat(assignmentRepository.count()).isEqualTo(assignmentsBefore);
    }
}
