package com.occ.housecleaning.controller;

import com.occ.housecleaning.dto.MarginCallResponse;
import com.occ.housecleaning.dto.MarginResponse;
import com.occ.housecleaning.dto.PageResponse;
import com.occ.housecleaning.dto.PositionResponse;
import com.occ.housecleaning.dto.VolatilityShockRequest;
import com.occ.housecleaning.dto.VolatilityShockResponse;
import com.occ.housecleaning.model.MemberLedgerBalance;
import com.occ.housecleaning.service.ClearingService;
import com.occ.housecleaning.service.RiskService;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/v1")
public class RiskController {

    private final ClearingService clearingService;
    private final RiskService riskService;

    public RiskController(ClearingService clearingService, RiskService riskService) {
        this.clearingService = clearingService;
        this.riskService = riskService;
    }

    @GetMapping("/members/{memberId}/margin")
    public ResponseEntity<MarginResponse> getMargin(@PathVariable String memberId) {
        MemberLedgerBalance balance = clearingService.getMemberBalance(memberId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Member not found"));
        var positions = riskService.getPositions(memberId).stream()
                .map(p -> new PositionResponse(p.getOptionSymbol(), p.getNetQuantity(), p.getLastPrice(),
                        riskService.factorFor(p.getOptionSymbol()), riskService.marginFor(p)))
                .toList();
        BigDecimal shortfall = balance.getMarginBalance().subtract(balance.getCashBalance()).max(BigDecimal.ZERO);
        return ResponseEntity.ok(new MarginResponse(memberId, balance.getMarginBalance(),
                balance.getCashBalance(), shortfall, positions, balance.getUpdatedAt()));
    }

    /** Paginated, newest first. Out-of-range page parameters are clamped, not rejected. */
    @GetMapping("/members/{memberId}/margin-calls")
    public ResponseEntity<PageResponse<MarginCallResponse>> getMarginCalls(
            @PathVariable String memberId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + MemberLedgerController.DEFAULT_PAGE_SIZE) int size) {
        if (!clearingService.memberExists(memberId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Member not found");
        }
        var pageable = PageRequest.of(Math.max(page, 0),
                Math.min(Math.max(size, 1), MemberLedgerController.MAX_PAGE_SIZE),
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        return ResponseEntity.ok(ResponseMapper.toPage(
                riskService.getMarginCalls(memberId, pageable), ResponseMapper::toResponse));
    }

    @PostMapping("/risk/volatility")
    public ResponseEntity<VolatilityShockResponse> setVolatility(@Valid @RequestBody VolatilityShockRequest request) {
        return ResponseEntity.ok(riskService.applyShock(request.symbol(), request.factor()));
    }
}
