package com.occ.housecleaning.repository;

import com.occ.housecleaning.model.NovatedContract;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NovatedContractRepository extends JpaRepository<NovatedContract, String> {
    List<NovatedContract> findByExternalTradeId(String externalTradeId);
    List<NovatedContract> findByClearingMemberId(String clearingMemberId);
    boolean existsByExternalTradeId(String externalTradeId);
}
