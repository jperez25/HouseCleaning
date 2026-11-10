package com.occ.housecleaning.repository;

import com.occ.housecleaning.dto.MarginCallStatus;
import com.occ.housecleaning.model.MarginCall;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MarginCallRepository extends JpaRepository<MarginCall, Long> {
    Optional<MarginCall> findFirstByMemberIdAndStatus(String memberId, MarginCallStatus status);

    Page<MarginCall> findByMemberId(String memberId, Pageable pageable);
}
