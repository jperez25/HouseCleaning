package com.occ.housecleaning.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record ContractLeg(
    String legId,
    String externalTradeId,
    LegType legType,
    String buyerMemberId,
    String sellerMemberId,
    String optionSymbol,
    Integer quantity,
    BigDecimal executionPrice,
    Instant executionTimestamp,
    NovationStatus status
) {
}
