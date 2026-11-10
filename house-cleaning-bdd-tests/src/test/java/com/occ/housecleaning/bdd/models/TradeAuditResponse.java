package com.occ.housecleaning.bdd.models;

import java.time.Instant;

public record TradeAuditResponse(
        String externalTradeId,
        String exchangeCode,
        String rawPayload,
        Instant receivedAt,
        NovationStatus status,
        RejectionReason rejectionReason
) {
}
