@ext-metadata @in-process @file @metadata
Feature: flag and flag set metadata

  # Ported from flagd-testbed gherkin/metadata.feature. The testbed's "a stable flagd provider"
  # becomes the TCK's "a stable provider", and its "a metadata flagd provider" the "metadata"
  # launchpad configuration followed by "a stable provider". @file is dropped: this suite has no
  # file resolver.

  Scenario: Returns metadata
    Given a stable provider
    And a Boolean-flag with key "metadata-flag" and a default value "true"
    When the flag was evaluated with details
    Then the resolved metadata should contain
      | key     | metadata_type | value |
      | string  | String        | a     |
      | integer | Integer       | 1     |
      | float   | Float         | 1.2   |
      | boolean | Boolean       | true  |

  @metadata-provider
  Scenario: Returns flag set metadata
    Given an option "selector" of type "String" with value "rawflags/selector-flag-combined-metadata.json"
    And the flagd backend serves the "metadata" configuration
    And a stable provider
    And a Boolean-flag with key "only-set-metadata-flag" and a default value "true"
    When the flag was evaluated with details
    Then the resolved metadata should contain
      | key              | metadata_type | value |
      | string           | String        | b     |
      | integer          | Integer       | 2     |
      | float            | Float         | 2.2   |
      | boolean          | Boolean       | false |
      | flag-set-string  | String        | c     |
      | flag-set-integer | Integer       | 3     |
      | flag-set-float   | Float         | 3.2   |
      | flag-set-boolean | Boolean       | false |

  @metadata-provider
  Scenario: Flag metadata overwrites flag set metadata
    Given an option "selector" of type "String" with value "rawflags/selector-flag-combined-metadata.json"
    And the flagd backend serves the "metadata" configuration
    And a stable provider
    And a Boolean-flag with key "combined-metadata-flag" and a default value "true"
    When the flag was evaluated with details
    Then the resolved metadata should contain
      | key              | metadata_type | value |
      | string           | String        | a     |
      | integer          | Integer       | 1     |
      | float            | Float         | 1.2   |
      | boolean          | Boolean       | true  |
      | flag-set-string  | String        | c     |
      | flag-set-integer | Integer       | 3     |
      | flag-set-float   | Float         | 3.2   |
      | flag-set-boolean | Boolean       | false |

  Scenario: Returns no metadata
    Given a Boolean-flag with key "boolean-flag" and a default value "true"
    And a stable provider
    When the flag was evaluated with details
    Then the resolved metadata is empty
