@ext-evaluation @in-process
Feature: flagd selector

  # Ported from flagd-testbed gherkin/selector.feature. The launchpad's default configuration
  # serves both flags/allFlags.json and rawflags/selector-flags.json, so the selector narrows the
  # sync to the second source.

  Background:
    Given an option "selector" of type "String" with value "rawflags/selector-flags.json"
    And a stable provider

  Scenario Outline: Flags not found
    Given a <type>-flag with key "<key>" and a default value "<default>"
    When the flag was evaluated with details
    Then the reason should be "ERROR"

    Examples:
      | key          | type    | default |
      | boolean-flag | Boolean | false   |
      | string-flag  | String  | bye     |
      | integer-flag | Integer | 1       |
      | float-flag   | Float   | 0.1     |

  Scenario: Resolve values
    Given a String-flag with key "selector-flag" and a default value "foo"
    When the flag was evaluated with details
    Then the reason should be "STATIC"
