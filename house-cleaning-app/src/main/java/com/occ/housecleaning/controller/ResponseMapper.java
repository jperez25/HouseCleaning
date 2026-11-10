package com.occ.housecleaning.controller;

import com.occ.housecleaning.dto.ContractLeg;
import com.occ.housecleaning.dto.LedgerEntryResponse;
import com.occ.housecleaning.dto.MarginCallResponse;
import com.occ.housecleaning.dto.MemberBalanceResponse;
import com.occ.housecleaning.dto.MemberPositionResponse;
import com.occ.housecleaning.dto.SettlementExceptionResponse;
import com.occ.housecleaning.dto.PageResponse;
import com.occ.housecleaning.dto.TradeAuditResponse;
import com.occ.housecleaning.model.MarginCall;
import com.occ.housecleaning.model.MemberLedgerBalance;
import com.occ.housecleaning.model.MemberLedgerEntry;
import com.occ.housecleaning.model.NovatedContract;
import com.occ.housecleaning.model.Position;
import com.occ.housecleaning.model.RawTradeAudit;
import com.occ.housecleaning.model.SettlementExceptionRecord;
import com.occ.housecleaning.service.SettlementMapper;
import org.springframework.data.domain.Page;

import java.util.function.Function;

/**
 * Maps persistence entities to API response DTOs so the wire contract is independent of the schema.
 */
final class ResponseMapper {

    private ResponseMapper() {
    }

    static TradeAuditResponse toResponse(RawTradeAudit a) {
        return new TradeAuditResponse(a.getExternalTradeId(), a.getExchangeCode(), a.getRawPayload(),
                a.getReceivedAt(), a.getStatus(), a.getRejectionReason());
    }

    static ContractLeg toResponse(NovatedContract c) {
        return new ContractLeg(c.getLegId(), c.getExternalTradeId(), c.getLegType(), c.getBuyerMemberId(),
                c.getSellerMemberId(), c.getOptionSymbol(), c.getQuantity(), c.getExecutionPrice(),
                c.getExecutionTimestamp(), c.getStatus());
    }

    static MemberBalanceResponse toResponse(MemberLedgerBalance b) {
        return new MemberBalanceResponse(b.getMemberId(), b.getCashBalance(), b.getMarginBalance(), b.getUpdatedAt());
    }

    static LedgerEntryResponse toResponse(MemberLedgerEntry e) {
        return new LedgerEntryResponse(e.getId(), e.getMemberId(), e.getExternalTradeId(), e.getEntryType(),
                e.getAmount(), e.getBalanceAfter(), e.getDescription(), e.getCreatedAt());
    }

    static MarginCallResponse toResponse(MarginCall c) {
        return new MarginCallResponse(c.getId(), c.getMemberId(), c.getMarginRequired(), c.getCashAvailable(),
                c.getShortfall(), c.getStatus(), c.getCreatedAt(), c.getUpdatedAt(), c.getClosedAt());
    }

    static MemberPositionResponse toResponse(Position p) {
        return new MemberPositionResponse(p.getMemberId(), p.getOptionSymbol(), p.getNetQuantity(),
                p.getLastPrice(), p.getUpdatedAt());
    }

    static SettlementExceptionResponse toResponse(SettlementExceptionRecord e) {
        return SettlementMapper.toResponse(e);
    }

    static <E, R> PageResponse<R> toPage(Page<E> page, Function<E, R> mapper) {
        return new PageResponse<>(page.getContent().stream().map(mapper).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }
}
