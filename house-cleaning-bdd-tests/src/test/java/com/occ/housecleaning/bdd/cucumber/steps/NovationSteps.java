package com.occ.housecleaning.bdd.cucumber.steps;

import com.occ.housecleaning.bdd.models.RejectionReason;
import com.occ.housecleaning.bdd.support.TradeApiClient;
import com.occ.housecleaning.bdd.support.TradeData;
import com.occ.housecleaning.bdd.support.TradeRequestGenerator;
import com.occ.housecleaning.bdd.support.TradeValidator;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

/**
 * Step definitions: thin glue between the Gherkin in features/cucumber and the shared client / generator /
 * validator layer in the support package. Cucumber creates one instance per scenario, so the fields below
 * are scenario-scoped. Given = shape the request, When = call the service, Then = fluent validation.
 */
public class NovationSteps {

    private final TradeContext ctx = new TradeContext();
    private final TradeApiClient tradeApiClient = new TradeApiClient(TradeData.baseUrl());
    private final TradeValidator tradeValidator = new TradeValidator();

    // ---- Given ---------------------------------------------------------------------------------

    @Given("a valid trade between {string} and {string}")
    public void aValidTradeBetween(String buyer, String seller) {
        ctx.requestBuilder = TradeRequestGenerator.builder().withMembers(buyer, seller);
    }

    @Given("the trade has quantity {int}")
    public void theTradeHasQuantity(int quantity) {
        ctx.requestBuilder.withQuantity(quantity);
    }

    @Given("the trade has no external trade id")
    public void theTradeHasNoExternalTradeId() {
        ctx.requestBuilder.withExternalTradeId("");
    }

    // ---- When ----------------------------------------------------------------------------------

    @When("the trade is submitted for novation")
    public void theTradeIsSubmitted() {
        ctx.request = ctx.requestBuilder.build();
        ctx.response = tradeApiClient.postNovateTrade(ctx.request);
    }

    @When("the same trade is submitted again")
    public void theSameTradeIsSubmittedAgain() {
        ctx.response = tradeApiClient.postNovateTrade(ctx.request);
    }

    // ---- Then ----------------------------------------------------------------------------------

    @Then("the response status is {int}")
    public void theResponseStatusIs(int status) {
        tradeValidator.validateStatusCode(ctx.response, status);
    }

    @Then("the trade is novated into a buyer leg and a seller leg facing OCC")
    public void theTradeIsNovated() {
        tradeValidator.validateNovateTradeResponse(ctx.response, ctx.request);
    }

    @Then("the novated contracts for the trade can be retrieved")
    public void theNovatedContractsCanBeRetrieved() {
        var contracts = tradeApiClient.getNovatedContracts(ctx.request.externalTradeId());
        tradeValidator
                .validateStatusCode(contracts, 200)
                .validateNovatedContractsResponse(contracts, ctx.request);
    }

    @Then("the audit trail for the trade can be retrieved")
    public void theAuditTrailCanBeRetrieved() {
        var audit = tradeApiClient.getTradeAudit(ctx.request.externalTradeId());
        tradeValidator
                .validateStatusCode(audit, 200)
                .validateTradeAuditResponse(audit, ctx.request);
    }

    @Then("the trade is rejected with reason {string}")
    public void theTradeIsRejectedWithReason(String reason) {
        tradeValidator.validateRejection(ctx.response, RejectionReason.valueOf(reason));
    }

    @Then("the validation errors mention {string}")
    public void theValidationErrorsMention(String field) {
        tradeValidator.validateBadRequestResponse(ctx.response, field);
    }
}
