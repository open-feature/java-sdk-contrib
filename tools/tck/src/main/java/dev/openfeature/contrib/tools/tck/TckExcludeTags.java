package dev.openfeature.contrib.tools.tck;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Skips extension scenarios carrying any of these tags, even if they carry a
 * {@link TckIncludeTags} tag. Canonical scenarios are exempt. Put it on the
 * {@link ProviderTckHarness} class; declarations on the harness and its superclasses add up.
 * Capability tags are refused.
 *
 * <pre>{@code
 * @TckIncludeTags("@rpc")
 * @TckExcludeTags({"@fractional-v1", "@fractional-v2"})
 * public class RpcTest extends AbstractResolverTest { ... }
 * }</pre>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface TckExcludeTags {

    /**
     * Returns the tags to skip.
     *
     * @return the tags, with the leading at-sign
     */
    String[] value();
}
