package com.occ.housecleaning.dto;

import java.time.Instant;

public record SettlementExceptionResponse(
        Long id,
        String externalTradeId,
        String memberId,
        SettlementFailureReason reason,
        String detail,
        SettlementExceptionStatus status,
        Instant createdAt,
        Instant resolvedAt
) {
}
