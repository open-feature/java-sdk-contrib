@ext-evaluation @disabled @in-process @rpc
Feature: Disabled flag evaluation

  # Ported from flagd-testbed gherkin/disabled.feature. A flag with state=DISABLED resolves
  # successfully with reason=DISABLED; the evaluator substitutes the caller-provided default value
  # and omits the variant. Relates to: https://github.com/open-feature/flagd/issues/1965
  #
  # The TCK's @disabled-flags scenarios ask the value for the four scalar flags and the reason for
  # disabled-boolean-flag only, so the boolean row is left out here: the remaining rows add the
  # DISABLED reason for each type and the structured flag.

  Scenario Outline: Evaluating a disabled flag returns reason DISABLED and code default
    Given an option "cache" of type "CacheType" with value "disabled"
    And a stable provider
    And a <type>-flag with key "<key>" and a default value "<default>"
    When the flag was evaluated with details
    Then the resolved details value should be "<default>"
    And the reason should be "DISABLED"

    Examples:
      | key                   | type    | default |
      | disabled-string-flag  | String  | bye     |
      | disabled-integer-flag | Integer | 1       |
      | disabled-float-flag   | Float   | 0.1     |
      | disabled-object-flag  | Object  | {}      |

  Scenario Outline: Flag disabled in one flag set, enabled in another
    Given an option "cache" of type "CacheType" with value "disabled"
    And an option "selector" of type "String" with value "<selector>"
    And a stable provider
    And a Boolean-flag with key "cross-flagset-flag" and a default value "false"
    When the flag was evaluated with details
    Then the resolved details value should be "<resolved_value>"
    And the reason should be "<reason>"

    Examples:
      | selector                     | resolved_value | reason   |
      | flags/allFlags.json          | false          | DISABLED |
      | rawflags/selector-flags.json | true           | STATIC   |
