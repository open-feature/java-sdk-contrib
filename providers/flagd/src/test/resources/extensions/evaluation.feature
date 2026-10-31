@ext-evaluation @rpc @in-process @file
Feature: flagd evaluations

  # Ported from flagd-testbed gherkin/evaluation.feature. Asks what the TCK's evaluation.feature
  # and reason.feature do not: the reason flagd reports for a zero value, a float zero value,
  # zero values served by a targeting rule, and flags with no default variant.
  #
  # Not ported: "Resolve values" (the TCK's evaluation.feature asks it) and the @deprecated
  # @no-default outline, which @no-default-variant replaces.

  Background:
    Given an option "cache" of type "CacheType" with value "disabled"
    And a stable provider

  Scenario Outline: Resolves zero value
    Given a <type>-flag with key "<key>" and a default value "<default>"
    When the flag was evaluated with details
    Then the resolved details value should be "<resolved_value>"
    And the reason should be "STATIC"

    Examples:
      | key               | type    | default | resolved_value |
      | boolean-zero-flag | Boolean | true    | false          |
      | string-zero-flag  | String  | hi      |                |
      | integer-zero-flag | Integer | 1       | 0              |
      | float-zero-flag   | Float   | 0.1     | 0.0            |

  @targeting
  Scenario Outline: Resolves zero value with targeting
    Given a <type>-flag with key "<key>" and a default value "<default>"
    And a context containing a key "email", with type "String" and with value "ballmer@macrosoft.com"
    When the flag was evaluated with details
    Then the resolved details value should be "<resolved_value>"
    And the reason should be "TARGETING_MATCH"

    Examples:
      | key                        | type    | default | resolved_value |
      | boolean-targeted-zero-flag | Boolean | true    | false          |
      | string-targeted-zero-flag  | String  | hi      |                |
      | integer-targeted-zero-flag | Integer | 1       | 0              |
      | float-targeted-zero-flag   | Float   | 0.1     | 0.0            |

  @targeting
  Scenario Outline: Resolves zero value with targeting using default
    Given a <type>-flag with key "<key>" and a default value "<default>"
    And a context containing a key "email", with type "String" and with value "ballmer@none.com"
    When the flag was evaluated with details
    Then the resolved details value should be "<resolved_value>"
    And the reason should be "DEFAULT"

    Examples:
      | key                        | type    | default | resolved_value |
      | boolean-targeted-zero-flag | Boolean | true    | false          |
      | string-targeted-zero-flag  | String  | hi      |                |
      | integer-targeted-zero-flag | Integer | 1       | 0              |
      | float-targeted-zero-flag   | Float   | 0.1     | 0.0            |

  @no-default-variant
  Scenario Outline: Resolves flag with no default variant correctly
    Given a <type>-flag with key "<key>" and a default value "<code_default>"
    And a context containing a key "email", with type "String" and with value "<email>"
    When the flag was evaluated with details
    Then the resolved details value should be "<resolved_value>"
    And the reason should be "<reason>"

    Examples:
      | key                                         | type    | email              | code_default  | resolved_value | reason          |
      | null-default-flag                           | Boolean |                    | true          | true           | DEFAULT         |
      | null-default-flag                           | Boolean |                    | false         | false          | DEFAULT         |
      | undefined-default-flag                      | Integer |                    | 100           | 100            | DEFAULT         |
      | no-default-flag-null-targeting-variant      | String  | wozniak@orange.com | Inventor      | Inventor       | DEFAULT         |
      | no-default-flag-null-targeting-variant      | String  | wozniak@orange.com | Founder       | Founder        | DEFAULT         |
      | no-default-flag-null-targeting-variant      | String  | jobs@orange.com    | CEO           | CEO            | TARGETING_MATCH |
      | no-default-flag-undefined-targeting-variant | String  | wozniak@orange.com | Retired       | Retired        | DEFAULT         |
