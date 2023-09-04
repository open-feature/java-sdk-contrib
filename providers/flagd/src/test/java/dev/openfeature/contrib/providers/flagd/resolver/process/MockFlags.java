package dev.openfeature.contrib.providers.flagd.resolver.process;

import dev.openfeature.contrib.providers.flagd.resolver.process.model.FeatureFlag;

import java.util.HashMap;
import java.util.Map;

public class MockFlags {

    static final Map<String, Object> booleanVariant;
    static final Map<String, Object> stringVariants;

    static {
        booleanVariant = new HashMap<>();
        booleanVariant.put("on", true);
        booleanVariant.put("off", false);

        stringVariants = new HashMap<>();
        stringVariants.put("loop", "loopAlg");
        stringVariants.put("binet", "binetAlg");
    }

    // correct flag
    static final FeatureFlag BOOLEAN_FLAG = new FeatureFlag("ENABLED", "on", booleanVariant, null);

    // flag in disabled state
    static final FeatureFlag DISABLED_FLAG = new FeatureFlag("DISABLED", "on", booleanVariant, null);

    // incorrect flag - variant mismatch
    static final FeatureFlag VARIANT_MISMATCH_FLAG = new FeatureFlag("ENABLED", "true", stringVariants, null);

    // flag with targeting rule
    static final FeatureFlag FLAG_WIH_IF_IN_TARGET = new FeatureFlag("ENABLED", "loop", stringVariants,
            "{\"if\":[{\"in\":[\"@faas.com\",{\"var\":[\"email\"]}]},\"binet\",null]}");

    // flag with incorrect targeting rule
    static final FeatureFlag FLAG_WIH_INVALID_TARGET = new FeatureFlag("ENABLED", "loop", stringVariants,
            "{if this, then that}");
}
