function fn() {
  var baseUrl = karate.properties['test.base.url'] || 'http://localhost:8080';
  karate.configure('connectTimeout', 5000);
  karate.configure('readTimeout', 10000);
  return {
    baseUrl: baseUrl,
    // Java interop: the same request generator the Cucumber steps use
    TradeRequestGenerator: Java.type('com.occ.housecleaning.bdd.support.TradeRequestGenerator')
  };
}
