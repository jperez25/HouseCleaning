package com.occ.housecleaning.tests;

import com.occ.housecleaning.dto.TradeRequest;
import com.occ.housecleaning.model.MemberLedgerBalance;
import com.occ.housecleaning.model.MemberLedgerEntry;
import com.occ.housecleaning.model.NovatedContract;
import com.occ.housecleaning.model.RawTradeAudit;
import com.occ.housecleaning.service.ClearingService;
import io.restassured.response.Response;
import org.assertj.core.api.SoftAssertions;
import org.testng.annotations.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Service-level deployment tests for trade novation.
 * Refactored to align with docs/testing-strategy.md:
 * - Extends AbstractTest base class.
 * - Communicates with the deployed service over HTTP via TradeApiClient (REST Assured).
 * - Follows standard test flow: Generate Request -> Send Request -> Validate Response.
 * - Employs soft assertions in required order: status code -> response fields -> assertAll.
 * - Test methods named using verb + expected outcome in lower camel case.
 */
public class TradeNovationTest extends AbstractTest {

    @Test(description = "Post a valid trade request and validate successful novation response, audit, contracts, and ledger")
    public void postTradeRequestValidateResponse() {
        // 1. Generate the request
        TradeRequest tradeRequest = createRandomTradeRequest();

        // Supplementary repository setup / balance pre-check (if repository support is available)
        final BigDecimal initialBuyerCash = hasRepositorySupport()
                ? memberLedgerBalanceRepository.findByMemberId(tradeRequest.buyerMemberId())
                        .map(MemberLedgerBalance::getCashBalance)
                        .orElse(BigDecimal.ZERO)
                : BigDecimal.ZERO;

        final BigDecimal initialSellerCash = hasRepositorySupport()
                ? memberLedgerBalanceRepository.findByMemberId(tradeRequest.sellerMemberId())
                        .map(MemberLedgerBalance::getCashBalance)
                        .orElse(BigDecimal.ZERO)
                : BigDecimal.ZERO;

        // 2. Send the request
        var response = tradeApiClient.postNovateTrade(tradeRequest);

        // 3. Validate the responses using the fluent validator
        SoftAssertions softly = new SoftAssertions();

        // Exercise downstream service contract endpoints via HTTP client
        Response auditResponse = tradeApiClient.getTradeAudit(tradeRequest.externalTradeId());
        Response contractsResponse = tradeApiClient.getNovatedContracts(tradeRequest.externalTradeId());
        Response buyerBalanceResponse = tradeApiClient.getMemberBalance(tradeRequest.buyerMemberId());

        tradeValidator
                .validateNovateTradeResponse(response, tradeRequest)
                .validateTradeAuditResponse(auditResponse, tradeRequest)
                .validateNovatedContractsResponse(contractsResponse, tradeRequest)
                .validateMemberBalanceResponse(buyerBalanceResponse, tradeRequest.buyerMemberId());

        // Supplementary persistence verification when repository support is available in test environment
        if (hasRepositorySupport()) {
            Optional<RawTradeAudit> auditOpt = rawTradeAuditRepository.findByExternalTradeId(tradeRequest.externalTradeId());
            softly.assertThat(auditOpt).as("Raw trade audit persisted").isPresent();
            auditOpt.ifPresent(audit -> {
                softly.assertThat(audit.getExternalTradeId()).isEqualTo(tradeRequest.externalTradeId());
                softly.assertThat(audit.getExchangeCode()).isEqualTo(tradeRequest.exchangeCode());
                softly.assertThat(audit.getRawPayload()).contains(tradeRequest.externalTradeId());
                softly.assertThat(audit.getStatus()).isEqualTo("NOVATED");
                softly.assertThat(audit.getReceivedAt()).isNotNull();
            });

            List<NovatedContract> contracts = novatedContractRepository.findByExternalTradeId(tradeRequest.externalTradeId());
            softly.assertThat(contracts).as("Novated contracts in repository").hasSize(2);

            BigDecimal expectedPremium = tradeRequest.executionPrice()
                    .multiply(BigDecimal.valueOf(tradeRequest.quantity()))
                    .multiply(ClearingService.CONTRACT_MULTIPLIER);

            Optional<MemberLedgerBalance> buyerBalance = memberLedgerBalanceRepository.findByMemberId(tradeRequest.buyerMemberId());
            softly.assertThat(buyerBalance).isPresent();
            buyerBalance.ifPresent(b -> softly.assertThat(b.getCashBalance())
                    .isEqualByComparingTo(initialBuyerCash.subtract(expectedPremium)));

            Optional<MemberLedgerBalance> sellerBalance = memberLedgerBalanceRepository.findByMemberId(tradeRequest.sellerMemberId());
            softly.assertThat(sellerBalance).isPresent();
            sellerBalance.ifPresent(b -> softly.assertThat(b.getCashBalance())
                    .isEqualByComparingTo(initialSellerCash.add(expectedPremium)));

            List<MemberLedgerEntry> entries = memberLedgerEntryRepository.findByExternalTradeId(tradeRequest.externalTradeId());
            softly.assertThat(entries).as("Ledger entries in repository").hasSize(2);
        }

        // Complete supplementary persistence assertions
        softly.assertAll();
    }

    @Test(description = "Post empty trade request and validate 400 Bad Request response")
    public void postEmptyTradeRequestValidateBadRequest() {
        // 1. Generate the request (empty payload)
        TradeRequest emptyPayload = new TradeRequest();

        // 2. Send the request
        Response response = tradeApiClient.postNovateTrade(emptyPayload);

        // 3. Validate the response
        tradeValidator.validateStatusCode(response, 400);
    }

    @Test(description = "Post valid trade request and validate 200 OK response")
    public void postTradeRequest() {
        // 1. Generate the request
        TradeRequest request = createRandomTradeRequest();

        // 2. Send the request
        Response response = tradeApiClient.postNovateTrade(request);
        tradeValidator.validateNovateTradeResponse(response, request);

        // 3. Validate the responses
        var novatedContractsResponse = tradeApiClient.getNovatedContracts(request.externalTradeId());
        tradeValidator.validateNovatedContractsResponse(novatedContractsResponse, request);
    }
}
