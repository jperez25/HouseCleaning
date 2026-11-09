package com.occ.housecleaning.controller;

import com.occ.housecleaning.dto.LedgerEntryResponse;
import com.occ.housecleaning.dto.MemberBalanceResponse;
import com.occ.housecleaning.dto.PageResponse;
import com.occ.housecleaning.service.ClearingService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/members")
public class MemberLedgerController {

    static final int DEFAULT_PAGE_SIZE = 50;
    static final int MAX_PAGE_SIZE = 200;

    private final ClearingService clearingService;

    public MemberLedgerController(ClearingService clearingService) {
        this.clearingService = clearingService;
    }

    @GetMapping("/{memberId}/balance")
    public ResponseEntity<MemberBalanceResponse> getMemberBalance(@PathVariable String memberId) {
        return clearingService.getMemberBalance(memberId)
                .map(b -> ResponseEntity.ok(ResponseMapper.toResponse(b)))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Member balance not found"));
    }

    /** Paginated journal, oldest entry first. Out-of-range page parameters are clamped, not rejected. */
    @GetMapping("/{memberId}/ledger")
    public ResponseEntity<PageResponse<LedgerEntryResponse>> getMemberLedger(
            @PathVariable String memberId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + DEFAULT_PAGE_SIZE) int size) {
        if (!clearingService.memberExists(memberId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Member not found");
        }
        var pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by("id").ascending());
        return ResponseEntity.ok(ResponseMapper.toPage(
                clearingService.getMemberLedgerEntries(memberId, pageable), ResponseMapper::toResponse));
    }
}
