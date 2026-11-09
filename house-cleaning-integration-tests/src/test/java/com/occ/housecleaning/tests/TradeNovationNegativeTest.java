package com.occ.housecleaning.tests;

import com.occ.housecleaning.dto.RejectionReason;
import com.occ.housecleaning.dto.TradeRequest;
import com.occ.housecleaning.generators.TradeRequestGenerator;
import io.restassured.response.Response;
import org.testng.annotations.Test;

import java.math.BigDecimal;

import static com.occ.housecleaning.generators.TradeRequestGenerator.createRandomTradeRequest;

/**
 * Negative deployment tests for trade novation: invalid input, business-rule rejections and missing resources.
 * Each test follows docs/testing-strategy.md: generate request -> send request -> validate with the
 * shared fluent {@code tradeValidator}. Tests contain no assertions of their own.
 */
public class TradeNovationNegativeTest extends AbstractTest {

    @Test(description = "Post an empty trade request and validate 400 Bad Request listing the invalid fields")
    public void postEmptyTradeRequestValidateBadRequest() {
        TradeRequest invalidRequest = TradeRequestGenerator.createEmptyTradeRequest();

        Response response = tradeApiClient.postNovateTrade(invalidRequest);

        tradeValidator.validateBadRequestResponse(response,
                "externalTradeId", "exchangeCode", "optionSymbol", "quantity", "executionPrice");
    }

    @Test(description = "Resubmit a novated trade and validate 409 DUPLICATE_TRADE with the original novation intact")
    public void postDuplicateTradeValidateConflict() {
        TradeRequest request = createRandomTradeRequest();
        tradeValidator.validateStatusCode(tradeApiClient.postNovateTrade(request), 200);

        Response duplicate = tradeApiClient.postNovateTrade(request);
        tradeValidator.validateRejection(duplicate, RejectionReason.DUPLICATE_TRADE);

        // The original novation is unaffected: still exactly two legs, and the audit still reports NOVATED.
        Response contractsResponse = tradeApiClient.getNovatedContracts(request.externalTradeId());
        tradeValidator
                .validateStatusCode(contractsResponse, 200)
                .validateNovatedContractsResponse(contractsResponse, request);

        Response auditResponse = tradeApiClient.getTradeAudit(request.externalTradeId());
        tradeValidator
                .validateStatusCode(auditResponse, 200)
                .validateTradeAuditResponse(auditResponse, request);
    }

    @Test(description = "Post a trade with an unregistered member and validate 422 UNKNOWN_MEMBER, audited as REJECTED")
    public void postTradeWithUnknownMemberValidateUnprocessable() {
        TradeRequest request = TradeRequestGenerator.builder().withBuyerMemberId("MEM-NOT-REGISTERED").build();

        Response response = tradeApiClient.postNovateTrade(request);
        tradeValidator.validateRejection(response, RejectionReason.UNKNOWN_MEMBER);

        Response auditResponse = tradeApiClient.getTradeAudit(request.externalTradeId());
        tradeValidator.validateRejectedTradeAuditResponse(auditResponse, request, RejectionReason.UNKNOWN_MEMBER);

        tradeValidator.validateStatusCode(tradeApiClient.getNovatedContracts(request.externalTradeId()), 404);
    }

    @Test(description = "Post a trade with an inactive member and validate 422 INACTIVE_MEMBER, audited as REJECTED")
    public void postTradeWithInactiveMemberValidateUnprocessable() {
        TradeRequest request = TradeRequestGenerator.builder().withSellerMemberId("MEM-INACTIVE").build();

        Response response = tradeApiClient.postNovateTrade(request);
        tradeValidator.validateRejection(response, RejectionReason.INACTIVE_MEMBER);

        Response auditResponse = tradeApiClient.getTradeAudit(request.externalTradeId());
        tradeValidator.validateRejectedTradeAuditResponse(auditResponse, request, RejectionReason.INACTIVE_MEMBER);
    }

    @Test(description = "Post a trade the zero-credit buyer cannot afford and validate 422 INSUFFICIENT_FUNDS, balance unchanged")
    public void postTradeWithInsufficientFundsValidateUnprocessable() {
        TradeRequest request = TradeRequestGenerator.builder()
                .withBuyerMemberId("MEM-LOWCREDIT").withSellerMemberId("MEM-402")
                .withQuantity(1).withExecutionPrice(1.00).build();

        Response response = tradeApiClient.postNovateTrade(request);
        tradeValidator.validateRejection(response, RejectionReason.INSUFFICIENT_FUNDS);

        // MEM-LOWCREDIT is reserved for this scenario, so its balance must still be exactly zero.
        Response buyerBalanceResponse = tradeApiClient.getMemberBalance("MEM-LOWCREDIT");
        tradeValidator.validateMemberCashBalance(buyerBalanceResponse, "MEM-LOWCREDIT", BigDecimal.ZERO);

        tradeValidator.validateStatusCode(tradeApiClient.getNovatedContracts(request.externalTradeId()), 404);
    }

    @Test(description = "Get the audit of an unknown trade and validate 404 Not Found")
    public void getUnknownTradeAuditValidateNotFound() {
        Response response = tradeApiClient.getTradeAudit(TradeRequestGenerator.randomExternalTradeId());

        tradeValidator.validateStatusCode(response, 404);
    }

    @Test(description = "Get the ledger and balance of an unregistered member and validate 404 Not Found")
    public void getUnknownMemberLedgerValidateNotFound() {
        tradeValidator
                .validateStatusCode(tradeApiClient.getMemberLedger("MEM-DOES-NOT-EXIST"), 404)
                .validateStatusCode(tradeApiClient.getMemberBalance("MEM-DOES-NOT-EXIST"), 404);
    }
}
