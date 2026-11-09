package com.occ.housecleaning.service;

import com.occ.housecleaning.model.RawTradeAudit;
import com.occ.housecleaning.repository.RawTradeAuditRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists rejection audit records in their own transaction so they survive the rollback
 * of the novation transaction that rejected the trade.
 */
@Service
public class TradeAuditRecorder {

    private final RawTradeAuditRepository rawTradeAuditRepository;

    public TradeAuditRecorder(RawTradeAuditRepository rawTradeAuditRepository) {
        this.rawTradeAuditRepository = rawTradeAuditRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordRejection(RawTradeAudit audit) {
        rawTradeAuditRepository.save(audit);
    }
}
