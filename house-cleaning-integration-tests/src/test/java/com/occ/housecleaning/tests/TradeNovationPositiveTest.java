package com.occ.housecleaning.tests;

import com.occ.housecleaning.dto.TradeRequest;
import com.occ.housecleaning.generators.TradeRequestGenerator;
import io.restassured.response.Response;
import org.testng.annotations.Test;

import static com.occ.housecleaning.generators.TradeRequestGenerator.createRandomTradeRequest;

/**
 * Positive (happy path) deployment tests for trade novation.
 * Each test follows docs/testing-strategy.md: generate request -> send request -> validate with the
 * shared fluent {@code tradeValidator}. Tests contain no assertions of their own.
 */
public class TradeNovationPositiveTest extends AbstractTest {

    @Test(description = "Post a valid trade request and validate the novation response, audit, contracts and balance")
    public void postTradeRequestValidateResponse() {
        TradeRequest request = createRandomTradeRequest();

        Response response = tradeApiClient.postNovateTrade(request);
        tradeValidator
                .validateStatusCode(response, 200)
                .validateNovateTradeResponse(response, request);

        Response novatedContractsResponse = tradeApiClient.getNovatedContracts(request.externalTradeId());
        tradeValidator
                .validateStatusCode(novatedContractsResponse, 200)
                .validateNovatedContractsResponse(novatedContractsResponse, request);

        Response auditResponse = tradeApiClient.getTradeAudit(request.externalTradeId());
        tradeValidator
                .validateStatusCode(auditResponse, 200)
                .validateTradeAuditResponse(auditResponse, request);

        Response buyerBalanceResponse = tradeApiClient.getMemberBalance(request.buyerMemberId());
        tradeValidator
                .validateStatusCode(buyerBalanceResponse, 200)
                .validateMemberBalanceResponse(buyerBalanceResponse, request.buyerMemberId());
    }

    @Test(description = "Get the ledger of a member with trades and validate page size, totals and ownership")
    public void getMemberLedgerValidatePagination() {
        TradeRequest first = TradeRequestGenerator.builder().withMembers("MEM-201", "MEM-202").build();
        TradeRequest second = TradeRequestGenerator.builder().withMembers("MEM-201", "MEM-202").build();
        tradeApiClient.postNovateTrade(first);
        tradeApiClient.postNovateTrade(second);

        Response ledgerPage = tradeApiClient.getMemberLedger("MEM-201", 0, 1);

        tradeValidator
                .validateStatusCode(ledgerPage, 200)
                .validateLedgerPageResponse(ledgerPage, "MEM-201", 1, 2);
    }
}
