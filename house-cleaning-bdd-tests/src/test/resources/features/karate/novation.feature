@karate
Feature: Trade novation (Karate)

  Background:
    * url baseUrl
    * def newTrade = function(buyer, seller){ return TradeRequestGenerator.builder().withMembers(buyer, seller).buildMap() }

  @smoke
  Scenario: A valid trade is novated and auditable
    * def trade = newTrade('MEM-101', 'MEM-102')
    Given path 'api/v1/trades/novate'
    And request trade
    When method post
    Then status 200
    And match response contains { externalTradeId: '#(trade.externalTradeId)', status: 'NOVATED' }
    And match response.buyerLeg contains { legType: 'BUYER_LEG', buyerMemberId: 'MEM-101', sellerMemberId: 'OCC' }
    And match response.sellerLeg contains { buyerMemberId: 'OCC', sellerMemberId: 'MEM-102' }
    And match response.buyerLeg.quantity == trade.quantity

    # Karate resets path/body after each `method`, but keeps `url`
    Given path 'api/v1/trades', trade.externalTradeId, 'audit'
    When method get
    Then status 200

  Scenario: A duplicate trade is rejected with 409
    * def trade = newTrade('MEM-201', 'MEM-202')
    Given path 'api/v1/trades/novate'
    And request trade
    When method post
    Then status 200
    Given path 'api/v1/trades/novate'
    And request trade
    When method post
    Then status 409
    And match response.code == 'DUPLICATE_TRADE'

  Scenario: An unregistered member is rejected with 422
    * def trade = newTrade('MEM-NOT-REGISTERED', 'MEM-102')
    Given path 'api/v1/trades/novate'
    And request trade
    When method post
    Then status 422
    And match response.code == 'UNKNOWN_MEMBER'

  Scenario Outline: Invalid quantity <quantity> is rejected
    * def trade = TradeRequestGenerator.builder().withMembers('MEM-101', 'MEM-102').withQuantity(<quantity>).buildMap()
    Given path 'api/v1/trades/novate'
    And request trade
    When method post
    Then status 400
    And match response.validationErrors.quantity == 'quantity must be greater than zero'

    Examples:
      | quantity |
      | 0        |
      | -5       |
