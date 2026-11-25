package dev.openfeature.contrib.tools.tck;

import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URISyntaxException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Verifies glue classes are public; Cucumber silently skips steps it cannot see. */
class GlueVisibilityTest {

    private static final List<Class<? extends Annotation>> STEP_ANNOTATIONS =
            List.of(Given.class, When.class, Then.class);

    @Test
    @DisplayName("every canonical step class is public, or Cucumber silently finds no steps")
    void stepClassesArePublic() throws Exception {
        List<Class<?>> glue = canonicalGlue();

        assertThat(glue).as("the canonical glue package holds step classes").isNotEmpty();

        assertThat(glue)
                .allSatisfy(type -> assertThat(Modifier.isPublic(type.getModifiers()))
                        .as("%s must be public, or its scenarios report undefined", type.getSimpleName())
                        .isTrue());
    }

    @Test
    @DisplayName("every annotated step method is public, or Cucumber silently finds no steps")
    void stepMethodsArePublic() throws Exception {
        List<Method> steps = new ArrayList<>();
        for (Class<?> type : canonicalGlue()) {
            for (Method method : type.getDeclaredMethods()) {
                if (STEP_ANNOTATIONS.stream().anyMatch(method::isAnnotationPresent)) {
                    steps.add(method);
                }
            }
        }

        assertThat(steps).as("the canonical glue package defines steps").isNotEmpty();

        assertThat(steps)
                .allSatisfy(method -> assertThat(Modifier.isPublic(method.getModifiers()))
                        .as(
                                "%s.%s must be public: getMethods() sees nothing else",
                                method.getDeclaringClass().getSimpleName(), method.getName())
                        .isTrue());
    }

    /**
     * Loads the step classes in {@link ProviderTck#GLUE} from the code source.
     *
     * @return the classes in the canonical glue package that define steps
     */
    private static List<Class<?>> canonicalGlue() throws IOException, URISyntaxException, ClassNotFoundException {
        CodeSource codeSource = ProviderTck.class.getProtectionDomain().getCodeSource();
        assertThat(codeSource)
                .as("the suite has a code source to read the glue package from")
                .isNotNull();

        Path root = Paths.get(codeSource.getLocation().toURI());
        Path pkg = root.resolve(ProviderTck.GLUE.replace('.', '/'));
        assertThat(Files.isDirectory(pkg))
                .as("the glue package %s exists at %s", ProviderTck.GLUE, pkg)
                .isTrue();

        List<Class<?>> glue = new ArrayList<>();
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(pkg, "*.class")) {
            for (Path entry : entries) {
                String file = entry.getFileName().toString();
                if (file.contains("$")) {
                    continue;
                }
                String name = ProviderTck.GLUE + "." + file.substring(0, file.length() - ".class".length());
                Class<?> type = Class.forName(name);
                boolean definesSteps = false;
                for (Method method : type.getDeclaredMethods()) {
                    if (STEP_ANNOTATIONS.stream().anyMatch(method::isAnnotationPresent)) {
                        definesSteps = true;
                        break;
                    }
                }
                if (definesSteps) {
                    glue.add(type);
                }
            }
        }
        return glue;
    }
}
