@extension-selftest
Feature: An adopter's own scenarios run inside the TCK suite

  This file is the TCK's own proof of its extension point. It sits exactly where an adopter's
  extension features sit — on the test classpath under tck-extensions/ — and is picked up with no
  annotation, no selector and no runner configuration written anywhere for it.

  It is test-scoped, so it is not packaged in the released JAR and cannot reach an adopter's run.
  It carries no canonical scenario and cannot stand in for one: the canonical set is what features/
  contains, and this file is not in it.

  Scenario: A step class in the adopter's own glue package is on the suite's glue path
    Given a step defined in the extension glue package
    Then the extension step ran
