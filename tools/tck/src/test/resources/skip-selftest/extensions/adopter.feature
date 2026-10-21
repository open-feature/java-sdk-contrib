Feature: An adopter selects its own extension scenarios by tag

  Proof of TckIncludeTags and TckExcludeTags, run by ExcludedTagsTest. Kept outside the shared
  extensions/ directory so the other suites never pick it up.

  @mode-a
  Scenario: An extension scenario carrying the included tag
    Given a stable provider

  @mode-a @mode-b
  Scenario: An extension scenario tagged for both modes
    Given a stable provider

  @mode-b
  Scenario: An extension scenario tagged for the other mode only
    Given a stable provider

  @mode-a @old-version @events
  Scenario: An extension scenario carrying an excluded tag
    Given a stable provider

  @mode-b @filtered
  Scenario: An extension scenario a tag filter removes first
    Given a stable provider
