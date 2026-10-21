@ext-fractional @rpc @in-process @file
Feature: Fractional targeting

  # Ported from the flagd-testbed's gherkin/targeting.feature: every @fractional scenario, plus the
  # fractional @operator-errors ones. The TCK's own @targeting scenarios do not cover the
  # fractional operator.
  #
  # @fractional-vN tags select the bucketing version. The suites run v3, which the Java in-process
  # evaluator and the testbed's flagd implement, and exclude @fractional-v2 with @TckExcludeTags.
  # Untagged scenarios must hold for every version.
  # The @fractional-v1 blocks are left out: no backend here implements v1 bucketing.
  #
  # Where the testbed's context values are Objects or integers outside 32 bits, the TCK's context
  # step cannot convert them, so those outlines use the table step with the testbed's conversion.

  Background:
    Given an option "cache" of type "CacheType" with value "disabled"
    And a stable provider

  @fractional
  Scenario Outline: Fractional operator
    Given a String-flag with key "fractional-flag" and a default value "fallback"
    And a context containing a nested property with outer key "user" and inner key "name", with value "<name>"
    When the flag was evaluated with details
    Then the resolved details value should be "<value>"

    @fractional-v2
    Examples: v2
      | name  | value    |
      | jack  | hearts   |
      | queen | spades   |
      | ten   | clubs    |
      | nine  | diamonds |
      | 3     | clubs    |

    @fractional-v3
    Examples: v3
      | name  | value    |
      | jack  | diamonds |
      | queen | diamonds |
      | ten   | clubs    |
      | nine  | clubs    |
      | 3     | spades   |

  @fractional
  Scenario Outline: Fractional operator shorthand
    Given a String-flag with key "fractional-flag-shorthand" and a default value "fallback"
    And a context containing a targeting key with value "<targeting key>"
    When the flag was evaluated with details
    Then the resolved details value should be "<value>"

    @fractional-v2
    Examples: v2
      | targeting key    | value |
      | jon@company.com  | heads |
      | jane@company.com | tails |

    @fractional-v3
    Examples: v3
      | targeting key    | value |
      | jon@company.com  | tails |
      | jane@company.com | tails |

  @fractional @fractional-v2 @fractional-single-entry
  Scenario: Fractional operator with single entry always resolves to the only variant
    Given a String-flag with key "fractional-single-entry-flag" and a default value "fallback"
    And a context containing a targeting key with value "some-targeting-key"
    When the flag was evaluated with details
    Then the resolved details value should be "single"

  @fractional
  Scenario Outline: Fractional operator with shared seed
    Given a String-flag with key "fractional-flag-A-shared-seed" and a default value "fallback"
    And a context containing a nested property with outer key "user" and inner key "name", with value "<name>"
    When the flag was evaluated with details
    Then the resolved details value should be "<value>"

    @fractional-v2
    Examples: v2
      | name  | value    |
      | seven | hearts   |
      | eight | diamonds |
      | nine  | clubs    |
      | two   | spades   |

    @fractional-v3
    Examples: v3
      | name  | value    |
      | seven | hearts   |
      | eight | hearts   |
      | nine  | diamonds |
      | two   | diamonds |

  @fractional
  Scenario Outline: Second fractional operator with shared seed
    Given a String-flag with key "fractional-flag-B-shared-seed" and a default value "fallback"
    And a context containing a nested property with outer key "user" and inner key "name", with value "<name>"
    When the flag was evaluated with details
    Then the resolved details value should be "<value>"

    @fractional-v2
    Examples: v2
      | name  | value           |
      | seven | ace-of-hearts   |
      | eight | ace-of-diamonds |
      | nine  | ace-of-clubs    |
      | two   | ace-of-spades   |

    @fractional-v3
    Examples: v3
      | name  | value           |
      | seven | ace-of-hearts   |
      | eight | ace-of-hearts   |
      | nine  | ace-of-diamonds |
      | two   | ace-of-diamonds |

  # Hash edge-case vectors — keys chosen by brute-force search so their
  # MurmurHash3-x86-32 (seed=0) falls at the six critical boundary values.
  # All keys are exact 6-char MurmurHash3-x86-32 (seed=0) preimages found by exhaustive search.
  # ejOoVL → hash=0          EXACT → bv(100)=0  → lower
  # bY9fO- → hash=1          EXACT → bv(100)=0  → lower
  # SI7p-  → hash=2147483647 EXACT i32::MAX     → bv(100)=49 → lower
  # 6LvT0  → hash=2147483648 EXACT i32::MIN u32 → bv(100)=50 → upper
  # ceQdGm → hash=4294967295 EXACT u32::MAX     → bv(100)=99 → upper
  @fractional @fractional-v2
  Scenario Outline: Fractional operator hash edge cases
    Given a String-flag with key "fractional-hash-edge-flag" and a default value "fallback"
    And a context containing a targeting key with value "<key>"
    When the flag was evaluated with details
    Then the resolved details value should be "<value>"
    Examples:
      | key    | value |
      | ejOoVL | lower |
      | bY9fO- | lower |
      | SI7p-  | lower |
      | 6LvT0  | upper |
      | ceQdGm | upper |

  # Nested JSON Logic expressions as bucket variant names.
  # Requires providers to support the @fractional-nested feature.
  # Use -t "not @fractional-nested" to exclude during transition.
  @fractional @fractional-nested
  Scenario Outline: Fractional operator with nested if expression as variant name
    # fractional-nested-if-flag: seed=targetingKey, bucket0=[if(tier=="premium","premium","standard"),50], bucket1=["standard",50]
    # jon@company.com bv(100)=36 → bucket0; user1 bv(100)=76 → bucket1
    Given a String-flag with key "fractional-nested-if-flag" and a default value "fallback"
    And a context containing a targeting key with value "<targetingKey>"
    And a context containing a key "tier", with type "String" and with value "<tier>"
    When the flag was evaluated with details
    Then the resolved details value should be "<value>"
    Examples:
      | targetingKey    | tier    | value    |
      | jon@company.com | premium | premium  |
      | jon@company.com | basic   | standard |
      | user1           | premium | standard |
      | user1           | basic   | standard |

  @fractional @fractional-nested
  Scenario Outline: Fractional operator with nested var expression as variant name
    # fractional-nested-var-flag: seed=targetingKey, bucket0=[var("color"),50], bucket1=["blue",50]
    # jon@company.com bv(100)=36 → bucket0 (resolves var "color"); user1 bv(100)=76 → bucket1 ("blue")
    Given a String-flag with key "fractional-nested-var-flag" and a default value "fallback"
    And a context containing a targeting key with value "<targetingKey>"
    And a context containing a key "color", with type "String" and with value "<color>"
    When the flag was evaluated with details
    Then the resolved details value should be "<value>"
    Examples:
      | targetingKey    | color  | value    |
      | jon@company.com | red    | red      |
      | jon@company.com | green  | green    |
      | user1           | red    | blue     |
      | jon@company.com | yellow | fallback |
      | jon@company.com |        | fallback |

  @fractional @fractional-nested
  Scenario Outline: Fractional operator with nested if expression as weight
    # fractional-nested-weight-flag: seed=targetingKey, bucket0=["red",if(tier=="premium",100,0)], bucket1=["blue",10]
    Given a String-flag with key "fractional-nested-weight-flag" and a default value "fallback"
    And a context containing a targeting key with value "<targetingKey>"
    And a context containing a key "tier", with type "String" and with value "<tier>"
    When the flag was evaluated with details
    Then the resolved details value should be "<value>"
    Examples:
      | targetingKey    | tier    | value   |
      | jon@company.com | premium | red     |
      | jon@company.com | basic   | blue    |
      | user1           | premium | red     |
      | user1           | basic   | blue    |

  @fractional @fractional-nested
  Scenario: Fractional as condition
    Given a String-flag with key "fractional-as-condition-flag" and a default value "zero"
    And a context containing a targeting key with value "some-targeting-key"
    When the flag was evaluated with details
    Then the resolved details value should be "hundreds"

  @fractional @fractional-nested
  Scenario: Fractional as condition evaluates false path
    Given a String-flag with key "fractional-as-condition-false-flag" and a default value "zero"
    And a context containing a targeting key with value "some-targeting-key"
    When the flag was evaluated with details
    Then the resolved details value should be "ones"

  @operator-errors
  Scenario: fractional operator with missing bucket key falls back to default variant
    Given a String-flag with key "fractional-null-bucket-key-flag" and a default value "wrong"
    When the flag was evaluated with details
    Then the resolved details value should be "fallback"
    And the reason should be "DEFAULT"

  @operator-errors @fractional
  Scenario: fractional with all-zero bucket weights falls back to default variant
    Given a String-flag with key "fractional-zero-weights-flag" and a default value "wrong"
    And a context containing a targeting key with value "any-user"
    When the flag was evaluated with details
    Then the resolved details value should be "fallback"
    And the reason should be "DEFAULT"

  @operator-errors @fractional
  Scenario: fractional negative bucket weight is clamped to zero
    # ["one", -50] is treated as ["one", 0]; "two" gets 100% of the weight
    Given a String-flag with key "fractional-negative-weight-flag" and a default value "wrong"
    And a context containing a targeting key with value "any-user"
    When the flag was evaluated with details
    Then the resolved details value should be "two"

  @fractional @fractional-v3
  Scenario Outline: Fractional operator with basic types
    Given a String-flag with key "fractional-basic-flag" and a default value "fallback"
    And a context containing a key "hashing_input", with type "<type>" and with value "<hashing_input>"
    When the flag was evaluated with details
    Then the resolved details value should be "<value>"

    Examples: v3
      | hashing_input | type    | value    |
      | true          | Boolean | bucket5  |
      | false         | Boolean | bucket1  |
      | user1         | String  | bucket22 |
      | user2         | String  | bucket16 |
      | 123           | Integer | bucket20 |
      | 456           | Integer | bucket12 |
      | 1.23          | Float   | bucket14 |
      | 4.56          | Float   | bucket18 |
      | 123           | String  | bucket22 |
      | true          | String  | bucket24 |
      | false         | String  | bucket1  |
      | null          | String  | bucket8  |
      | 1.23          | String  | bucket4  |
      | 0             | Integer | bucket8  |

  @fractional @fractional-v3
  Scenario Outline: Fractional operator with float mapping
    Given a String-flag with key "fractional-basic-flag" and a default value "fallback"
    And a context with the following keys:
      | key           | type    | value           |
      | hashing_input | <type>  | <hashing_input> |
    When the flag was evaluated with details
    Then the resolved details value should be "<value>"

    Examples: v3
      | hashing_input      | type    | value    |
      | 1.0                | Float   | bucket23 |
      | 1                  | Integer | bucket23 |
      | 1.0000000000000001 | Float   | bucket23 |
      | -2.0               | Float   | bucket12 |
      | -2                 | Integer | bucket12 |
      | 9007199254740992.0 | Float   | bucket10 |
      | 9007199254740992   | Integer | bucket10 |

  @fractional @fractional-v3
  Scenario Outline: Fractional operator with zero values
    Given a String-flag with key "fractional-basic-flag" and a default value "fallback"
    And a context containing a key "hashing_input", with type "<type>" and with value "<hashing_input>"
    When the flag was evaluated with details
    Then the resolved details value should be "<value>"

    Examples: v3
      | hashing_input | type    | value   |
      | 0.0           | Float   | bucket8 |
      | -0.0          | Float   | bucket8 |
      | 0             | Integer | bucket8 |

  @fractional @fractional-v3
  Scenario Outline: Fractional operator with integer limits
    Given a String-flag with key "fractional-basic-flag" and a default value "fallback"
    And a context with the following keys:
      | key           | type    | value           |
      | hashing_input | Integer | <hashing_input> |
    When the flag was evaluated with details
    Then the resolved details value should be "<value>"

    Examples: v3
      | hashing_input    | value    |
      | 2147483647       | bucket10 |
      | 2147483648       | bucket25 |
      | -2147483648      | bucket8  |
      | -2147483649      | bucket18 |
      | 9007199254740991 | bucket2  |
      | 23               | bucket21 |
      | 24               | bucket11 |
      | 255              | bucket13 |
      | 256              | bucket13 |
      | 65535            | bucket4  |
      | 65536            | bucket11 |
      | 4294967295       | bucket14 |
      | 4294967296       | bucket19 |
      | -24              | bucket4  |
      | -25              | bucket16 |

  @fractional @fractional-v3
  Scenario Outline: Fractional operator with floats limits
    Given a String-flag with key "fractional-basic-flag" and a default value "fallback"
    And a context containing a key "hashing_input", with type "Float" and with value "<hashing_input>"
    When the flag was evaluated with details
    Then the resolved details value should be "<value>"

    Examples: v3
      | hashing_input              | value    |
      | 1e20                       | bucket19 |
      | -1e20                      | bucket25 |
      | 65504.0                    | bucket16 |
      | -65504.0                   | bucket9  |
      | 0.00006103515625           | bucket22 |
      | 0.000000059604644775390625 | bucket23 |
      | 65505.0                    | bucket17 |
      | -65505.0                   | bucket17 |
      | 3.4028234663852886e+38     | bucket10 |
      | -3.4028234663852886e+38    | bucket3  |
      | 1.1754943508222875e-38     | bucket24 |
      | 3.5e+38                    | bucket1  |
      | -3.5e+38                   | bucket6  |
      | 1.7976931348623157e+308    | bucket17 |
      | -1.7976931348623157e+308   | bucket9  |
      | 2.2250738585072014e-308    | bucket11 |
      | 4.9406564584124654e-324    | bucket2  |

  @fractional @fractional-v3
  Scenario Outline: Fractional operator with map key ordering
    Given a String-flag with key "fractional-basic-flag" and a default value "fallback"
    And a context with the following keys:
      | key           | type    | value           |
      | hashing_input | Object  | <hashing_input> |
    When the flag was evaluated with details
    Then the resolved details value should be "<value>"

    Examples: v3
      | hashing_input                                    | value    |
      | {"a": 1, "b": 2}                                 | bucket23 |
      | {"b": 2, "a": 1}                                 | bucket23 |
      | {"a": 1, "b": 2, "c": 3}                         | bucket18 |
      | {"c": 3, "a": 1, "b": 2}                         | bucket18 |
      | {"z": 1, "aa": 2}                                | bucket21 |
      | {"aa": 2, "z": 1}                                | bucket21 |
      | {"a": {"b": 1}, "b": 2}                          | bucket9  |
      | {"b": 2, "a": {"b": 1}}                          | bucket9  |
      | {"a": true, "bbb": 1, "c": "text", "dddd": 2.5}  | bucket25 |
      | {"c": "text", "bbb": 1, "a": true, "dddd": 2.5}  | bucket25 |
      | {"aaa": 1, "ÿ": 2}                               | bucket12 |
      | {"ÿ": 2, "aaa": 1}                               | bucket12 |
      | {"": 2, "abc": 1}                                | bucket25 |
      | {"key": -0.0}                                    | bucket10 |
      | {"key": 0}                                       | bucket10 |
      | {"key": 0.0}                                     | bucket10 |
      | {"b": -0.0, "a": {"b": 1}, "c": 0.0}             | bucket8  |
      | {"c": 0.0, "b": -0.0, "a": {"b": 1}}             | bucket8  |
      | {"café": "façade", "b": {"résumé": {"ÿ": true}}} | bucket22 |

  @fractional @fractional-v3
  Scenario Outline: Fractional operator with advanced structures
    Given a String-flag with key "fractional-basic-flag" and a default value "fallback"
    And a context with the following keys:
      | key           | type    | value           |
      | hashing_input | Object  | <hashing_input> |
    When the flag was evaluated with details
    Then the resolved details value should be "<value>"

    Examples: v3
      | hashing_input                                          | value    |
      | {}                                                     | bucket3  |
      | {"a": {} }                                             | bucket20 |
      | {"b": 2, "a": {} }                                     | bucket20 |
      | {"a": {}, "b": {}}                                     | bucket21 |
      | {"a": {}, "b": []}                                     | bucket7  |
      | {"c": 0.0, "a": []}                                    | bucket18 |
      | {"c": 0.0, "b": -0.0, "a": {"b": []}}                  | bucket9  |
      | {"a": [] }                                             | bucket12 |
      | {"a": [1, [], 3], "b": null}                           | bucket3  |
      | {"a": ["a", "b", {}], "b": [1, null, 2]}               | bucket23 |
      | {"a": [1, "two", true, [], null]}                      | bucket23 |
      | {"a": [false, 2.5, ""], "b": [], "c": [{}], "c": null} | bucket15 |
      | {"x": [{"a": 1}, {"b": 2}, {"b": null}]}               | bucket3  |
      | {"x": [{"a": {"b": []}}] }                             | bucket16 |
      | {"x": [{"a": {"b": null}}] }                           | bucket6  |
      | {"x": [{"a": {"b": {}}}] }                             | bucket2  |
      | {"": "", "": [], "": {}, "": null}                     | bucket25 |
      | {"": {"": ""}, "": ["", {"": ""}], "": {"": {"": ""}}} | bucket11 |
      | {"a": [[[[[]]]]] }                                     | bucket15 |
      | {"a": [[[[null]]]] }                                   | bucket7  |
      | {"a": {"b": {"c": {"d": {}}} } }                       | bucket21 |
      | {"a": {"b": {"c": {"d": null}} } }                     | bucket19 |

  @fractional @fractional-v3
  Scenario Outline: Fractional operator with string length boundaries
    Given a String-flag with key "fractional-basic-flag" and a default value "fallback"
    And a context containing a key "hashing_input", with type "String" and with value "<hashing_input>"
    When the flag was evaluated with details
    Then the resolved details value should be "<value>"

    Examples: v3
      | hashing_input                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                          | value    |
      |                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                        | bucket4  |
      | a                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                      | bucket6  |
      | 12345678901234567890123                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                | bucket4  |
      | 123456789012345678901234                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                               | bucket14 |
      | aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa  | bucket18 |
      | aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa | bucket7  |

  @fractional @fractional-v3
  Scenario Outline: Valid well-formed UTF-8 String consistency across different languages
    Given a String-flag with key "fractional-basic-flag" and a default value "fallback"
    And a context containing a key "hashing_input", with type "String" and with value "<hashing_input>"
    When the flag was evaluated with details
    Then the resolved details value should be "<value>"

    Examples: v3
      | hashing_input           | value    |
      | あいうえおかき            | bucket17 |
      | あいうえおかきく           | bucket16 |
      | ééééééééééé             | bucket16 |
      | éééééééééééé            | bucket19 |
      | café façade résumé café | bucket9  |
      | A\u0000B                | bucket15 |
      | e\u0301                 | bucket7  |
      | \uD83D\uDE00            | bucket23 |

  @fractional @fractional-v3
  Scenario Outline: Fractional operator using implicit targeting key
    Given a String-flag with key "fractional-flag-shorthand" and a default value "draw"
    And a context containing a targeting key with value "<targeting_key>"
    When the flag was evaluated with details
    Then the resolved details value should be "<value>"

    Examples: v3
      | targeting_key | value |
      | user-1        | heads |
      | user-2        | tails |
      | user-3        | heads |

  @fractional @fractional-v3
  Scenario Outline: Fractional operator invalid implicit inputs
    Given a String-flag with key "fractional-flag-shorthand" and a default value "fallback"
    And a context containing a key "<key>", with type "<type>" and with value "<value>"
    When the flag was evaluated with details
    Then the resolved details value should be "draw"
    Then the reason should be "DEFAULT"

    Examples: v3
      | key          | type    | value |
      | irrelevant   | String  | none  |
      | targetingKey | Integer | 12345 |
      | targetingKey | Boolean | true  |
