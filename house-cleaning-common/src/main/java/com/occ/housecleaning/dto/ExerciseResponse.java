package com.occ.housecleaning.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record ExerciseResponse(
        String exerciseId,
        String memberId,
        String optionSymbol,
        int quantity,
        BigDecimal strike,
        BigDecimal settlementPrice,
        BigDecimal intrinsicValue,
        BigDecimal totalPayout,
        Instant createdAt,
        List<AssignmentResponse> assignments
) {
}
