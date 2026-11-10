package com.occ.housecleaning.repository;

import com.occ.housecleaning.dto.SettlementExceptionStatus;
import com.occ.housecleaning.model.SettlementExceptionRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SettlementExceptionRepository
        extends JpaRepository<SettlementExceptionRecord, Long>, JpaSpecificationExecutor<SettlementExceptionRecord> {
    List<SettlementExceptionRecord> findByExternalTradeIdAndStatus(String externalTradeId, SettlementExceptionStatus status);

    List<SettlementExceptionRecord> findByExternalTradeIdOrderByIdAsc(String externalTradeId);
}
