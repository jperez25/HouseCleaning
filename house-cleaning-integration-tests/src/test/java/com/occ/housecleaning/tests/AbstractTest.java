package com.occ.housecleaning.tests;

import com.occ.housecleaning.clients.TradeApiClient;
import com.occ.housecleaning.dto.TradeRequest;
import com.occ.housecleaning.generators.TradeRequestGenerator;
import com.occ.housecleaning.repository.MemberLedgerBalanceRepository;
import com.occ.housecleaning.repository.MemberLedgerEntryRepository;
import com.occ.housecleaning.repository.NovatedContractRepository;
import com.occ.housecleaning.repository.RawTradeAuditRepository;
import com.occ.housecleaning.validators.TradeValidator;
import org.testng.annotations.BeforeClass;

import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.util.List;

/**
 * Shared base class for deployment / integration tests.
 * Follows the Application Testing Strategy:
 * - Provides shared Client, Repositories, Generators, and Validators.
 * - Targets configurable service URL over HTTP (via REST Assured).
 * - Does not embed the application or use @SpringBootTest.
 */
public abstract class AbstractTest {

    public static final String DEFAULT_BASE_URL = "http://localhost:8080";

    // Client — sends HTTP requests to the running service
    protected TradeApiClient tradeApiClient;

    // Validators — centralizes response assertions and common validation rules
    protected TradeValidator tradeValidator;

    // Repositories — provides repository support when test data setup or persistence verification is required
    protected RawTradeAuditRepository rawTradeAuditRepository;
    protected NovatedContractRepository novatedContractRepository;
    protected MemberLedgerBalanceRepository memberLedgerBalanceRepository;
    protected MemberLedgerEntryRepository memberLedgerEntryRepository;

    @BeforeClass(alwaysRun = true)
    public void setUpAbstractTest() {
        String baseUrl = resolveBaseUrl();
        this.tradeApiClient = new TradeApiClient(baseUrl);
        this.tradeValidator = new TradeValidator();

        if (!isServiceReachable(baseUrl)) {
            throw new IllegalStateException("Service is not reachable at " + baseUrl
                    + ". Start or deploy the service first as specified in docs/testing-strategy.md.");
        }
    }

    /**
     * Resolves the target service base URL from system properties, environment variables, or defaults.
     */
    protected String resolveBaseUrl() {
        String baseUrl = System.getProperty("test.base.url");
        if (baseUrl != null && !baseUrl.isBlank()) {
            return baseUrl;
        }
        String envUrl = System.getenv("TEST_BASE_URL");
        if (envUrl != null && !envUrl.isBlank()) {
            return envUrl;
        }
        String host = System.getProperty("test.base.host", "http://localhost");
        String port = System.getProperty("test.base.port", "8080");
        return host + ":" + port;
    }

    /**
     * Checks whether the target service URL is network-reachable.
     */
    protected boolean isServiceReachable(String baseUrl) {
        try {
            URI uri = URI.create(baseUrl);
            String host = uri.getHost() != null ? uri.getHost() : "localhost";
            int port = uri.getPort() != -1 ? uri.getPort() : 8080;
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(host, port), 1000);
                return true;
            }
        } catch (Exception e) {
            return false;
        }
    }


    /**
     * Generator helper to create a batch of valid random TradeRequests.
     */
    protected List<TradeRequest> createRandomTradeRequests(int count) {
        return TradeRequestGenerator.createRandomTradeRequests(count);
    }

    /**
     * Indicates whether supplementary repository support is available in this test environment.
     */
    public boolean hasRepositorySupport() {
        return rawTradeAuditRepository != null
                && novatedContractRepository != null
                && memberLedgerBalanceRepository != null
                && memberLedgerEntryRepository != null;
    }
}
