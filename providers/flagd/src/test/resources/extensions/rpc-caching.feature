@ext-connection @rpc @flagd-caching
Feature: Flag evaluation with caching

  # Ported from flagd-testbed gherkin/rpc-caching.feature (RPC only). The testbed's @caching tag is
  # a reserved TCK capability, which the TCK refuses on any scenario, so this feature carries
  # @flagd-caching instead.
  #
  # Reconnect timing is the testbed's "stable flagd provider", as in events.feature.

  Background:
    Given an option "cache" of type "CacheType" with value "lru"
    And an option "retryGracePeriod" of type "Integer" with value "5"
    And an option "retryBackoffMaxMs" of type "Integer" with value "500"
    And a stable provider

  Scenario Outline: Resolves <type> details with caching
    Given a <type>-flag with key "<key>" and a default value "<default>"
    When the flag was evaluated with details
    Then the resolved details value should be "<resolved_value>"
    And the variant should be "<resolved_variant>"
    And the reason should be "STATIC"
    When the flag was evaluated with details
    Then the resolved details value should be "<resolved_value>"
    And the variant should be "<resolved_variant>"
    And the reason should be "CACHED"

    Examples:
      | key          | type    | default | resolved_variant | resolved_value                                                                |
      | boolean-flag | Boolean | false   | on               | true                                                                          |
      | string-flag  | String  | bye     | greeting         | hi                                                                            |
      | integer-flag | Integer | 1       | ten              | 10                                                                            |
      | float-flag   | Float   | 0.1     | half             | 0.5                                                                           |
      | object-flag  | Object  | {}      | template         | {"showImages": true, "title": "Check out these pics!", "imagesPerPage": 100.0 } |

  Scenario: Flag change event with caching
    Given a String-flag with key "changing-flag" and a default value "false"
    And a change event handler
    When the flag was modified
    And a change event was fired
    And the flag was evaluated with details
    Then the reason should be "STATIC"
    When the flag was evaluated with details
    Then the reason should be "CACHED"
    When the flag was modified
    And a change event was fired
    And the flag was evaluated with details
    Then the reason should be "STATIC"
    When the flag was evaluated with details
    Then the reason should be "CACHED"

  Scenario: Stale and Error stage
    Given a ready event handler
    And a stale event handler
    And a error event handler
    And a Boolean-flag with key "boolean-flag" and a default value "false"
    When the flag was evaluated with details
    Then the reason should be "STATIC"
    When the flag was evaluated with details
    Then the reason should be "CACHED"
    When the connection is lost for 6s
    And a stale event was fired
    And the flag was evaluated with details
    Then the reason should be "CACHED"
    When a error event was fired
    And a ready event was fired
    And the flag was evaluated with details
    Then the reason should be "STATIC"
