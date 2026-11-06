Feature: A canonical scenario runs whatever the harness includes or excludes

  Proof for ExcludedTagsTest; canonical scenarios are exempt, and carry only capability tags.

  Scenario: A canonical scenario runs without the included tag
    Given a stable provider
