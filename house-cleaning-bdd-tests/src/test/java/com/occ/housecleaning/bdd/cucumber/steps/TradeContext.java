package com.occ.housecleaning.bdd.cucumber.steps;

import com.occ.housecleaning.bdd.models.TradeRequest;
import com.occ.housecleaning.bdd.support.TradeRequestGenerator;
import io.restassured.response.Response;

/**
 * Per-scenario state shared between steps. Given steps shape the request through the builder (the request
 * model is an immutable record), the When step builds and sends it, and Then steps read the response.
 */
public class TradeContext {
    TradeRequestGenerator.Builder requestBuilder;
    TradeRequest request;
    Response response;
}
