# Fixture for ConformanceReportPluginTest, not part of the conformance suite: it lives under a
# separate classpath root so nothing selecting "gherkin" picks it up. Shaped like the real suite
# (capability tags on the feature, a scenario and one Examples block; an outline whose rows share a
# name), and it carries both kinds of skip: an undeclared capability, and one no provider in this
# language can be asked.

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

  @large-integers
  Scenario: A scenario needing a capability this SDK cannot express
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
