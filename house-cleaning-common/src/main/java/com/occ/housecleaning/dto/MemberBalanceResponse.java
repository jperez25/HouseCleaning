package com.occ.housecleaning.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record MemberBalanceResponse(
        String memberId,
        BigDecimal cashBalance,
        BigDecimal marginBalance,
        Instant updatedAt
) {
}
