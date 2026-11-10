package com.occ.housecleaning.bdd.support;

import com.occ.housecleaning.bdd.models.ApiErrorResponse;
import com.occ.housecleaning.bdd.models.ContractLeg;
import com.occ.housecleaning.bdd.models.LegType;
import com.occ.housecleaning.bdd.models.NovationStatus;
import com.occ.housecleaning.bdd.models.RejectionReason;
import com.occ.housecleaning.bdd.models.TradeAuditResponse;
import com.occ.housecleaning.bdd.models.TradeNovationResponse;
import com.occ.housecleaning.bdd.models.TradeRequest;
import io.restassured.response.Response;
import java.math.BigDecimal;
import org.assertj.core.api.SoftAssertions;

import static org.assertj.core.api.InstanceOfAssertFactories.STRING;

/**
 * Centralized response validator and common assertion rules for trade-related tests.
 * Follows the testing strategy doc by validating responses using soft assertions in sequence:
 * 1. Check HTTP status code
 * 2. Check response fields and values (including matching data sent)
 * 3. Complete soft-assertion check
 */
public class TradeValidator {

    /** Counterparty id OCC assumes on both novated legs (mirrors the service's constant). */
    public static final String OCC_PARTICIPANT_ID = "OCC";

    /**
     * Validates that the response HTTP status code matches the expected status code.
     */
    public TradeValidator validateStatusCode(Response response, int expectedStatusCode) {
        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(response)
                    .as("Response status code")
                    .extracting(Response::getStatusCode)
                    .isEqualTo(expectedStatusCode);
        });
        return this;
    }

    /**
     * Validates that the trade novation response returned 200 OK and matches the expected trade data.
     */
    public TradeValidator validateNovateTradeResponse(Response response, TradeRequest tradeRequest) {
        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(response.getStatusCode())
                    .as("Novate trade response status code")
                    .isEqualTo(200);

            var tradeResponse = response.getBody().as(TradeNovationResponse.class);

            // Check response fields and values, including that relevant fields match request data
            softly.assertThat(tradeResponse.externalTradeId())
                    .as("Trade response externalTradeId")
                    .isEqualTo(tradeRequest.externalTradeId());

            softly.assertThat(tradeResponse.status())
                    .as("Trade response status")
                    .isEqualTo(NovationStatus.NOVATED);

            softly.assertThat(tradeResponse.novatedAt())
                    .as("Trade response novatedAt")
                    .isNotNull();

            // Buyer leg assertions
            softly.assertThat(tradeResponse.buyerLeg().legType())
                    .as("Buyer leg type")
                    .isEqualTo(LegType.BUYER_LEG);
            softly.assertThat(tradeResponse.buyerLeg().buyerMemberId())
                    .as("Buyer leg buyerMemberId")
                    .isEqualTo(tradeRequest.buyerMemberId());
            softly.assertThat(tradeResponse.buyerLeg().sellerMemberId())
                    .as("Buyer leg sellerMemberId (counterparty)")
                    .isEqualTo(OCC_PARTICIPANT_ID);
            softly.assertThat(tradeResponse.buyerLeg().optionSymbol())
                    .as("Buyer leg optionSymbol")
                    .isEqualTo(tradeRequest.optionSymbol());
            softly.assertThat(tradeResponse.buyerLeg().quantity())
                    .as("Buyer leg quantity")
                    .isEqualTo(tradeRequest.quantity());

            // Seller leg assertions
            softly.assertThat(tradeResponse.sellerLeg().legType())
                    .as("Seller leg type")
                    .isEqualTo(LegType.SELLER_LEG);
            softly.assertThat(tradeResponse.sellerLeg().buyerMemberId())
                    .as("Seller leg buyerMemberId (counterparty)")
                    .isEqualTo(OCC_PARTICIPANT_ID);
            softly.assertThat(tradeResponse.sellerLeg().sellerMemberId())
                    .as("Seller leg sellerMemberId")
                    .isEqualTo(tradeRequest.sellerMemberId());
            softly.assertThat(tradeResponse.sellerLeg().optionSymbol())
                    .as("Seller leg optionSymbol")
                    .isEqualTo(tradeRequest.optionSymbol());
            softly.assertThat(tradeResponse.sellerLeg().quantity())
                    .as("Seller leg quantity")
                    .isEqualTo(tradeRequest.quantity());
        });
        return this;
    }

    /**
     * Validates trade audit endpoint response.
     */
    public TradeValidator validateTradeAuditResponse(Response response, TradeRequest tradeRequest) {
        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(response.getStatusCode())
                    .as("Trade audit response status code")
                    .isEqualTo(200);

            var audit = response.getBody().as(TradeAuditResponse.class);

            softly.assertThat(audit)
                    .as("Trade audit externalTradeId")
                    .extracting(TradeAuditResponse::externalTradeId)
                    .isEqualTo(tradeRequest.externalTradeId());

            softly.assertThat(audit)
                    .as("Trade audit exchangeCode")
                    .extracting(TradeAuditResponse::exchangeCode)
                    .isEqualTo(tradeRequest.exchangeCode());

            softly.assertThat(audit)
                    .as("Trade audit status")
                    .extracting(TradeAuditResponse::status)
                    .isEqualTo(NovationStatus.NOVATED);

            softly.assertThat(audit)
                    .as("Trade audit rawPayload contains externalTradeId")
                    .extracting(TradeAuditResponse::rawPayload, STRING)
                    .contains(tradeRequest.externalTradeId());
        });
        return this;
    }

    /**
     * Validates novated contracts endpoint response.
     */
    public TradeValidator validateNovatedContractsResponse(Response response, TradeRequest tradeRequest) {
        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(response.getStatusCode())
                    .as("Novated contracts response status code")
                    .isEqualTo(200);

            var contracts = response.getBody().jsonPath().getList("", ContractLeg.class);

            // Novation creates two contracts: one for the buyer leg and one for the seller leg
            softly.assertThat(contracts)
                    .as("Novated contracts count")
                    .hasSize(2);

            // Validate buyer leg contract
            var buyerContract = contracts.stream()
                    .filter(c -> c.legType() == LegType.BUYER_LEG)
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("Buyer leg contract not found"));
            softly.assertThat(buyerContract.buyerMemberId())
                    .as("Buyer leg contract buyerMemberId")
                    .isEqualTo(tradeRequest.buyerMemberId());
            softly.assertThat(buyerContract.sellerMemberId())
                    .as("Buyer leg contract sellerMemberId (counterparty)")
                    .isEqualTo(OCC_PARTICIPANT_ID);

            // Validate seller leg contract
            var sellerContract = contracts.stream()
                    .filter(c -> c.legType() == LegType.SELLER_LEG)
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("Seller leg contract not found"));
            softly.assertThat(sellerContract.buyerMemberId())
                    .as("Seller leg contract buyerMemberId (counterparty)")
                    .isEqualTo(OCC_PARTICIPANT_ID);
            softly.assertThat(sellerContract.sellerMemberId())
                    .as("Seller leg contract sellerMemberId")
                    .isEqualTo(tradeRequest.sellerMemberId());
        });
        return this;
    }

    /**
     * Validates member balance endpoint response.
     */
    public TradeValidator validateMemberBalanceResponse(Response response, String expectedMemberId) {
        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(response.getStatusCode())
                    .as("Member balance response status code")
                    .isEqualTo(200);

            softly.assertThat(response.jsonPath().getString("memberId"))
                    .as("Member balance memberId")
                    .isEqualTo(expectedMemberId);
        });
        return this;
    }

    /**
     * Validates a structured business-rule rejection: expected HTTP status plus the machine-readable code.
     */
    public TradeValidator validateRejection(Response response, RejectionReason expectedReason) {
        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(response.getStatusCode())
                    .as("Rejection HTTP status for " + expectedReason)
                    .isEqualTo(expectedReason.httpStatus());

            var error = response.getBody().as(ApiErrorResponse.class);
            softly.assertThat(error.code())
                    .as("Rejection business code")
                    .isEqualTo(expectedReason.name());
            softly.assertThat(error.message())
                    .as("Rejection message")
                    .isNotBlank();
        });
        return this;
    }

    /**
     * Validates a 400 Bad Request caused by bean validation, including that each expected field is reported.
     */
    public TradeValidator validateBadRequestResponse(Response response, String... expectedInvalidFields) {
        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(response.getStatusCode())
                    .as("Bad request response status code")
                    .isEqualTo(400);

            var error = response.getBody().as(ApiErrorResponse.class);
            softly.assertThat(error.message())
                    .as("Bad request message")
                    .isEqualTo("Request validation failed");
            softly.assertThat(error.validationErrors())
                    .as("Reported validation errors")
                    .containsKeys(expectedInvalidFields);
        });
        return this;
    }

    /**
     * Validates that the audit endpoint reports a rejected attempt with the expected reason.
     */
    public TradeValidator validateRejectedTradeAuditResponse(
            Response response, TradeRequest tradeRequest, RejectionReason expectedReason) {
        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(response.getStatusCode())
                    .as("Rejected trade audit response status code")
                    .isEqualTo(200);

            var audit = response.getBody().as(TradeAuditResponse.class);
            softly.assertThat(audit.externalTradeId())
                    .as("Rejected audit externalTradeId")
                    .isEqualTo(tradeRequest.externalTradeId());
            softly.assertThat(audit.status())
                    .as("Rejected audit status")
                    .isEqualTo(NovationStatus.REJECTED);
            softly.assertThat(audit.rejectionReason())
                    .as("Rejected audit reason")
                    .isEqualTo(expectedReason);
            softly.assertThat(audit.rawPayload())
                    .as("Rejected audit rawPayload contains externalTradeId")
                    .contains(tradeRequest.externalTradeId());
        });
        return this;
    }

    /**
     * Validates the member balance endpoint reports the expected cash balance.
     */
    public TradeValidator validateMemberCashBalance(Response response, String memberId, BigDecimal expectedCash) {
        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(response.getStatusCode())
                    .as("Member balance response status code")
                    .isEqualTo(200);
            softly.assertThat(response.jsonPath().getString("memberId"))
                    .as("Member balance memberId")
                    .isEqualTo(memberId);
            softly.assertThat(response.jsonPath().getObject("cashBalance", BigDecimal.class))
                    .as("Member cash balance for " + memberId)
                    .isEqualByComparingTo(expectedCash);
        });
        return this;
    }

    /**
     * Validates one page of a member ledger: page size, totals and that every entry belongs to the member.
     */
    public TradeValidator validateLedgerPageResponse(
            Response response, String memberId, int expectedPageSize, long minimumTotalEntries) {
        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(response.getStatusCode())
                    .as("Ledger page response status code")
                    .isEqualTo(200);

            softly.assertThat(response.jsonPath().getInt("size"))
                    .as("Ledger page size")
                    .isEqualTo(expectedPageSize);
            softly.assertThat(response.jsonPath().getList("content"))
                    .as("Ledger entries on page")
                    .hasSize(expectedPageSize);
            softly.assertThat(response.jsonPath().getList("content.memberId", String.class))
                    .as("Ledger entry memberIds")
                    .allMatch(memberId::equals);
            softly.assertThat(response.jsonPath().getLong("totalElements"))
                    .as("Ledger total entries")
                    .isGreaterThanOrEqualTo(minimumTotalEntries);
            softly.assertThat(response.jsonPath().getInt("totalPages"))
                    .as("Ledger total pages")
                    .isGreaterThanOrEqualTo((int) Math.ceil((double) minimumTotalEntries / expectedPageSize));
        });
        return this;
    }
}
