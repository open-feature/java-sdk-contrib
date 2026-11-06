Feature: A reserved capability tag on a scenario fails the run

  Proof of the Capability.reserved() expiry check, run by ReservedTagExpiryTest. Kept outside
  extensions/ since the second scenario is meant to fail.

  Scenario: A Gherkin comment naming a reserved tag is prose, not a tag
    # untagged; this comment mentions @caching, which must not count as a tag
    Given a stable provider

  @caching
  Scenario: A tag this implementation still calls reserved fails the run
    Given a stable provider
