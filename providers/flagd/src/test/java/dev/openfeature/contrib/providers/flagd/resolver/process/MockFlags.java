package dev.openfeature.contrib.providers.flagd.resolver.process;

import dev.openfeature.contrib.providers.flagd.resolver.process.model.FeatureFlag;
import java.util.HashMap;
import java.util.Map;

public class MockFlags {

    static final Map<String, Object> booleanVariant;
    static final Map<String, Object> shorthandVariant;
    static final Map<String, Object> stringVariants;
    static final Map<String, Object> doubleVariants;
    static final Map<String, Object> intVariants;
    static final Map<String, Object> objectVariants;

    static {
        booleanVariant = new HashMap<>();
        booleanVariant.put("on", true);
        booleanVariant.put("off", false);

        shorthandVariant = new HashMap<>();
        shorthandVariant.put("true", true);
        shorthandVariant.put("false", false);

        stringVariants = new HashMap<>();
        stringVariants.put("loop", "loopAlg");
        stringVariants.put("binet", "binetAlg");

        doubleVariants = new HashMap<>();
        doubleVariants.put("one", 3.141d);
        doubleVariants.put("two", 3.14159265359d);

        intVariants = new HashMap<>();
        intVariants.put("one", 1);
        intVariants.put("two", 2);

        Map<String, Object> typeA = new HashMap<>();
        typeA.put("key", "0165");
        typeA.put("date", "01.01.2000");

        Map<String, Object> typeB = new HashMap<>();
        typeB.put("key", "0166");
        typeB.put("date", "01.01.2010");

        objectVariants = new HashMap<>();
        objectVariants.put("typeA", typeA);
        objectVariants.put("typeB", typeB);
    }

    // correct flag - boolean
    static final FeatureFlag BOOLEAN_FLAG = new FeatureFlag("ENABLED", "on", booleanVariant, null);

    // correct flag - boolean
    static final FeatureFlag SHORTHAND_FLAG =
            new FeatureFlag("ENABLED", "false", booleanVariant, null);

    // correct flag - double
    static final FeatureFlag DOUBLE_FLAG = new FeatureFlag("ENABLED", "one", doubleVariants, null);

    // correct flag - int
    static final FeatureFlag INT_FLAG = new FeatureFlag("ENABLED", "one", intVariants, null);

    // correct flag - object
    static final FeatureFlag OBJECT_FLAG = new FeatureFlag("ENABLED", "typeA", objectVariants, null);

    // flag in disabled state
    static final FeatureFlag DISABLED_FLAG = new FeatureFlag("DISABLED", "on", booleanVariant, null);

    // incorrect flag - variant mismatch
    static final FeatureFlag VARIANT_MISMATCH_FLAG =
            new FeatureFlag("ENABLED", "true", stringVariants, null);

    // flag with targeting rule - string
    static final FeatureFlag FLAG_WIH_IF_IN_TARGET = new FeatureFlag(
            "ENABLED",
            "loop",
            stringVariants,
            "{\"if\":[{\"in\":[\"@faas.com\",{\"var\":[\"email\"]}]},\"binet\",null]}",
            new HashMap<>());

    static final FeatureFlag FLAG_WITH_TARGETING_KEY = new FeatureFlag(
            "ENABLED",
            "loop",
            stringVariants,
            "{\"if\":[{\"==\":[{\"var\":\"targetingKey\"},\"xyz\"]},\"binet\",null]}",
            new HashMap<>());

    // flag with incorrect targeting rule
    static final FeatureFlag FLAG_WIH_INVALID_TARGET =
            new FeatureFlag("ENABLED", "loop", stringVariants, "{if this, then that}");

    // flag with shorthand rule
    static final FeatureFlag FLAG_WIH_SHORTHAND_TARGETING =
            new FeatureFlag("ENABLED", "false", shorthandVariant, "{ \"if\": [true, true, false] }");
}
