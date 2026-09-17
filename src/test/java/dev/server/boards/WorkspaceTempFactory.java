package dev.server.boards;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.extension.AnnotatedElementContext;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.io.TempDirFactory;

/** Keeps JUnit's automatically cleaned temporary directories inside the module. */
public final class WorkspaceTempFactory implements TempDirFactory {
    @Override
    public Path createTempDirectory(AnnotatedElementContext element,ExtensionContext context) throws Exception {
        Path module=Path.of(System.getProperty("basedir",System.getProperty("user.dir"))).toAbsolutePath().normalize();
        Path parent=module.resolve("target/test-temp");
        Files.createDirectories(parent);
        // JUnit retains ownership of cleanup; only the allocation location changes.
        return Files.createTempDirectory(parent,"junit-");
    }
}
