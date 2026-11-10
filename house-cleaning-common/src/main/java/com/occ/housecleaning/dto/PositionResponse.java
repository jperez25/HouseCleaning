package com.occ.housecleaning.dto;

import java.math.BigDecimal;

/** A member's net position in one option symbol and the margin it currently requires. */
public record PositionResponse(
        String optionSymbol,
        long netQuantity,
        BigDecimal lastPrice,
        BigDecimal volatilityFactor,
        BigDecimal marginRequirement
) {
}
