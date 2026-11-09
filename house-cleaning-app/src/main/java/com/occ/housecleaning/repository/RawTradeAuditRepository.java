package com.occ.housecleaning.repository;

import com.occ.housecleaning.model.RawTradeAudit;
import com.occ.housecleaning.dto.NovationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RawTradeAuditRepository extends JpaRepository<RawTradeAudit, Long> {
    /** A trade id can have several audit rows (rejected attempts, then a novated one); this returns the newest. */
    Optional<RawTradeAudit> findFirstByExternalTradeIdOrderByIdDesc(String externalTradeId);
    Optional<RawTradeAudit> findFirstByExternalTradeIdAndStatusOrderByIdDesc(String externalTradeId, NovationStatus status);
    List<RawTradeAudit> findByExternalTradeId(String externalTradeId);
}
