@ext-metadata @in-process @sync-payload
Feature: Disabled Sync Metadata

  # Ported from flagd-testbed gherkin/sync-payload.feature. The testbed's "a syncpayload flagd
  # provider" becomes "a stable provider with "sync-payload" configuration", moved from the
  # Background into each scenario so the @grace one can record options first.
  #
  # The @grace scenario restores the testbed provider's retry timing (grace period 5s, backoff
  # 500ms capped at 500ms) over the adoption's (grace 2s, backoff capped at the 5000ms default), so
  # the provider is back within the event timeout once the connection is restored.

  Scenario: Use enriched context
    Given a stable provider with "sync-payload" configuration
    And a String-flag with key "flagd-context-aware" and a default value "not"
    When the flag was evaluated with details
    Then the resolved details value should be "INTERNAL"

  @grace
  Scenario: Use enriched context on connection error for IN-PROCESS
    Given an option "retryGracePeriod" of type "Integer" with value "5"
    And an option "retryBackoffMs" of type "Integer" with value "500"
    And an option "retryBackoffMaxMs" of type "Integer" with value "500"
    And a stable provider with "sync-payload" configuration
    And a String-flag with key "flagd-context-aware" and a default value "not"
    And a stale event handler
    And a ready event handler
    When the flag was evaluated with details
    Then the resolved details value should be "INTERNAL"
    When the connection is lost
    And a stale event was fired
    When the flag was evaluated with details
    Then the resolved details value should be "INTERNAL"
    When the connection is restored
    And a ready event was fired

  Scenario: Resolve value independent of context
    Given a stable provider with "sync-payload" configuration
    And a Boolean-flag with key "boolean-flag" and a default value "false"
    When the flag was evaluated with details
    Then the resolved details value should be "true"
