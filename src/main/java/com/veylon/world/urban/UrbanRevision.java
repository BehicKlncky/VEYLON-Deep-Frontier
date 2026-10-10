package com.veylon.world.urban;

import com.veylon.world.World;

/**
 * Which revision of urban layout and urban content a world was created under,
 * and which ones this build can still honour (urban-survival decision D-04).
 *
 * <h2>Two kinds of revision</h2>
 *
 * <p>The <b>layout revision</b> covers everything a save re-derives instead of
 * storing: blocks, plans and the stable ids in {@link UrbanIds}. It is fixed by
 * the generator version, one urban generator per layout revision, so it is
 * known the moment a save's header is read and before bootstrap generates a
 * single chunk. A change that would alter an existing world's blocks, plans or
 * ids — a reshaped template, a new rule, catalogue growth that changes what an
 * existing parcel stamps — is never made under an existing generator. It adds
 * a new generator version and layout revision, and the old one either keeps
 * producing its old output (pinned by {@code WorldGeneratorFingerprintTest}) or
 * is retired.
 *
 * <p>The <b>content revision</b> covers what is rolled rather than stamped:
 * loot, encounter and trade tables. It never shapes geometry. A new world
 * records the current one, saves keep it in the {@code urban.meta} section, and
 * tables are append-only within a revision; changing an existing entry means a
 * new content revision.
 *
 * <h2>Retirement</h2>
 *
 * <p>A revision is retired only by explicit load-time rejection with a clear
 * message and a recorded decision, never by silently regenerating or resetting
 * the world. A retired layout leaves the supported generator set
 * ({@link World#isSupportedGenerator}); a retired content revision raises
 * {@link #MIN_SUPPORTED_CONTENT}.
 */
public final class UrbanRevision {

    /** Layout revision of {@link World#GEN_URBAN}: the street datum and nothing else. */
    public static final int LAYOUT_DATUM_PLACEHOLDER = 1;
    /** Content revision every new urban world records. */
    public static final int CURRENT_CONTENT = 1;
    /** Oldest content revision this build still loads. */
    public static final int MIN_SUPPORTED_CONTENT = 1;
    /**
     * Content revision of an urban save that carries no {@code urban.meta}
     * section. Every build that can create an urban world writes the section, so
     * this is a defined default for damaged or hand-made files, not a migration.
     */
    public static final int ABSENT_CONTENT = 1;

    /** The urban generator the development selector creates: the newest layout. */
    public static final int DEVELOPMENT_GENERATOR = World.GEN_URBAN;

    private UrbanRevision() {
    }

    /**
     * The layout revision an urban generator version builds.
     *
     * @throws IllegalArgumentException for a generator that is not urban
     */
    public static int layoutRevisionOf(int generatorVersion) {
        return switch (generatorVersion) {
            case World.GEN_URBAN -> LAYOUT_DATUM_PLACEHOLDER;
            default -> throw new IllegalArgumentException(
                    "generator " + generatorVersion + " is not an urban generator");
        };
    }

    /**
     * Rejects a content revision this build cannot honour.
     *
     * @throws IllegalArgumentException naming the revision and the supported range
     */
    public static void requireSupportedContent(int contentRevision) {
        if (contentRevision > CURRENT_CONTENT) {
            throw new IllegalArgumentException("urban content revision " + contentRevision
                    + " is newer than this build supports (" + MIN_SUPPORTED_CONTENT + ".."
                    + CURRENT_CONTENT + ")");
        }
        if (contentRevision < MIN_SUPPORTED_CONTENT) {
            throw new IllegalArgumentException("urban content revision " + contentRevision
                    + " is not supported by this build (" + MIN_SUPPORTED_CONTENT + ".."
                    + CURRENT_CONTENT + ")");
        }
    }
}
