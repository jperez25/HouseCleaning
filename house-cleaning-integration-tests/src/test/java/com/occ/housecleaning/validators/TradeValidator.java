package com.occ.housecleaning.validators;

import com.occ.housecleaning.dto.LegType;
import com.occ.housecleaning.dto.NovationStatus;
import com.occ.housecleaning.dto.TradeNovationResponse;
import com.occ.housecleaning.dto.TradeRequest;
import com.occ.housecleaning.service.ClearingService;
import io.restassured.response.Response;
import org.assertj.core.api.SoftAssertions;

/**
 * Centralized response validator and common assertion rules for trade-related tests.
 * Follows the testing strategy doc by validating responses using soft assertions in sequence:
 * 1. Check HTTP status code
 * 2. Check response fields and values (including matching data sent)
 * 3. Complete soft-assertion check
 */
public class TradeValidator {

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

            var tradeResponse = response.getBody().as(TradeNovationResponse.class);

            // 2. Check response fields and values, including that relevant fields match request data
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
                    .isEqualTo(ClearingService.OCC_PARTICIPANT_ID);
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
                    .isEqualTo(ClearingService.OCC_PARTICIPANT_ID);
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
                    .as("Trade audit HTTP status code")
                    .isEqualTo(200);
            softly.assertThat(response.jsonPath().getString("externalTradeId"))
                    .as("Trade audit externalTradeId")
                    .isEqualTo(tradeRequest.externalTradeId());

            softly.assertThat(response.jsonPath().getString("exchangeCode"))
                    .as("Trade audit exchangeCode")
                    .isEqualTo(tradeRequest.exchangeCode());

            softly.assertThat(response.jsonPath().getString("status"))
                    .as("Trade audit status")
                    .isEqualTo("NOVATED");

            softly.assertThat(response.jsonPath().getString("rawPayload"))
                    .as("Trade audit rawPayload contains externalTradeId")
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
                    .as("Novated contracts HTTP status code")
                    .isEqualTo(200);
            softly.assertThat(response.jsonPath().getList(""))
                    .as("Novated contracts count")
                    .hasSize(2);
        });
        return this;
    }

    /**
     * Validates member balance endpoint response.
     */
    public TradeValidator validateMemberBalanceResponse(Response response, String expectedMemberId) {
        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(response.getStatusCode())
                    .as("Member balance HTTP status code")
                    .isEqualTo(200);
            softly.assertThat(response.jsonPath().getString("memberId"))
                    .as("Member balance memberId")
                    .isEqualTo(expectedMemberId);
        });
        return this;
    }
}
