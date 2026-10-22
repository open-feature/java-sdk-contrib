package dev.openfeature.contrib.tools.tck;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Runs only the extension scenarios carrying at least one of these tags; tag a scenario for
 * several modes to run it in each. Canonical scenarios are exempt. Put it on the
 * {@link ProviderTckHarness} class; declarations on the harness and its superclasses add up.
 * Capability tags are refused.
 *
 * <pre>{@code
 * @TckIncludeTags("@rpc")
 * public class RpcTest extends AbstractResolverTest { ... }  // runs "@rpc" and "@rpc @in-process"
 * }</pre>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface TckIncludeTags {

    /**
     * Returns the tags an extension scenario must carry one of.
     *
     * @return the tags, with the leading at-sign
     */
    String[] value();
}
