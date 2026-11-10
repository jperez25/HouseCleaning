package com.occ.housecleaning.repository;

import com.occ.housecleaning.model.Position;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PositionRepository extends JpaRepository<Position, Long>, JpaSpecificationExecutor<Position> {
    Optional<Position> findByMemberIdAndOptionSymbol(String memberId, String optionSymbol);

    /** Short holders of a symbol, in a stable order. */
    List<Position> findByOptionSymbolAndNetQuantityLessThanOrderByMemberIdAsc(String optionSymbol, long netQuantity);

    List<Position> findByMemberIdOrderByOptionSymbolAsc(String memberId);

    /** Sorted, so callers lock member balances in the same order as novation does. */
    @Query("select distinct p.memberId from Position p order by p.memberId")
    List<String> findDistinctMemberIds();
}
