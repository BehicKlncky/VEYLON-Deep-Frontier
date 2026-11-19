package com.veylon.testsupport;

import org.junit.jupiter.api.extension.AnnotatedElementContext;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.io.TempDirFactory;

import java.nio.file.Path;
import java.util.Locale;

/**
 * The suite's default {@code @TempDir} factory, named by
 * {@code junit.jupiter.tempdir.factory.default} in {@code junit-platform.properties}.
 *
 * <p>It creates each directory exactly where and as JUnit's standard factory does, then hands
 * every directory that JUnit will always clean up to {@link TempDirCleanup}, which deletes it
 * first with bounded retries (0.9.1 defect B02). A directory whose cleanup mode is
 * {@code NEVER} or {@code ON_SUCCESS} is left to JUnit alone, so those modes keep their meaning.
 */
public final class TrackedTempDirFactory implements TempDirFactory {

    @Override
    public Path createTempDirectory(AnnotatedElementContext elementContext, ExtensionContext extensionContext)
            throws Exception {
        Path directory = TempDirFactory.Standard.INSTANCE.createTempDirectory(elementContext, extensionContext);
        if (cleanupMode(elementContext, extensionContext) == CleanupMode.ALWAYS) {
            TempDirCleanup.track(extensionContext, directory);
        }
        return directory;
    }

    private static CleanupMode cleanupMode(AnnotatedElementContext element, ExtensionContext context) {
        CleanupMode declared = element.findAnnotation(TempDir.class)
                .map(TempDir::cleanup)
                .orElse(CleanupMode.DEFAULT);
        if (declared != CleanupMode.DEFAULT) {
            return declared;
        }
        return context.getConfigurationParameter(TempDir.DEFAULT_CLEANUP_MODE_PROPERTY_NAME)
                .map(TrackedTempDirFactory::parse)
                .orElse(CleanupMode.ALWAYS);
    }

    /** As JUnit does, an unknown value means the default mode, {@code ALWAYS}. */
    private static CleanupMode parse(String value) {
        try {
            return CleanupMode.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            return CleanupMode.ALWAYS;
        }
    }
}
