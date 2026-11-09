package com.occ.housecleaning.repository;

import com.occ.housecleaning.model.MemberLedgerEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MemberLedgerEntryRepository extends JpaRepository<MemberLedgerEntry, Long> {
    Page<MemberLedgerEntry> findByMemberId(String memberId, Pageable pageable);
    List<MemberLedgerEntry> findByExternalTradeId(String externalTradeId);
}
