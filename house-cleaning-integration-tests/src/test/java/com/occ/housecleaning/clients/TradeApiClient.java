package com.occ.housecleaning.clients;

import com.occ.housecleaning.dto.TradeNovationResponse;
import com.occ.housecleaning.dto.TradeRequest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import tools.jackson.databind.json.JsonMapper;

/**
 * Reusable HTTP client for trade-related API calls in integration tests.
 */
public class TradeApiClient {

    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

    private final String baseUrl;

    public TradeApiClient(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public TradeApiClient(int port) {
        this("http://localhost:" + port);
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    private RequestSpecification given() {
        return RestAssured.given()
                .baseUri(baseUrl)
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON);
    }

    /**
     * Sends a POST request to /api/v1/trades/novate with the specified payload.
     *
     * @param jsonBody request JSON string
     * @return REST-assured Response
     */
    public Response postNovateTrade(TradeRequest req) {
        return given().body(req).post("/api/v1/trades/novate");
    }

    /**
     * Sends a GET request to /api/v1/trades/{externalTradeId}/audit.
     */
    public Response getTradeAudit(String externalTradeId) {
        return given().get("/api/v1/trades/" + externalTradeId + "/audit");
    }

    /**
     * Sends a GET request to /api/v1/trades/{externalTradeId}/contracts.
     */
    public Response getNovatedContracts(String externalTradeId) {
        return given().get("/api/v1/trades/" + externalTradeId + "/contracts");
    }

    /**
     * Sends a GET request to /api/v1/members/{memberId}/balance.
     */
    public Response getMemberBalance(String memberId) {
        return given().get("/api/v1/members/" + memberId + "/balance");
    }

    /**
     * Sends a GET request to /api/v1/members/{memberId}/ledger.
     */
    public Response getMemberLedger(String memberId) {
        return given().get("/api/v1/members/" + memberId + "/ledger");
    }
}
