package com.occ.housecleaning.repository;

import com.occ.housecleaning.model.NovatedContract;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NovatedContractRepository extends JpaRepository<NovatedContract, String>, JpaSpecificationExecutor<NovatedContract> {
    List<NovatedContract> findByExternalTradeId(String externalTradeId);
    List<NovatedContract> findByClearingMemberId(String clearingMemberId);
    boolean existsByExternalTradeId(String externalTradeId);

    /** Row-locks both legs so concurrent settlement attempts for one trade serialize. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from NovatedContract c where c.externalTradeId = :tradeId order by c.legType")
    List<NovatedContract> findByExternalTradeIdForUpdate(@Param("tradeId") String externalTradeId);
}
