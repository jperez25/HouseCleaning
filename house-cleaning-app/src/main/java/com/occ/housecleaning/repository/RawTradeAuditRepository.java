package com.occ.housecleaning.repository;

import com.occ.housecleaning.model.RawTradeAudit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RawTradeAuditRepository extends JpaRepository<RawTradeAudit, Long> {
    Optional<RawTradeAudit> findByExternalTradeId(String externalTradeId);
}
