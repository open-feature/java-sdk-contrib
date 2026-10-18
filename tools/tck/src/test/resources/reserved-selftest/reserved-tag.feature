Feature: A reserved capability tag on a scenario fails the run

  This file is the TCK's own proof of the expiry check on Capability.reserved(). It is driven by
  ReservedTagExpiryTest through ReservedTagSuiteFixture, which selects this directory and nothing
  else, so the two scenarios below run inside a real suite — real parse, real @Before hook, real
  CapabilityGate — without affecting any other suite in this module.

  It lives outside extensions/ on purpose. A feature file under extensions/ is selected by every
  suite this module runs, and the second scenario here is meant to fail.

  Scenario: A Gherkin comment naming a reserved tag is prose, not a tag
    # This scenario is untagged. The line you are reading mentions @caching, exactly as
    # gherkin/events.feature does where it explains which stale-provider behaviour is deliberately
    # not covered yet. A check that scanned feature files as text rather than reading the parsed
    # tags would fail this scenario, and would therefore fail every adoption on the day it shipped.
    Given a stable provider

  @caching
  Scenario: A tag this implementation still calls reserved fails the run
    Given a stable provider
