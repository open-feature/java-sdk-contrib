Feature: Provider error handling

  # Every scenario here asserts the same three-part contract, because all three parts matter and
  # providers routinely get one of them wrong:
  #
  #   1. the code default is returned — an application must keep working,
  #   2. the correct error code is reported — an application must be able to tell what went wrong,
  #   3. nothing is thrown — an unhandled exception from a flag evaluation is never acceptable.
  #
  # Requires the backend to be seeded with the canonical flag set — see flags/canonical-flags.json.

  Background:
    Given a stable provider

  Scenario: Requesting the wrong type returns the code default
    # 'wrong-flag' is a string flag. Asking for a boolean cannot be satisfied.
    Given a Boolean-flag with key "wrong-flag" and a default value "false"
    When the flag was evaluated with details
    Then the resolved details value should be "false"
    And the reason should be "ERROR"
    And the error-code should be "TYPE_MISMATCH"
    And no exception should have been thrown

  @strict-numeric-typing
  Scenario: A float flag is not silently narrowed to an integer
    # 'float-flag' resolves to 0.5. Narrowing that to an integer would lose information
    # silently, so it must be reported as a type mismatch rather than rounded.
    Given a Integer-flag with key "float-flag" and a default value "1"
    When the flag was evaluated with details
    Then the resolved details value should be "1"
    And the reason should be "ERROR"
    And the error-code should be "TYPE_MISMATCH"
    And no exception should have been thrown

  Scenario: An unknown flag key returns the code default
    # 'missing-flag' is deliberately absent from the canonical flag set.
    Given a String-flag with key "missing-flag" and a default value "fallback"
    When the flag was evaluated with details
    Then the resolved details value should be "fallback"
    And the reason should be "ERROR"
    And the error-code should be "FLAG_NOT_FOUND"
    And no exception should have been thrown
