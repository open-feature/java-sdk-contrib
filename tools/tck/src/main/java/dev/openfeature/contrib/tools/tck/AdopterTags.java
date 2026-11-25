package dev.openfeature.contrib.tools.tck;

import java.lang.annotation.Annotation;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import org.opentest4j.TestAbortedException;

/**
 * The gate for {@link TckExcludeTags} and {@link TckIncludeTags} on a harness class.
 *
 * <p>Only adopter tags can be named: a {@link Capability} tag is refused, so this cannot skip
 * what {@link ProviderTckHarness#capabilities()} gates. Canonical scenarios carry only capability
 * tags, and are exempt besides.
 */
public final class AdopterTags {

    private AdopterTags() {
        // static gate; harnesses cannot override it
    }

    /**
     * Rejects a capability tag, a malformed tag, or a tag both excluded and included.
     *
     * @param harness the harness class
     * @throws IllegalArgumentException if the annotations name a capability tag or a malformed
     *     tag, or overlap
     */
    public static void requireValid(Class<?> harness) {
        Set<String> excluded = excluded(harness);
        Set<String> included = included(harness);
        requireAdopterTags("@TckExcludeTags", excluded);
        requireAdopterTags("@TckIncludeTags", included);
        List<String> both = new ArrayList<>(excluded);
        both.retainAll(included);
        if (!both.isEmpty()) {
            throw new IllegalArgumentException(
                    harness.getName() + " both excludes and includes " + both + "; a tag is one or the other.");
        }
    }

    /**
     * Skips an extension scenario that carries an excluded tag, or none of the included tags when
     * there are any. Canonical scenarios are never skipped here. Validates the annotations first,
     * so no path to the gate can skip on a capability tag.
     *
     * @param source the scenario's feature file, as Cucumber reports it, or {@code null} if unknown
     * @param tags the scenario's Gherkin tags, including the leading at-sign
     * @param harness the harness class
     * @throws IllegalArgumentException if the annotations are invalid; see {@link #requireValid}
     * @throws TestAbortedException if the scenario is not selected
     */
    public static void requireSelected(URI source, Collection<String> tags, Class<?> harness) {
        requireValid(harness);
        // before any skip, so an exclude cannot mask a reserved tag on the scenario
        Capability.requireValidVocabulary(source, tags);
        if (Capability.isCanonical(source)) {
            return;
        }
        Set<String> excluded = excluded(harness);
        List<String> carried = new ArrayList<>();
        for (String tag : tags) {
            if (excluded.contains(tag)) {
                carried.add(tag);
            }
        }
        if (!carried.isEmpty()) {
            throw new TestAbortedException(
                    "Skipped: scenario carries " + carried + ", excluded by " + harness.getSimpleName() + ".");
        }
        Set<String> included = included(harness);
        if (!included.isEmpty() && Collections.disjoint(tags, included)) {
            throw new TestAbortedException(
                    "Skipped: scenario carries none of " + included + ", included by " + harness.getSimpleName() + ".");
        }
    }

    static Set<String> excluded(Class<?> harness) {
        return collect(harness, TckExcludeTags.class, TckExcludeTags::value);
    }

    static Set<String> included(Class<?> harness) {
        return collect(harness, TckIncludeTags.class, TckIncludeTags::value);
    }

    // declarations on the class and its superclasses add up
    private static <A extends Annotation> Set<String> collect(
            Class<?> harness, Class<A> annotation, Function<A, String[]> value) {
        Set<String> tags = new LinkedHashSet<>();
        for (Class<?> type = harness; type != null; type = type.getSuperclass()) {
            A declared = type.getDeclaredAnnotation(annotation);
            if (declared != null) {
                tags.addAll(Arrays.asList(value.apply(declared)));
            }
        }
        return tags;
    }

    private static void requireAdopterTags(String source, Collection<String> tags) {
        List<String> capabilityTags = new ArrayList<>();
        for (String tag : tags) {
            if (tag == null
                    || tag.length() < 2
                    || !tag.startsWith("@")
                    || !tag.trim().equals(tag)) {
                throw new IllegalArgumentException(
                        source + " names '" + tag + "'; a tag starts with '@' and has no surrounding whitespace.");
            }
            if (Capability.fromTag(tag).isPresent()) {
                capabilityTags.add(tag);
            }
        }
        if (!capabilityTags.isEmpty()) {
            throw new IllegalArgumentException(source + " names TCK capability tags " + capabilityTags
                    + "; gate them through capabilities() instead.");
        }
    }
}
