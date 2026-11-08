package com.occ.housecleaning.controller;

import com.occ.housecleaning.model.MemberLedgerBalance;
import com.occ.housecleaning.model.MemberLedgerEntry;
import com.occ.housecleaning.service.ClearingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/members")
public class MemberLedgerController {

    private final ClearingService clearingService;

    public MemberLedgerController(ClearingService clearingService) {
        this.clearingService = clearingService;
    }

    @GetMapping("/{memberId}/balance")
    public ResponseEntity<MemberLedgerBalance> getMemberBalance(@PathVariable String memberId) {
        return clearingService.getMemberBalance(memberId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/{memberId}/ledger")
    public ResponseEntity<List<MemberLedgerEntry>> getMemberLedger(@PathVariable String memberId) {
        List<MemberLedgerEntry> entries = clearingService.getMemberLedgerEntries(memberId);
        return ResponseEntity.ok(entries);
    }
}
