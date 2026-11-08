package com.occ.housecleaning.generators;

import com.occ.housecleaning.dto.TradeRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class TradeRequestGeneratorTest {

    private Validator validator;

    @BeforeClass
    public void setUp() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            validator = factory.getValidator();
        }
    }

    @Test(description = "Verify generated random trade request passes bean validation")
    public void shouldGenerateValidRandomTradeRequest() {
        for (int i = 0; i < 50; i++) {
            TradeRequest request = TradeRequestGenerator.createRandomTradeRequest();

            assertThat(request).isNotNull();
            assertThat(request.externalTradeId()).isNotBlank();
            assertThat(request.exchangeCode()).matches("^[A-Z0-9]{3,10}$");
            assertThat(request.optionSymbol()).matches("^[A-Z]{1,6}\\d{6}[CP]\\d{8}$");
            assertThat(request.quantity()).isPositive();
            assertThat(request.executionPrice()).isGreaterThanOrEqualTo(new BigDecimal("0.01"));
            assertThat(request.buyerMemberId()).isNotBlank();
            assertThat(request.sellerMemberId()).isNotBlank();
            assertThat(request.buyerMemberId()).isNotEqualToIgnoringCase(request.sellerMemberId());
            assertThat(request.executionTimestamp()).isNotNull();

            var violations = validator.validate(request);
            assertThat(violations).as("Violations found for request: " + request).isEmpty();
        }
    }

    @Test(description = "Verify batch generation creates requested number of valid requests")
    public void shouldGenerateBatchOfRequests() {
        int count = 20;
        List<TradeRequest> requests = TradeRequestGenerator.createRandomTradeRequests(count);

        assertThat(requests).hasSize(count);
        for (TradeRequest req : requests) {
            assertThat(validator.validate(req)).isEmpty();
        }
    }

    @Test(description = "Verify builder overrides specific fields while keeping random defaults for others")
    public void shouldSupportBuilderCustomization() {
        TradeRequest customRequest = TradeRequestGenerator.builder()
            .withExchangeCode("CBOE")
            .withQuantity(50)
            .withExecutionPrice(12.75)
            .withMembers("MEM-CUSTOM-1", "MEM-CUSTOM-2")
            .build();

        assertThat(customRequest.exchangeCode()).isEqualTo("CBOE");
        assertThat(customRequest.quantity()).isEqualTo(50);
        assertThat(customRequest.executionPrice()).isEqualByComparingTo("12.75");
        assertThat(customRequest.buyerMemberId()).isEqualTo("MEM-CUSTOM-1");
        assertThat(customRequest.sellerMemberId()).isEqualTo("MEM-CUSTOM-2");
        assertThat(customRequest.externalTradeId()).startsWith("EXCH-");
        assertThat(customRequest.optionSymbol()).matches("^[A-Z]{1,6}\\d{6}[CP]\\d{8}$");

        var violations = validator.validate(customRequest);
        assertThat(violations).isEmpty();
    }

    @Test(description = "Verify negative batch count throws IllegalArgumentException")
    public void shouldThrowOnNegativeBatchCount() {
        assertThatThrownBy(() -> TradeRequestGenerator.createRandomTradeRequests(-1))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
