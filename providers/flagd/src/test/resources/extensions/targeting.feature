@ext-targeting @rpc @in-process
Feature: Targeting rules

  # Ported from flagd-testbed's gherkin/targeting.feature: the json-evaluation scenarios the Provider
  # TCK does not ask. The @fractional scenarios live in their own extension feature.
  #
  # Not ported:
  # - "Targeting by targeting key": the TCK's evaluation.feature (@targeting) asserts both values
  #   and reason.feature asserts TARGETING_MATCH and DEFAULT for the same flag and keys.
  # - "fractional operator with missing bucket key": a fractional scenario, despite its tags.
  #
  # Deviations from the testbed's wording:
  # - "a stable flagd provider" collides with the TCK's "a {} provider", so the provider step is the
  #   TCK's "a stable provider".
  # - "Time-based operations" sets the context with "a context with the following keys:", because the
  #   TCK's context step parses Integer as 32 bits and 4133980802 does not fit.

  Background:
    Given an option "cache" of type "CacheType" with value "disabled"
    And a stable provider

  # evaluator refs
  Scenario Outline: Evaluator reuse
    Given a String-flag with key "<key>" and a default value "fallback"
    And a context containing a key "email", with type "String" and with value "ballmer@macrosoft.com"
    When the flag was evaluated with details
    Then the resolved details value should be "<value>"
    Examples:
      | key                            | value |
      | some-email-targeted-flag       | hi    |
      | some-other-email-targeted-flag | yes   |

  @evaluator-refs @evaluator-refs-whitespace
  Scenario Outline: Evaluator $ref resolves regardless of whitespace around the colon
    Given a String-flag with key "<key>" and a default value "fallback"
    And a context containing a key "email", with type "String" and with value "<email>"
    When the flag was evaluated with details
    Then the resolved details value should be "<value>"
    And the reason should be "TARGETING_MATCH"
    Examples:
      | key                                    | email                 | value |
      | ref-whitespace-compact-flag            | ballmer@macrosoft.com | hi    |
      | ref-whitespace-space-after-colon-flag  | ballmer@macrosoft.com | hi    |
      | ref-whitespace-space-around-colon-flag | ballmer@macrosoft.com | hi    |
      | ref-whitespace-compact-flag            | user@example.com      | bye   |
      | ref-whitespace-space-after-colon-flag  | user@example.com      | bye   |
      | ref-whitespace-space-around-colon-flag | user@example.com      | bye   |

  @evaluator-refs @non-existent-evaluator-ref
  Scenario: Ref to nonexistent evaluator yields parse error
    Given a String-flag with key "ref-to-nonexistent-evaluator-flag" and a default value "fallback"
    When the flag was evaluated with details
    Then the resolved details value should be "fallback"
    And the error-code should be "PARSE_ERROR"

  @string
  Scenario Outline: Substring operators
    Given a String-flag with key "starts-ends-flag" and a default value "fallback"
    And a context containing a key "id", with type "String" and with value "<id>"
    When the flag was evaluated with details
    Then the resolved details value should be "<value>"
    Examples:
      | id     | value   |
      | abcdef | prefix  |
      | uvwxyz | postfix |
      | abcxyz | prefix  |
      | lmnopq | none    |
      | 3      | none    |

  @semver
  Scenario Outline: Semantic version operator numeric comparison
    Given a String-flag with key "equal-greater-lesser-version-flag" and a default value "fallback"
    And a context containing a key "version", with type "String" and with value "<version>"
    When the flag was evaluated with details
    Then the resolved details value should be "<value>"
    Examples:
      | version     | value   |
      | 2.0.0       | equal   |
      | 2.1.0       | greater |
      | 1.9.0       | lesser  |
      | 2.0.0-alpha | lesser  |
      | 2.0.0.0     | none    |

  @semver
  Scenario Outline: Semantic version operator semantic comparison
    Given a String-flag with key "major-minor-version-flag" and a default value "fallback"
    And a context containing a key "version", with type "String" and with value "<version>"
    When the flag was evaluated with details
    Then the resolved details value should be "<value>"
    Examples:
      | version | value |
      | 3.0.1   | minor |
      | 3.1.0   | major |
      | 4.0.0   | none  |

  @semver @semver-edge-cases @semver-v-prefix
  Scenario Outline: sem_ver v-prefix handling
    Given a String-flag with key "semver-v-prefix-flag" and a default value "fallback"
    And a context containing a key "version", with type "String" and with value "<version>"
    When the flag was evaluated with details
    Then the resolved details value should be "<value>"
    Examples:
      | version | value    |
      | 1.0.0   | match    |
      | v1.0.0  | match    |
      | V1.0.0  | match    |
      | 2.0.0   | no-match |

  @semver @semver-edge-cases @semver-partial-version
  Scenario Outline: sem_ver partial version string handling
    Given a String-flag with key "semver-partial-version-flag" and a default value "fallback"
    And a context containing a key "version", with type "<type>" and with value "<version>"
    When the flag was evaluated with details
    Then the resolved details value should be "<value>"
    Examples:
      | version | type    | value    |
      | 1.5.0   | String  | match    |
      | 1.0.0   | String  | match    |
      | 1.0     | String  | match    |
      | 1       | String  | match    |
      | 1       | Integer | match    |
      | 1       | Float   | match    |
      | 1.2     | Float   | match    |
      | 2.0.0   | String  | no-match |

  @semver @semver-edge-cases @semver-numeric-context
  Scenario Outline: sem_ver numeric context value coercion
    Given a String-flag with key "semver-numeric-context-flag" and a default value "fallback"
    And a context containing a key "version", with type "<type>" and with value "<version>"
    When the flag was evaluated with details
    Then the resolved details value should be "<value>"
    Examples:
      | version | type    | value    |
      | 1.2     | Float   | match    |
      | 1.1     | Float   | no-match |
      | 2       | Integer | match    |
      | 1       | Integer | no-match |
      | 1.2     | String  | match    |

  @semver @semver-edge-cases @semver-build-metadata
  Scenario Outline: sem_ver build metadata ignored
    Given a String-flag with key "semver-build-metadata-flag" and a default value "fallback"
    And a context containing a key "version", with type "String" and with value "<version>"
    When the flag was evaluated with details
    Then the resolved details value should be "<value>"
    Examples:
      | version     | value    |
      | 1.0.0       | match    |
      | 1.0.0+other | match    |
      | 2.0.0       | no-match |

  Scenario Outline: Time-based operations
    Given a Integer-flag with key "timestamp-flag" and a default value "0"
    And a context with the following keys:
      | key  | type    | value  |
      | time | Integer | <time> |
    When the flag was evaluated with details
    Then the resolved details value should be "<value>"
    Examples:
      | time       | value |
      | 1          | -1    |
      | 4133980802 | 1     |

  Scenario Outline: Errors and edge cases
    Given a Integer-flag with key "<key>" and a default value "3"
    When the flag was evaluated with details
    Then the resolved details value should be "<value>"
    And the error-code should be "<error_code>"
    Examples:
      | key                               | value | error_code  |
      | targeting-null-variant-flag       | 2     |             |
      | error-targeting-flag              | 3     | PARSE_ERROR |
      | missing-variant-targeting-flag    | 3     | GENERAL     |
      | non-string-variant-targeting-flag | 2     |             |
      | empty-targeting-flag              | 1     |             |
      | targeting-null-flag               | 2     |             |

  @operator-errors
  Scenario Outline: Custom operator errors return null and fall back to default variant
    Given a String-flag with key "<key>" and a default value "wrong"
    And a context containing a key "version", with type "String" and with value "<context_value>"
    When the flag was evaluated with details
    Then the resolved details value should be "fallback"
    And the reason should be "DEFAULT"
    Examples:
      | key                          | context_value |
      | semver-invalid-version-flag  | not-a-version |
      | semver-invalid-operator-flag | 1.0.0         |

  # Follow-up error scenarios from https://github.com/open-feature/flagd/issues/1874
  # Operators must return null (not false) on error so the default variant is selected.

  @operator-errors @string
  Scenario Outline: starts_with and ends_with return null for non-string input
    Given a String-flag with key "<key>" and a default value "wrong"
    And a context containing a key "num", with type "Integer" and with value "123"
    When the flag was evaluated with details
    Then the resolved details value should be "fallback"
    And the reason should be "DEFAULT"
    Examples:
      | key                         |
      | starts-with-non-string-flag |
      | ends-with-non-string-flag   |

  @operator-errors @string
  Scenario Outline: starts_with and ends_with return null for wrong argument count
    Given a String-flag with key "<key>" and a default value "wrong"
    When the flag was evaluated with details
    Then the resolved details value should be "fallback"
    And the reason should be "DEFAULT"
    Examples:
      | key                         |
      | starts-with-wrong-args-flag |
      | ends-with-wrong-args-flag   |

  @operator-errors @semver
  Scenario: sem_ver returns null for wrong argument count
    Given a String-flag with key "semver-wrong-args-flag" and a default value "wrong"
    And a context containing a key "version", with type "String" and with value "1.0.0"
    When the flag was evaluated with details
    Then the resolved details value should be "fallback"
    And the reason should be "DEFAULT"
