package com.occ.housecleaning.service;

import com.occ.housecleaning.model.Position;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExerciseAllocationTest {

    private static Position shortPos(String member, long shortQty) {
        return Position.builder().memberId(member).optionSymbol("AAPL260619C00150000")
            .netQuantity(-shortQty).lastPrice(BigDecimal.ONE).build();
    }

    @Test
    @DisplayName("Assignment is proportional to short size")
    void proportional() {
        Map<String, Long> result = ExerciseService.allocateProRata(
            List.of(shortPos("A", 60), shortPos("B", 40)), 50);

        assertThat(result).containsEntry("A", 30L).containsEntry("B", 20L);
    }

    @Test
    @DisplayName("Leftover contracts go to the largest remainders, ties by member id; total is exact")
    void remaindersAreDeterministic() {
        // 10 across three equal shorts of 10: shares 3,3,3 + 1 leftover -> tie on remainder -> member "A"
        Map<String, Long> result = ExerciseService.allocateProRata(
            List.of(shortPos("A", 10), shortPos("B", 10), shortPos("C", 10)), 10);

        assertThat(result).containsEntry("A", 4L).containsEntry("B", 3L).containsEntry("C", 3L);
        assertThat(result.values().stream().mapToLong(Long::longValue).sum()).isEqualTo(10);
    }

    @Test
    @DisplayName("Never assigns a holder more than they are short")
    void neverExceedsShort() {
        Map<String, Long> result = ExerciseService.allocateProRata(
            List.of(shortPos("A", 1), shortPos("B", 99)), 100);

        assertThat(result).containsEntry("A", 1L).containsEntry("B", 99L);
    }

    @Test
    @DisplayName("Exercising more than the short interest is an integrity error")
    void shortInterestTooSmall() {
        assertThatThrownBy(() -> ExerciseService.allocateProRata(List.of(shortPos("A", 5)), 6))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("Option symbol parses into strike, side and intrinsic value")
    void optionContract() {
        OptionContract call = OptionContract.parse("AAPL260619C00150000");
        OptionContract put = OptionContract.parse("AAPL260619P00150500");

        assertThat(call.call()).isTrue();
        assertThat(call.strike()).isEqualByComparingTo("150");
        assertThat(call.intrinsicValue(new BigDecimal("160"))).isEqualByComparingTo("10");
        assertThat(call.intrinsicValue(new BigDecimal("140"))).isEqualByComparingTo("0");
        assertThat(put.strike()).isEqualByComparingTo("150.5");
        assertThat(put.intrinsicValue(new BigDecimal("140.5"))).isEqualByComparingTo("10");
        assertThat(put.intrinsicValue(new BigDecimal("160"))).isEqualByComparingTo("0");
        assertThatThrownBy(() -> OptionContract.parse("nope")).isInstanceOf(IllegalArgumentException.class);
    }
}
