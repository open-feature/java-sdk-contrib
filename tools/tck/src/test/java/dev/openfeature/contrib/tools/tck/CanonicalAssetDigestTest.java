package dev.openfeature.contrib.tools.tck;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.CodeSource;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The canonical assets in this build are the ones the pinned specification revision defines.
 *
 * <p>They are copied out of the {@code spec} submodule at {@code generate-resources}, and the
 * submodule's <em>gitlink</em> and its <em>working tree</em> are moved by different commands: a
 * rebase, a branch switch or a checkout moves the first, and only {@code git submodule update}
 * moves the second. Build in between and the copy step packages the previous pin's assets under
 * the new pin's name. Nothing about the result looks wrong — the old feature files agree with each
 * other and with the old flag set, so the suite runs, passes, and reports numbers that describe a
 * revision nobody asked for. That is not hypothetical; one language's suite did exactly this for a
 * whole adoption run and the only trace was that its totals matched the previous pass exactly.
 *
 * <p>{@link CanonicalTagCoverageTest} catches one symptom of it — a declarable capability whose
 * scenarios have not arrived — and catches it with a better message than this test could give. It
 * only fires for that symptom, though. A pin that changes the wording of a scenario, the value of a
 * canonical flag, or a field of the control API moves nothing it looks at, and those are the
 * changes most pins actually make: the re-pin this test was written for changed two
 * {@code $comment} blocks in {@code canonical-flags.json} and not one scenario.
 *
 * <p>So this asserts the assets themselves, by digest. It is deliberately blunt: it cannot say
 * <em>what</em> differs, only that what was packaged is not what the pin names. The build step it
 * backs up is in {@code pom.xml}, and the division between them is the point — the build moves the
 * checkout and empties the copy's target directory, and this fails the build if the assets are
 * nevertheless wrong. Every route to stale assets ends here, including the one where the checkout
 * was skipped by hand.
 *
 * <h2>Updating the pin</h2>
 *
 * <p>Three things move together, in one commit:
 *
 * <ol>
 *   <li>the {@code spec} submodule gitlink — {@code git -C tools/tck/spec checkout <rev>};
 *   <li>{@link #PINNED_REVISION} below;
 *   <li>{@link #PINNED_DIGEST} below, which this test prints when it fails.
 * </ol>
 *
 * <p>On the report branch a fourth follows: {@code tck.spec.revision} in the POM, which is what a
 * conformance report names as the source of its scenarios. That one is checked against
 * {@link #PINNED_REVISION} where it is read back, so it cannot be forgotten.
 *
 * <h2>What the digest is over</h2>
 *
 * <p>All three asset trees, not just the Gherkin. The feature files are meaningless without the
 * flag set they evaluate and the control API that produces their outages, and it is the flag set
 * that a Gherkin-only digest would have missed here.
 *
 * <p>Line endings are normalised out of it. The assets are checked out through git, so a Windows
 * clone with {@code core.autocrlf=true} holds bytes a Linux one does not, and a digest that
 * disagreed with itself across platforms would be turned off within a week. Nothing else is
 * normalised: trailing whitespace, ordering and encoding are all part of what is pinned.
 */
class CanonicalAssetDigestTest {

    /**
     * The open-feature/spec commit the packaged assets come from.
     *
     * <p>Must equal {@code git -C tools/tck/spec rev-parse HEAD}, and is the value the report
     * branch publishes as the source of a run's scenarios.
     */
    static final String PINNED_REVISION = "ff68adb4c7617ad2d980988241e92603bc247926";

    /** SHA-256 of the three asset trees at {@link #PINNED_REVISION}, as {@link #digest} computes it. */
    static final String PINNED_DIGEST = "7a753b5f5f60248336d93f565bc12ad844af9f04de40867916b98f283363354b";

    /** The generated resource directories, in the order they are digested. */
    private static final List<String> ASSET_DIRECTORIES = Arrays.asList("flags", "gherkin", "openapi");

    @Test
    @DisplayName("the packaged canonical assets are the pinned revision's, byte for byte")
    void thePackagedAssetsAreThePinnedRevisions() {
        String actual = digest();
        assertThat(actual)
                .as(
                        "The canonical assets packaged in this build are not the ones open-feature/spec %s "
                                + "defines.%n%n"
                                + "  expected %s%n"
                                + "  actual   %s%n%n"
                                + "The usual cause is a spec submodule whose working tree has not caught up with "
                                + "its gitlink: a rebase or a checkout moves the gitlink, and only `git submodule "
                                + "update` moves the checkout. Run, from the repository root:%n%n"
                                + "  git submodule update --init tools/tck/spec%n"
                                + "  git -C tools/tck/spec rev-parse HEAD    # must print %s%n%n"
                                + "then rebuild with `clean`, because the copies under src/main/resources are "
                                + "generated and gitignored.%n%n"
                                + "If you meant to move the pin, update PINNED_REVISION and PINNED_DIGEST in this "
                                + "class in the same commit as the gitlink, taking the digest from the `actual` "
                                + "line above.",
                        PINNED_REVISION, PINNED_DIGEST, actual, PINNED_REVISION)
                .isEqualTo(PINNED_DIGEST);
    }

    /**
     * Digests the packaged assets.
     *
     * <p>Every regular file under {@code flags/}, {@code gherkin/} and {@code openapi/}, ordered by
     * its path relative to the code source root with {@code /} separators. Each contributes its path
     * and then its content, both terminated by a zero byte so that no rename can be absorbed into a
     * neighbouring file's bytes. Carriage returns are dropped from the content; see the class
     * comment.
     */
    private static String digest() {
        MessageDigest sha256;
        try {
            sha256 = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("This JVM does not provide SHA-256", e);
        }
        Path root = codeSourceRoot();
        for (Path file : assetFiles(root)) {
            sha256.update(relativePath(root, file).getBytes(StandardCharsets.UTF_8));
            sha256.update((byte) 0);
            sha256.update(withoutCarriageReturns(read(file)));
            sha256.update((byte) 0);
        }
        StringBuilder hex = new StringBuilder();
        for (byte b : sha256.digest()) {
            hex.append(String.format("%02x", b));
        }
        return hex.toString();
    }

    private static List<Path> assetFiles(Path root) {
        List<Path> files = new ArrayList<>();
        for (String directory : ASSET_DIRECTORIES) {
            Path assets = root.resolve(directory);
            if (!Files.isDirectory(assets)) {
                throw new IllegalStateException("No " + directory + "/ directory in the tck code source " + root
                        + ". The canonical assets were not copied from the spec submodule; run the build "
                        + "through Maven rather than compiling the sources alone.");
            }
            try (Stream<Path> walk = Files.walk(assets)) {
                files.addAll(walk.filter(Files::isRegularFile).collect(Collectors.toList()));
            } catch (IOException e) {
                throw new UncheckedIOException("Could not list the canonical assets under " + assets, e);
            }
        }
        files.sort(Comparator.comparing(file -> relativePath(root, file)));
        return files;
    }

    private static String relativePath(Path root, Path file) {
        return root.relativize(file).toString().replace('\\', '/');
    }

    private static byte[] read(Path file) {
        try {
            return Files.readAllBytes(file);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read the canonical asset " + file, e);
        }
    }

    private static byte[] withoutCarriageReturns(byte[] content) {
        byte[] stripped = new byte[content.length];
        int length = 0;
        for (byte b : content) {
            if (b != '\r') {
                stripped[length++] = b;
            }
        }
        byte[] exact = new byte[length];
        System.arraycopy(stripped, 0, exact, 0, length);
        return exact;
    }

    /**
     * The directory this artifact's classes and resources were loaded from.
     *
     * <p>Read through the code source rather than the classloader, as {@link CanonicalTagCoverageTest}
     * reads it, and for the same reason: an asset placed at the same classpath path on another root
     * shadows the packaged one, and a check that digested the shadowing copy would be comparing the
     * replacement against itself.
     */
    private static Path codeSourceRoot() {
        CodeSource codeSource = ProviderTck.class.getProtectionDomain().getCodeSource();
        if (codeSource == null || codeSource.getLocation() == null) {
            throw new IllegalStateException(
                    "The tck code source is not visible to this JVM, so the packaged canonical assets "
                            + "cannot be read.");
        }
        try {
            return Paths.get(codeSource.getLocation().toURI());
        } catch (URISyntaxException | IllegalArgumentException e) {
            throw new IllegalStateException("The tck code source is not a file: " + codeSource.getLocation(), e);
        }
    }
}
