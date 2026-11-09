package com.occ.housecleaning.repository;

import com.occ.housecleaning.model.MemberLedgerBalance;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MemberLedgerBalanceRepository extends JpaRepository<MemberLedgerBalance, String> {
    Optional<MemberLedgerBalance> findByMemberId(String memberId);

    /** Row-locks the balance (SELECT ... FOR UPDATE) so concurrent trades serialize per member. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from MemberLedgerBalance b where b.memberId = :memberId")
    Optional<MemberLedgerBalance> findByMemberIdForUpdate(@Param("memberId") String memberId);
}
