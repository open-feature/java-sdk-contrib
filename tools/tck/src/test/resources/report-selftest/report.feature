# Fixture for ConformanceReportPluginTest, and not part of the conformance suite: it lives under a
# separate classpath root so that nothing selecting "gherkin" can pick it up.
#
# It is shaped like the real suite rather than minimal, because the properties the report has to
# preserve are properties of that shape: a capability tag on the feature, one on a scenario, one on
# a single Examples block, and an outline whose rows all share a name. Running real Cucumber over
# this is the only way to check what a capability-gated abort actually becomes in the results.

@events
Feature: Report self-test

  Scenario: A mandatory scenario
    Given a step that passes

  @object
  Scenario: A scenario needing a declared capability
    Given a step that passes

  @stale
  Scenario: A scenario needing an undeclared capability
    Given a step that passes

  Scenario: A scenario that fails
    Given a step that fails

  Scenario Outline: Requesting the wrong type returns the code default
    Given a step that passes with "<key>" as "<requested>"

    Examples: a string flag requested as something else
      | key         | requested |
      | string-flag | Boolean   |
      | string-flag | Integer   |

    @stale
    Examples: gated by a tag on this block alone
      | key          | requested |
      | boolean-flag | String    |
