package com.occ.housecleaning.clients;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;

/**
 * Reusable HTTP client for trade-related API calls in integration tests.
 */
public class TradeApiClient {

    private final String baseUrl;

    public TradeApiClient(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public TradeApiClient(int port) {
        this("http://localhost:" + port);
    }

    /**
     * Sends a POST request to /api/v1/trades/novate with the specified payload.
     *
     * @param jsonBody request JSON string
     * @return REST-assured Response
     */
    public Response novateTrade(String jsonBody) {
        return RestAssured.given()
            .baseUri(baseUrl)
            .contentType(ContentType.JSON)
            .body(jsonBody)
            .when()
            .post("/api/v1/trades/novate");
    }
}
