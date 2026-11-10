package com.occ.housecleaning.bdd.models;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Request model for POST /api/v1/trades/novate. Deliberately a plain record: no bean-validation annotations and
 * no constructor guard, so negative scenarios can build invalid payloads and let the service reject them.
 */
public record TradeRequest(
        String externalTradeId,
        String exchangeCode,
        String optionSymbol,
        Integer quantity,
        BigDecimal executionPrice,
        String buyerMemberId,
        String sellerMemberId,
        Instant executionTimestamp
) {
}
