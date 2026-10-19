@extension-selftest
Feature: An adopter's own scenarios run inside the TCK suite

  The TCK's own proof of its extension point; test-scoped, so not in the released JAR.

  Scenario: A step class in the adopter's own glue package is on the suite's glue path
    Given a step defined in the extension glue package
    Then the extension step ran
