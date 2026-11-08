package com.occ.housecleaning.dto;

import java.time.Instant;

public record TradeNovationResponse(
    String externalTradeId,
    NovationStatus status,
    Instant novatedAt,
    ContractLeg buyerLeg,
    ContractLeg sellerLeg
) {
}
