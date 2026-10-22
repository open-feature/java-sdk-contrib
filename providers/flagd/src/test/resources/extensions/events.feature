@ext-connection @rpc @in-process @file @events
Feature: flagd provider state changes

  # Ported from flagd-testbed gherkin/events.feature. Not ported: "Flag change event", which the
  # canonical events.feature asks in "A configuration change is signalled and applied", together
  # with the changed evaluation it leaves out.
  #
  # These chains ask what the retry grace period decides, and they ask it by sequence rather than by
  # clock: stale -> ready when the connection returns inside the grace period, stale -> error -> ready
  # when the error assertion has first waited the grace period out. The testbed timed the outage
  # instead (the launchpad's /restart), which made the same question depend on a 1s or 8s wall clock.
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
    When the connection is lost
    Then the error event handler should have been executed
    When the connection is restored
    Then the ready event handler should have been executed

  @grace
  Scenario: Provider events chain ready -> stale -> error -> ready
    Given a ready event handler
    And a error event handler
    And a stale event handler
    Then the ready event handler should have been executed
    When the connection is lost
    Then the stale event handler should have been executed
    Then the error event handler should have been executed
    When the connection is restored
    Then the ready event handler should have been executed

  @grace
  Scenario: Provider events chain ready -> stale -> ready
    Given a ready event handler
    And a error event handler
    And a stale event handler
    Then the ready event handler should have been executed
    When the connection is lost
    Then the stale event handler should have been executed
    When the connection is restored
    Then the ready event handler should have been executed with no error event before it
    And the error event handler should not have been executed
