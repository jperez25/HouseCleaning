@cucumber
Feature: Trade novation
  As a clearing operator
  I want exchange trades to be novated to OCC
  So that OCC becomes the counterparty to both clearing members

  @smoke
  Scenario: A valid trade is novated
    Given a valid trade between "MEM-101" and "MEM-102"
    When the trade is submitted for novation
    Then the response status is 200
    And the trade is novated into a buyer leg and a seller leg facing OCC
    And the novated contracts for the trade can be retrieved
    And the audit trail for the trade can be retrieved

  Scenario: A duplicate trade is rejected
    Given a valid trade between "MEM-201" and "MEM-202"
    And the trade is submitted for novation
    When the same trade is submitted again
    Then the response status is 409
    And the trade is rejected with reason "DUPLICATE_TRADE"

  Scenario: A trade with an unregistered member is rejected
    Given a valid trade between "MEM-NOT-REGISTERED" and "MEM-102"
    When the trade is submitted for novation
    Then the trade is rejected with reason "UNKNOWN_MEMBER"

  Scenario: A trade with an invalid payload is rejected
    Given a valid trade between "MEM-101" and "MEM-102"
    And the trade has no external trade id
    When the trade is submitted for novation
    Then the response status is 400
    And the validation errors mention "externalTradeId"

  Scenario Outline: A trade with non-positive quantity is rejected
    Given a valid trade between "MEM-101" and "MEM-102"
    And the trade has quantity <quantity>
    When the trade is submitted for novation
    Then the response status is 400
    And the validation errors mention "quantity"

    Examples:
      | quantity |
      | 0        |
      | -5       |
