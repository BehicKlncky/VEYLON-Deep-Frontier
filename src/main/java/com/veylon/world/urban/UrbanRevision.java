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
    /**
     * Layout revision of {@link World#GEN_URBAN_STREETS}: the street network,
     * districts and reserved parcels of {@link CityPlanner}, with no buildings.
     */
    public static final int LAYOUT_STREET_NETWORK = 2;
    /** Frozen building template revision one, without building stamping (milestone 05). */
    public static final int LAYOUT_BUILDING_PLANS = 3;
    /** Frozen catalogue used by generator 6; later catalogues append another constant. */
    public static final int BUILDING_TEMPLATE_WALKUPS = 1;
    /** Generated, furnished apartments and shops (milestone 06). */
    public static final int LAYOUT_GENERATED_APARTMENTS = 4;
    /** Frozen furnishings and abandonment overlays, preserving catalogue one. */
    public static final int BUILDING_TEMPLATE_FURNISHED = 2;
    /** Milestone 06 native-QA refinement; earlier generated stairs remain frozen. */
    public static final int LAYOUT_SHAPED_STAIRS = 5;
    public static final int BUILDING_TEMPLATE_SHAPED_STAIRS = 3;
    public static final int LAYOUT_STARTER = 6;
    public static final int BUILDING_TEMPLATE_STARTER = 4;
    public static final int LAYOUT_ENCOUNTERS = 7;
    /**
     * Commercial and civic catalogue (milestone 12): template four's walk-ups and starter
     * superblock unchanged, plus six specialist families on the same skeleton.
     */
    public static final int BUILDING_TEMPLATE_CIVIC = 5;
    /** Layout of {@link World#GEN_URBAN_CIVIC}: layout seven plus template five. */
    public static final int LAYOUT_CIVIC = 8;
    /**
     * Layout of {@link World#GEN_URBAN_DISTRICTS}, six districts (milestone 13):
     * {@link DistrictMap} identities, district superblocks and uses, and the
     * {@link UrbanOverlay} raster, on the unchanged layout-two street lattice.
     */
    public static final int LAYOUT_DISTRICTS = 9;
    /**
     * The district catalogue (milestone 13): which family and variant each parcel of a
     * six-district plan carries. Its plans are template-four walk-ups and template-five
     * commercial and civic buildings, so it selects proven geometry rather than adding any.
     */
    public static final int BUILDING_TEMPLATE_DISTRICTS = 6;

    /** Content revision every new urban world records. */
    public static final int CURRENT_CONTENT = 3;
    /** Oldest content revision this build still loads. */
    public static final int MIN_SUPPORTED_CONTENT = 1;
    /**
     * Content revision of an urban save that carries no {@code urban.meta}
     * section. Every build that can create an urban world writes the section, so
     * this is a defined default for damaged or hand-made files, not a migration.
     */
    public static final int ABSENT_CONTENT = 1;

    /** The urban generator the development selector creates: the newest layout. */
    public static final int DEVELOPMENT_GENERATOR = World.GEN_URBAN_DISTRICTS;

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
            case World.GEN_URBAN_STREETS -> LAYOUT_STREET_NETWORK;
            case World.GEN_URBAN_PLANS -> LAYOUT_BUILDING_PLANS;
            case World.GEN_URBAN_APARTMENTS -> LAYOUT_GENERATED_APARTMENTS;
            case World.GEN_URBAN_SHAPED_STAIRS -> LAYOUT_SHAPED_STAIRS;
            case World.GEN_URBAN_STARTER -> LAYOUT_STARTER;
            case World.GEN_URBAN_ENCOUNTERS -> LAYOUT_ENCOUNTERS;
            case World.GEN_URBAN_CIVIC -> LAYOUT_CIVIC;
            case World.GEN_URBAN_DISTRICTS -> LAYOUT_DISTRICTS;
            default -> throw new IllegalArgumentException(
                    "generator " + generatorVersion + " is not an urban generator");
        };
    }

    /** Resolved from the saved generator before bootstrap; zero means no building catalogue. */
    public static int buildingTemplateRevisionOf(int generatorVersion) {
        return switch (generatorVersion) {
            case World.GEN_URBAN, World.GEN_URBAN_STREETS -> 0;
            case World.GEN_URBAN_PLANS -> BUILDING_TEMPLATE_WALKUPS;
            case World.GEN_URBAN_APARTMENTS -> BUILDING_TEMPLATE_FURNISHED;
            case World.GEN_URBAN_SHAPED_STAIRS -> BUILDING_TEMPLATE_SHAPED_STAIRS;
            case World.GEN_URBAN_STARTER, World.GEN_URBAN_ENCOUNTERS -> BUILDING_TEMPLATE_STARTER;
            case World.GEN_URBAN_CIVIC -> BUILDING_TEMPLATE_CIVIC;
            case World.GEN_URBAN_DISTRICTS -> BUILDING_TEMPLATE_DISTRICTS;
            default -> throw new IllegalArgumentException("not an urban generator " + generatorVersion);
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
