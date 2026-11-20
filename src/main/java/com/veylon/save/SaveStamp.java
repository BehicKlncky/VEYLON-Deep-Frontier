package com.veylon.save;

import java.util.Objects;

/**
 * When a save was written and by which build: the two things its {@link SaveSummary} says that the
 * game itself does not hold. Kept apart so a test can fix them and get the same bytes twice.
 *
 * @param savedAt the wall-clock time of the save, in milliseconds since 1970-01-01T00:00Z
 * @param version the writing build's version, such as "0.9.1", or {@value #DEVELOPMENT}
 */
public record SaveStamp(long savedAt, String version) {

    /** The version a build without a jar manifest writes: a development run from the classes. */
    public static final String DEVELOPMENT = "development";

    public SaveStamp {
        Objects.requireNonNull(version, "version");
    }

    /** This moment and this build. */
    public static SaveStamp now() {
        return new SaveStamp(System.currentTimeMillis(), buildVersion());
    }

    /**
     * The jar manifest's {@code Implementation-Version}; under Gradle's test tasks the
     * {@code veylon.version} property the build sets; otherwise {@value #DEVELOPMENT}.
     */
    public static String buildVersion() {
        Package own = SaveStamp.class.getPackage();
        String version = own == null ? null : own.getImplementationVersion();
        if (version == null || version.isBlank()) {
            version = System.getProperty("veylon.version");
        }
        return version == null || version.isBlank() ? DEVELOPMENT : version.strip();
    }
}
