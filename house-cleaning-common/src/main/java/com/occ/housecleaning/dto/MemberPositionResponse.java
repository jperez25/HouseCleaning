package com.occ.housecleaning.dto;

import java.math.BigDecimal;
import java.time.Instant;

/** @param netQuantity positive is long, negative is short */
public record MemberPositionResponse(
        String memberId,
        String optionSymbol,
        long netQuantity,
        BigDecimal lastPrice,
        Instant updatedAt
) {
}
