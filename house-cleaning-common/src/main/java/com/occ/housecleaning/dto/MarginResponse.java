package com.occ.housecleaning.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * @param shortfall margin required minus available cash, floored at zero; above zero means a margin call is open
 */
public record MarginResponse(
        String memberId,
        BigDecimal marginBalance,
        BigDecimal cashBalance,
        BigDecimal shortfall,
        List<PositionResponse> positions,
        Instant updatedAt
) {
}
