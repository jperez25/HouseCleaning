package com.occ.housecleaning.dto;

import java.time.Instant;
import java.util.List;

/**
 * Outcome of a settlement attempt. A failed settlement is a recorded business outcome, not a request error,
 * so it is returned with status FAILED and the reasons rather than as an HTTP error.
 */
public record SettlementResponse(
        String externalTradeId,
        NovationStatus status,
        Instant processedAt,
        List<ContractLeg> legs,
        List<SettlementExceptionResponse> exceptions
) {
}
