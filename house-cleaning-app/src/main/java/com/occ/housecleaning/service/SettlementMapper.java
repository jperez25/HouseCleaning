package com.occ.housecleaning.service;

import com.occ.housecleaning.dto.SettlementExceptionResponse;
import com.occ.housecleaning.model.SettlementExceptionRecord;

/** Package-visible so SettlementService and the controller layer share one mapping. */
public final class SettlementMapper {

    private SettlementMapper() {
    }

    public static SettlementExceptionResponse toResponse(SettlementExceptionRecord e) {
        return new SettlementExceptionResponse(e.getId(), e.getExternalTradeId(), e.getMemberId(), e.getReason(),
                e.getDetail(), e.getStatus(), e.getCreatedAt(), e.getResolvedAt());
    }
}
