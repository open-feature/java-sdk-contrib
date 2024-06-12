package dev.openfeature.contrib.providers.envvar;

import java.util.function.Function;
import java.util.function.UnaryOperator;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.CaseUtils;

/**
 * This class provides a way to transform any given key to another value. This is helpful, if keys in the code have a
 * different representation as in the actual environment, e.g. SCREAMING_SNAKE_CASE env vars vs. hyphen-case keys
 * for feature flags.
 *
 * <p>This class also supports chaining/combining different transformers incl. self-written ones by providing
 * a transforming function in the constructor. <br>
 * Currently, the following transformations are supported out of the box:
 * <ul>
 *     <li>{@link #toLowerCaseTransformer() converting to lower case}</li>
 *     <li>{@link #toUpperCaseTransformer() converting to UPPER CASE}</li>
 *     <li>{@link #hyphenCaseToScreamingSnake() converting hyphen-case to SCREAMING_SNAKE_CASE}</li>
 *     <li>{@link #toCamelCaseTransformer() convert to camelCase}</li>
 *     <li>{@link #replaceUnderscoreWithDotTransformer() replace '_' with '.'}</li>
 *     <li>{@link #replaceDotWithUnderscoreTransformer() replace '.' with '_'}</li>
 * </ul>
 *
 * <p><strong>Examples:</strong>
 *
 * <p>1. hyphen-case feature flag names to screaming snake-case environment variables:
 * <pre>
 * {@code
 * // Definition of the EnvVarProvider:
 * EnvironmentKeyTransformer transformer = EnvironmentKeyTransformer
 *     .hyphenCaseToScreamingSnake();
 *
 * FeatureProvider provider = new EnvVarProvider(transformer);
 * }
 * </pre>
 * 2. chained/composed transformations:
 * <pre>
 * {@code
 * // Definition of the EnvVarProvider:
 * EnvironmentKeyTransformer transformer = EnvironmentKeyTransformer
 *     .toLowerCaseTransformer()
 *     .andThen(EnvironmentKeyTransformer.replaceUnderscoreWithDotTransformer());
 *
 * FeatureProvider provider = new EnvVarProvider(transformer);
 * }
 * </pre>
 * 3. freely defined transformation function:
 * <pre>
 * {@code
 *
 * // Definition of the EnvVarProvider:
 * EnvironmentKeyTransformer transformer = new EnvironmentKeyTransformer(key -> "constant");
 *
 * FeatureProvider provider = new EnvVarProvider(keyTransformer);
 * }
 * </pre>
 */
@RequiredArgsConstructor
public class EnvironmentKeyTransformer {

    private static final UnaryOperator<String> TO_LOWER_CASE = StringUtils::lowerCase;
    private static final UnaryOperator<String> TO_UPPER_CASE = StringUtils::upperCase;
    private static final UnaryOperator<String> TO_CAMEL_CASE = s -> CaseUtils.toCamelCase(s, false, '_');
    private static final UnaryOperator<String> REPLACE_DOT_WITH_UNDERSCORE = s -> StringUtils.replaceChars(s, ".", "_");
    private static final UnaryOperator<String> REPLACE_UNDERSCORE_WITH_DOT = s -> StringUtils.replaceChars(s, "_", ".");
    private static final UnaryOperator<String> REPLACE_HYPHEN_WITH_UNDERSCORE =
        s -> StringUtils.replaceChars(s, "-", "_");

    private final Function<String, String> transformation;

    public String transformKey(String key) {
        return transformation.apply(key);
    }

    public EnvironmentKeyTransformer andThen(EnvironmentKeyTransformer another) {
        return new EnvironmentKeyTransformer(transformation.andThen(another::transformKey));
    }

    public static EnvironmentKeyTransformer toLowerCaseTransformer() {
        return new EnvironmentKeyTransformer(TO_LOWER_CASE);
    }

    public static EnvironmentKeyTransformer toUpperCaseTransformer() {
        return new EnvironmentKeyTransformer(TO_UPPER_CASE);
    }

    public static EnvironmentKeyTransformer toCamelCaseTransformer() {
        return new EnvironmentKeyTransformer(TO_CAMEL_CASE);
    }

    public static EnvironmentKeyTransformer replaceUnderscoreWithDotTransformer() {
        return new EnvironmentKeyTransformer(REPLACE_UNDERSCORE_WITH_DOT);
    }

    public static EnvironmentKeyTransformer replaceDotWithUnderscoreTransformer() {
        return new EnvironmentKeyTransformer(REPLACE_DOT_WITH_UNDERSCORE);
    }

    public static EnvironmentKeyTransformer hyphenCaseToScreamingSnake() {
        return new EnvironmentKeyTransformer(REPLACE_HYPHEN_WITH_UNDERSCORE)
            .andThen(EnvironmentKeyTransformer.toUpperCaseTransformer());
    }

    public static EnvironmentKeyTransformer doNothing() {
        return new EnvironmentKeyTransformer(s -> s);
    }
}
