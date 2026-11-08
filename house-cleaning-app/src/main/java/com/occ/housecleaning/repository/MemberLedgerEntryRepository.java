package com.occ.housecleaning.repository;

import com.occ.housecleaning.model.MemberLedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MemberLedgerEntryRepository extends JpaRepository<MemberLedgerEntry, Long> {
    List<MemberLedgerEntry> findByMemberId(String memberId);
    List<MemberLedgerEntry> findByExternalTradeId(String externalTradeId);
}
