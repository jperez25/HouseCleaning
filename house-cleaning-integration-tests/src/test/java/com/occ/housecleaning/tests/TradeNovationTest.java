package com.occ.housecleaning.tests;

import com.occ.housecleaning.clients.TradeApiClient;
import io.restassured.response.Response;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class TradeNovationTest {

    private TradeApiClient tradeApiClient;

    @BeforeClass
    public void setUp() {
        // Reads from -Dtest.base.url=... or defaults to localhost:8080
        String baseUrl = System.getProperty("test.base.url", "http://localhost:8080");
        tradeApiClient = new TradeApiClient(baseUrl);
    }

    @Test(description = "Verify trade novation endpoint is reachable and returns 400 Bad Request on empty body")
    public void shouldReturnBadRequestWhenBodyIsEmpty() {
        Response response = tradeApiClient.novateTrade("{}");

        assertThat(response.getStatusCode()).isEqualTo(400);
    }
}