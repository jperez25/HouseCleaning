package com.occ.housecleaning.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record MarginCallResponse(
        Long id,
        String memberId,
        BigDecimal marginRequired,
        BigDecimal cashAvailable,
        BigDecimal shortfall,
        MarginCallStatus status,
        Instant createdAt,
        Instant updatedAt,
        Instant closedAt
) {
}
