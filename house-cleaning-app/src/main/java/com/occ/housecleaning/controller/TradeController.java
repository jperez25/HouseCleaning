package com.occ.housecleaning.controller;

import com.occ.housecleaning.dto.ContractLeg;
import com.occ.housecleaning.dto.TradeAuditResponse;
import com.occ.housecleaning.dto.TradeNovationResponse;
import com.occ.housecleaning.dto.TradeRequest;
import com.occ.housecleaning.service.ClearingService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/v1/trades")
public class TradeController {

    private static final Logger log = LoggerFactory.getLogger(TradeController.class);

    private final ClearingService clearingService;

    public TradeController(ClearingService clearingService) {
        this.clearingService = clearingService;
    }

    @PostMapping("/novate")
    public ResponseEntity<TradeNovationResponse> novateTrade(@Valid @RequestBody TradeRequest tradeRequest) {
        log.info("Received trade novation request: {}", tradeRequest);
        return ResponseEntity.ok(clearingService.novateTrade(tradeRequest));
    }

    @GetMapping("/{externalTradeId}/audit")
    public ResponseEntity<TradeAuditResponse> getTradeAudit(@PathVariable String externalTradeId) {
        log.info("Received GET trade audit request for external trade id: {}", externalTradeId);
        return clearingService.getTradeAudit(externalTradeId)
                .map(a -> ResponseEntity.ok(ResponseMapper.toResponse(a)))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Trade audit not found"));
    }

    @GetMapping("/{externalTradeId}/contracts")
    public ResponseEntity<List<ContractLeg>> getNovatedContracts(@PathVariable String externalTradeId) {
        log.info("Received GET novation contracts request for external trade id: {}", externalTradeId);
        var contracts = clearingService.getNovatedContracts(externalTradeId);
        if (contracts.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Contracts not found");
        }
        return ResponseEntity.ok(contracts.stream().map(ResponseMapper::toResponse).toList());
    }
}
