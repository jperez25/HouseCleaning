function fn() {
  // Same resolution as the Cucumber side: -Dtest.base.url, else TEST_BASE_URL, else http://localhost:8080
  var baseUrl = Java.type('com.occ.housecleaning.bdd.support.TestEnvironment').baseUrl();
  karate.configure('connectTimeout', 5000);
  karate.configure('readTimeout', 10000);
  return {
    baseUrl: baseUrl,
    // Java interop: the same request generator the Cucumber steps use
    TradeRequestGenerator: Java.type('com.occ.housecleaning.bdd.support.TradeRequestGenerator')
  };
}
