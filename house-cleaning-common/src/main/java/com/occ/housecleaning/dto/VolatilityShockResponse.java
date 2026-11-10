package com.occ.housecleaning.dto;

import java.math.BigDecimal;

/** @param symbol null when the shock was applied globally */
public record VolatilityShockResponse(String symbol, BigDecimal factor, int membersRecalculated) {
}
