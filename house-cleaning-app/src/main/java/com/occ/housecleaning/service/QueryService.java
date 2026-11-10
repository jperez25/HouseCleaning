package com.occ.housecleaning.service;

import com.occ.housecleaning.dto.NovationStatus;
import com.occ.housecleaning.dto.SettlementExceptionStatus;
import com.occ.housecleaning.model.NovatedContract;
import com.occ.housecleaning.model.Position;
import com.occ.housecleaning.model.SettlementExceptionRecord;
import com.occ.housecleaning.repository.NovatedContractRepository;
import com.occ.housecleaning.repository.PositionRepository;
import com.occ.housecleaning.repository.SettlementExceptionRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/** Read-side, filterable, paginated queries. Null filters are ignored. */
@Service
@Transactional(readOnly = true)
public class QueryService {

    private final NovatedContractRepository contractRepository;
    private final PositionRepository positionRepository;
    private final SettlementExceptionRepository exceptionRepository;

    public QueryService(NovatedContractRepository contractRepository,
                        PositionRepository positionRepository,
                        SettlementExceptionRepository exceptionRepository) {
        this.contractRepository = contractRepository;
        this.positionRepository = positionRepository;
        this.exceptionRepository = exceptionRepository;
    }

    public Page<NovatedContract> tradeLog(String memberId, String optionSymbol, NovationStatus status, Pageable pageable) {
        return contractRepository.findAll(all(
                equal("clearingMemberId", memberId), equal("optionSymbol", optionSymbol), equal("status", status)),
                pageable);
    }

    /** Flat (zero) positions are hidden unless {@code includeFlat} is set. */
    public Page<Position> positions(String memberId, String optionSymbol, boolean includeFlat, Pageable pageable) {
        Specification<Position> notFlat = includeFlat ? null : (r, q, cb) -> cb.notEqual(r.get("netQuantity"), 0L);
        return positionRepository.findAll(all(equal("memberId", memberId), equal("optionSymbol", optionSymbol), notFlat),
                pageable);
    }

    public Page<SettlementExceptionRecord> settlementExceptions(SettlementExceptionStatus status, String memberId,
                                                                Pageable pageable) {
        return exceptionRepository.findAll(all(equal("status", status), equal("memberId", memberId)), pageable);
    }

    private static <T> Specification<T> equal(String field, Object value) {
        return value == null ? null : (root, query, cb) -> cb.equal(root.get(field), value);
    }

    @SafeVarargs
    private static <T> Specification<T> all(Specification<T>... specs) {
        List<Specification<T>> present = new ArrayList<>();
        for (Specification<T> s : specs) {
            if (s != null) {
                present.add(s);
            }
        }
        return present.isEmpty() ? (root, query, cb) -> cb.conjunction() : Specification.allOf(present);
    }
}
