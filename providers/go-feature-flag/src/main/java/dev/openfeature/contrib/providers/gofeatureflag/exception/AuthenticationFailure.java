package dev.openfeature.contrib.providers.gofeatureflag.exception;

import dev.openfeature.sdk.exceptions.FatalError;
import lombok.experimental.StandardException;

/**
 * Thrown when the relay proxy rejects our credentials (HTTP 401 or 403).
 *
 * <p>It extends the SDK's FatalError, whose error code is PROVIDER_FATAL, because that is what the
 * SDK inspects to move the provider to the FATAL state: credentials cannot be repaired by retrying,
 * so a provider that keeps retrying them would never recover and would hide the real problem.</p>
 */
@StandardException
public class AuthenticationFailure extends FatalError {}
