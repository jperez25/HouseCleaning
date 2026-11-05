package com.occ.housecleaning.controller;

import com.occ.housecleaning.dto.TradeRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/trades")
public class TradeController {

    private static final Logger log = LoggerFactory.getLogger(TradeController.class);

    @PostMapping("/novate")
    public ResponseEntity<Void> novateTrade(@Valid @RequestBody TradeRequest tradeRequest) {
        log.info(String.format("Received trade novation request: %s", tradeRequest));
        return ResponseEntity.ok().build();
    }
}
