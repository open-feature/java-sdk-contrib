@ext-connection
Feature: flagd provider disconnect and reconnect

  # Ported from flagd-testbed gherkin/connection.feature.
  #
  # Not ported: the "stable" row of the Connection outline, which every canonical evaluation
  # scenario asks, and the @unixsocket rows, which need a socket shared between host and container
  # that the TCK stack does not mount. The testbed's "Provider unavailable" is kept although the
  # canonical lifecycle.feature asks the same, because it holds flagd to a 3000ms bound where the
  # canonical scenario allows 10000ms.
  #
  # The outlines over provider flavours are spelled out, one scenario per flavour, because a flavour
  # here is a set of steps rather than a value. Reconnect timing is the testbed's "stable flagd
  # provider", as in events.feature.
  #
  # @targetURI and @forbidden connect through the envoy sidecar in the testbed's
  # test-harness/docker-compose.yaml.

  @rpc @in-process @file @customCert
  Scenario: Connection with a custom certificate
    Given the backend serves the "ssl" configuration
    And an option "tls" of type "Boolean" with value "true"
    And an option "certPath" of type "String" with value "test-harness/ssl/custom-root-cert.crt"
    And a stable provider
    And a Boolean-flag with key "boolean-flag" and a default value "false"
    When the flag was evaluated with details
    Then the resolved details value should be "true"

  @rpc @in-process @file @reconnect
  Scenario: Provider reconnection
    Given an option "retryGracePeriod" of type "Integer" with value "5"
    And an option "retryBackoffMaxMs" of type "Integer" with value "500"
    And a stable provider
    And a ready event handler
    And a error event handler
    When a ready event was fired
    When the connection is lost for 8s
    Then the error event handler should have been executed
    Then the ready event handler should have been executed

  @rpc @in-process @file @reconnect @customCert
  Scenario: Provider reconnection with a custom certificate
    Given the backend serves the "ssl" configuration
    And an option "tls" of type "Boolean" with value "true"
    And an option "certPath" of type "String" with value "test-harness/ssl/custom-root-cert.crt"
    And an option "retryGracePeriod" of type "Integer" with value "5"
    And an option "retryBackoffMaxMs" of type "Integer" with value "500"
    And a stable provider
    And a ready event handler
    And a error event handler
    When a ready event was fired
    When the connection is lost for 8s
    Then the error event handler should have been executed
    Then the ready event handler should have been executed

  @rpc @in-process @file @unavailable
  Scenario: Provider unavailable
    Given an option "deadlineMs" of type "Integer" with value "1000"
    And a unavailable provider
    And a error event handler
    Then the error event handler should have been executed within 3000ms

  # A forbidden response from flagd results in a fatal client state. "a unavailable provider" is the
  # TCK's flavour that does not wait for readiness; the step before it points that provider at envoy.
  @rpc @in-process @forbidden
  Scenario: Provider forbidden
    Given an option "fatalStatusCodes" of type "StringList" with value "PERMISSION_DENIED"
    And the provider connects to envoy's forbidden listener
    And a unavailable provider
    And a error event handler
    Then the error event handler should have been executed within 5000ms
    And the client should be in fatal state

  @targetURI @rpc
  Scenario: Connection via TargetUri rpc
    Given a target URI "envoy://localhost:<port>/rpc.service" routed through envoy
    And a stable provider
    And a Boolean-flag with key "boolean-flag" and a default value "false"
    When the flag was evaluated with details
    Then the resolved details value should be "true"

  @targetURI @in-process
  Scenario: Connection via TargetUri in-process
    Given a target URI "envoy://localhost:<port>/sync.service" routed through envoy
    And a stable provider
    And a Boolean-flag with key "boolean-flag" and a default value "false"
    When the flag was evaluated with details
    Then the resolved details value should be "true"

  @targetURI @in-process @reconnect
  Scenario: Re-Connection via TargetUri in-process
    Given a target URI "envoy://localhost:<port>/sync.service" routed through envoy
    And an option "retryGracePeriod" of type "Integer" with value "5"
    And an option "retryBackoffMaxMs" of type "Integer" with value "500"
    And a stable provider
    And a ready event handler
    And a error event handler
    When a ready event was fired
    When the connection is lost for 8s
    Then the error event handler should have been executed
    Then the ready event handler should have been executed

  @targetURI @in-process @reconnect @grace
  Scenario: Re-Connection via TargetUri in-process with grace period
    Given a target URI "envoy://localhost:<port>/sync.service" routed through envoy
    And an option "retryGracePeriod" of type "Integer" with value "5"
    And an option "retryBackoffMaxMs" of type "Integer" with value "500"
    And a stable provider
    And a ready event handler
    And a stale event handler
    And a error event handler
    When a ready event was fired
    When the connection is lost for 8s
    Then the stale event handler should have been executed
    Then the error event handler should have been executed
    Then the ready event handler should have been executed
