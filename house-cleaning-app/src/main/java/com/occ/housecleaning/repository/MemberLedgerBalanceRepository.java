package com.occ.housecleaning.repository;

import com.occ.housecleaning.model.MemberLedgerBalance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MemberLedgerBalanceRepository extends JpaRepository<MemberLedgerBalance, String> {
    Optional<MemberLedgerBalance> findByMemberId(String memberId);
}
