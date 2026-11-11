package com.occ.housecleaning.bdd.models;

import java.time.Instant;

public record TradeNovationResponse(
    String externalTradeId,
    NovationStatus status,
    Instant novatedAt,
    ContractLeg buyerLeg,
    ContractLeg sellerLeg
) {
}
