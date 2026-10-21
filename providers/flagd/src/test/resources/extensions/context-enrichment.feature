@ext-metadata @contextEnrichment
Feature: Context Enrichment

  # Ported from flagd-testbed gherkin/contextEnrichment.feature. The testbed's Background "a stable
  # flagd provider" moves into each scenario as the TCK's "a stable provider", because the @grace
  # scenarios record options that have to come before it.
  #
  # The @grace scenarios restore the testbed provider's retry timing (grace period 5s, backoff
  # 500ms capped at 500ms) over the adoption's (grace 2s, backoff capped at the 5000ms default), so
  # the provider is back within the event timeout once flagd returns after the 6s outage. The RPC
  # one also restores the testbed's 1000ms deadline: at the adoption's 15000ms an evaluation issued
  # while flagd is down could wait out the outage and succeed, which is not what it asks.
  #
  # The testbed's @caching is renamed @flagd-caching: the TCK reserves @caching and refuses a
  # scenario that carries it.

  @in-process @rpc
  Scenario: Use enriched context
    Given a stable provider
    And a String-flag with key "flagd-context-aware" and a default value "not"
    When the flag was evaluated with details
    Then the resolved details value should be "INTERNAL"

  @grace @in-process
  Scenario: Use enriched context on connection error for IN-PROCESS
    Given an option "retryGracePeriod" of type "Integer" with value "5"
    And an option "retryBackoffMs" of type "Integer" with value "500"
    And an option "retryBackoffMaxMs" of type "Integer" with value "500"
    And a stable provider
    And a String-flag with key "flagd-context-aware" and a default value "not"
    And a stale event handler
    And a ready event handler
    When the flag was evaluated with details
    Then the resolved details value should be "INTERNAL"
    When the connection is lost for 6s
    And a stale event was fired
    When the flag was evaluated with details
    Then the resolved details value should be "INTERNAL"
    When a ready event was fired

  @grace @rpc
  Scenario: Use enriched context on connection error for RPC
    Given an option "deadlineMs" of type "Integer" with value "1000"
    And an option "retryGracePeriod" of type "Integer" with value "5"
    And an option "retryBackoffMs" of type "Integer" with value "500"
    And an option "retryBackoffMaxMs" of type "Integer" with value "500"
    And a stable provider
    And a String-flag with key "flagd-context-aware" and a default value "not"
    And a stale event handler
    And a ready event handler
    When the flag was evaluated with details
    Then the resolved details value should be "INTERNAL"
    When the connection is lost for 6s
    And a stale event was fired
    When the flag was evaluated with details
    Then the resolved details value should be "not"
    When a ready event was fired

  @rpc @flagd-caching
  Scenario: Use enriched context on RPC connection will not cache the value
    Given a stable provider
    And a String-flag with key "flagd-context-aware" and a default value "not"
    And a change event handler
    And a ready event handler
    When the flag was modified
    And a change event was fired
    And the flag was evaluated with details
    Then the reason should be "TARGETING_MATCH"
    # ensure that we do not cache a "TARGETING_MATCH", we should only cache evaluation with a "STATIC" reason
    When the flag was evaluated with details
    Then the reason should be "TARGETING_MATCH"
