function fn() {
  var baseUrl = karate.properties['test.base.url'] || 'http://localhost:8080';
  karate.configure('connectTimeout', 5000);
  karate.configure('readTimeout', 10000);
  return {
    baseUrl: baseUrl,
    // Java interop: reuse the same random-data factory as the Cucumber side
    TradeData: Java.type('com.occ.housecleaning.bdd.support.TradeData')
  };
}
