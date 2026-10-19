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
 * Asserts the packaged canonical assets match the pinned spec revision, by digest. To move the pin,
 * update the {@code spec} gitlink, {@link #PINNED_REVISION} and {@link #PINNED_DIGEST} in one commit.
 */
class CanonicalAssetDigestTest {

    /** The open-feature/spec commit the packaged assets come from; must match the submodule HEAD. */
    static final String PINNED_REVISION = "10b1a610ed0afe6bf0e38b441d7362f5a76cb47d";

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
                        "Packaged canonical assets do not match open-feature/spec %s.%n"
                                + "  expected %s%n"
                                + "  actual   %s%n"
                                + "Run `git submodule update --init tools/tck/spec` (HEAD must be %s) and rebuild "
                                + "with `clean`. If moving the pin, update PINNED_REVISION and PINNED_DIGEST.",
                        PINNED_REVISION, PINNED_DIGEST, actual, PINNED_REVISION)
                .isEqualTo(PINNED_DIGEST);
    }

    /** SHA-256 over each asset's relative path and CR-stripped content, sorted by path, zero-separated. */
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

    /** The artifact's code source root; avoids the classloader so shadowing copies are ignored. */
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
