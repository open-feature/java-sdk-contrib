@ext-connection @rpc @in-process @file @events
Feature: flagd provider state changes

  # Ported from flagd-testbed gherkin/events.feature. Not ported: "Flag change event", which the
  # canonical events.feature asks in "A configuration change is signalled and applied", together
  # with the changed evaluation it leaves out.
  #
  # The canonical @stale scenario stops flagd and starts it again on request. These chains instead
  # take flagd down for a fixed time with the launchpad's /restart, so they ask what the retry grace
  # period decides: an outage shorter than it ends stale -> ready, a longer one escalates to error.
  #
  # Each "should have been executed" drains the recorded events through the one it matched, so the
  # next assertion waits for a newer event and a chain asserts its order.
  #
  # Timing is the testbed's "stable flagd provider" except for the deadline, which stays at the
  # adoption's 15000ms for the in-process sync (see AbstractResolverTest): a 5s grace period, and a
  # retry backoff capped at 500ms so reconnecting does not wait out an exponential backoff.

  Background:
    Given an option "retryGracePeriod" of type "Integer" with value "5"
    And an option "retryBackoffMaxMs" of type "Integer" with value "500"
    And a stable provider

  Scenario: Provider events chain ready -> error -> ready
    Given a ready event handler
    And a error event handler
    Then the ready event handler should have been executed
    When the connection is lost for 8s
    Then the error event handler should have been executed
    Then the ready event handler should have been executed

  @grace
  Scenario: Provider events chain ready -> stale -> error -> ready
    Given a ready event handler
    And a error event handler
    And a stale event handler
    Then the ready event handler should have been executed
    When the connection is lost for 8s
    Then the stale event handler should have been executed
    Then the error event handler should have been executed
    Then the ready event handler should have been executed

  @grace
  Scenario: Provider events chain ready -> stale -> ready
    Given a ready event handler
    And a error event handler
    And a stale event handler
    Then the ready event handler should have been executed
    When the connection is lost for 1s
    Then the stale event handler should have been executed
    Then the ready event handler should have been executed with no error event before it
    And the error event handler should not have been executed
