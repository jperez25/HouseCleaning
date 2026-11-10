package com.occ.housecleaning.controller;

import com.occ.housecleaning.dto.ContractLeg;
import com.occ.housecleaning.dto.ExerciseRequest;
import com.occ.housecleaning.dto.ExerciseResponse;
import com.occ.housecleaning.dto.MemberPositionResponse;
import com.occ.housecleaning.dto.NovationStatus;
import com.occ.housecleaning.dto.PageResponse;
import com.occ.housecleaning.dto.SettlementExceptionResponse;
import com.occ.housecleaning.dto.SettlementExceptionStatus;
import com.occ.housecleaning.dto.SettlementResponse;
import com.occ.housecleaning.service.ExerciseService;
import com.occ.housecleaning.service.QueryService;
import com.occ.housecleaning.service.SettlementService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class SettlementController {

    private static final String PAGE_SIZE = "" + MemberLedgerController.DEFAULT_PAGE_SIZE;

    private final SettlementService settlementService;
    private final ExerciseService exerciseService;
    private final QueryService queryService;

    public SettlementController(SettlementService settlementService, ExerciseService exerciseService,
                                QueryService queryService) {
        this.settlementService = settlementService;
        this.exerciseService = exerciseService;
        this.queryService = queryService;
    }

    /** 404 if never novated, 409 if already SETTLED; a failed attempt returns 200 with status FAILED. */
    @PostMapping("/trades/{externalTradeId}/settle")
    public ResponseEntity<SettlementResponse> settle(@PathVariable String externalTradeId) {
        return ResponseEntity.ok(settlementService.settleTrade(externalTradeId));
    }

    @PostMapping("/options/exercise")
    public ResponseEntity<ExerciseResponse> exercise(@Valid @RequestBody ExerciseRequest request) {
        return ResponseEntity.ok(exerciseService.exercise(request));
    }

    /** Contract legs, newest first, filterable by clearing member, option symbol and status. */
    @GetMapping("/trades/log")
    public ResponseEntity<PageResponse<ContractLeg>> tradeLog(
            @RequestParam(required = false) String memberId,
            @RequestParam(required = false) String optionSymbol,
            @RequestParam(required = false) NovationStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = PAGE_SIZE) int size) {
        var pageable = Paging.of(page, size, Sort.by(Sort.Order.desc("novatedAt"), Sort.Order.asc("legId")));
        return ResponseEntity.ok(ResponseMapper.toPage(
                queryService.tradeLog(memberId, optionSymbol, status, pageable), ResponseMapper::toResponse));
    }

    /** Net positions, by member then symbol. Flat (zero) positions are hidden unless includeFlat=true. */
    @GetMapping("/positions")
    public ResponseEntity<PageResponse<MemberPositionResponse>> positions(
            @RequestParam(required = false) String memberId,
            @RequestParam(required = false) String optionSymbol,
            @RequestParam(defaultValue = "false") boolean includeFlat,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = PAGE_SIZE) int size) {
        var pageable = Paging.of(page, size, Sort.by(Sort.Order.asc("memberId"), Sort.Order.asc("optionSymbol")));
        return ResponseEntity.ok(ResponseMapper.toPage(
                queryService.positions(memberId, optionSymbol, includeFlat, pageable), ResponseMapper::toResponse));
    }

    /** Failed settlements, newest first, filterable by status and member. */
    @GetMapping("/settlement/exceptions")
    public ResponseEntity<PageResponse<SettlementExceptionResponse>> settlementExceptions(
            @RequestParam(required = false) SettlementExceptionStatus status,
            @RequestParam(required = false) String memberId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = PAGE_SIZE) int size) {
        var pageable = Paging.of(page, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        return ResponseEntity.ok(ResponseMapper.toPage(
                queryService.settlementExceptions(status, memberId, pageable), ResponseMapper::toResponse));
    }
}
