package com.veylon.world.urban;

/**
 * Every salt an urban plan derivation uses, declared in one place (decision
 * D-38).
 *
 * <p>A plan value is a pure function of the world seed, the layout revision, a
 * stable coordinate or id and one of these salts ({@link UrbanHash}). Two
 * decisions that shared a salt would be correlated — every boulevard would
 * also be a commercial district, say — so each decision gets its own, and
 * {@code UrbanSaltsTest} checks reflectively that no two are equal.
 *
 * <p>The values are part of the frozen layout of every urban generator that
 * uses them: changing one rewrites saved urban worlds. A new decision appends
 * a new salt; an existing one is never edited.
 */
public final class UrbanSalts {
    /** Catalogue-one infected site selection; independent from furnishings and loot. */
    public static final long ENCOUNTERS = 0x5645_494c_504f_5055L;

    /** District identity of a planning region. */
    public static final long DISTRICT = 0x5644_4953_5452_4943L;
    /** Offset of a north-south local street line from its nominal position. */
    public static final long LINE_JITTER_X = 0x564c_494e_455f_5821L;
    /** Offset of an east-west local street line from its nominal position. */
    public static final long LINE_JITTER_Z = 0x564c_494e_455f_5a21L;
    /** Cross-section of one avenue segment (plain or boulevard). */
    public static final long AVENUE_PROFILE = 0x5641_5645_4e55_4521L;
    /** Which superblock of a region becomes its park or plaza. */
    public static final long OPEN_SPACE = 0x564f_5045_4e5f_5350L;
    /** Whether a region gets a park or plaza at all. */
    public static final long OPEN_SPACE_CHANCE = 0x564f_5045_4e5f_4348L;
    /** Which superblock of a residential region becomes a shop row. */
    public static final long SHOP_ROW = 0x5653_484f_505f_524fL;
    /** Which superblocks of a commercial region keep courtyard housing. */
    public static final long MIXED_HOUSING = 0x564d_4958_4544_5f48L;
    /** Strip depths and side widths of a courtyard superblock. */
    public static final long RING_DEPTHS = 0x5652_494e_475f_4450L;
    /** Where a strip of parcels is split around its courtyard passage. */
    public static final long STRIP_SPLIT = 0x5653_5452_4950_5f53L;
    /** Position of a commercial superblock's service alley. */
    public static final long ALLEY_OFFSET = 0x5641_4c4c_4559_5f4fL;
    /** Widths of the parcels along a commercial superblock half. */
    public static final long PARCEL_WIDTHS = 0x5650_4152_4345_4c57L;
    /** Whether a courtyard corner parcel is reserved as a corner shop. */
    public static final long CORNER_SHOP = 0x5643_4f52_4e45_5253L;
    /** Storey envelope of a parcel. */
    public static final long STOREYS = 0x5653_544f_5245_5953L;
    /** Which parcel of a region is its service reservation. */
    public static final long SERVICE_PARCEL = 0x5653_4552_5649_4345L;
    /** Whether a candidate tree position in planned greenery is planted. */
    public static final long TREE = 0x5654_5245_455f_5f50L;
    /** Trunk height of a planted tree. */
    public static final long TREE_HEIGHT = 0x5654_5245_455f_4854L;
    /** Revision-one abandonment/occupation variant of a building. */
    public static final long BUILDING_VARIANT = 0x5642_5549_4c44_5641L;
    /** Whether a revision-one walk-up has a first basement. */
    public static final long BUILDING_BASEMENT = 0x5642_5549_4c44_4241L;
    /** Independent content-revision loot decisions, never layout or simulation RNG draws. */
    public static final long LOOT_PRESENCE = 0x564c_4f4f_545f_5052L;
    public static final long LOOT_COUNT = 0x564c_4f4f_545f_434eL;
    public static final long LOOT_CONDITION = 0x564c_4f4f_545f_4344L;
    /** Template revision five (milestone 12): which family a commercial parcel carries. */
    public static final long CIVIC_FAMILY = 0x5643_4956_4641_4d49L;
    /** Which residential parcel of a region hosts its school or security post. */
    public static final long CIVIC_SITE = 0x5643_4956_5349_5445L;
    /** Abandonment, damage or partial occupation of a commercial or civic building. */
    public static final long CIVIC_VARIANT = 0x5643_4956_5641_5249L;
    /** Whether a school or office block has a first basement. */
    public static final long CIVIC_BASEMENT = 0x5643_4956_4241_5345L;
    /** The name painted on a frontage sign; presentation only. */
    public static final long CIVIC_NAME = 0x5643_4956_4e41_4d45L;
    /** Encounter catalogue two: which unguarded specialist buildings hold a resident threat. */
    public static final long CIVIC_ENCOUNTERS = 0x5643_4956_454e_434fL;

    // Layout revision 9 (milestone 13): six districts. Street lattice and subdivision keep the
    // layout-two inputs above; only district decisions use these.
    /** The open draw of a region's district identity. */
    public static final long DISTRICT_MAP = 0x5644_4d41_505f_4c39L;
    /** Which regions near the spawn carry the guaranteed commercial and specialist districts. */
    public static final long DISTRICT_ANCHORS = 0x5644_414e_4348_4f52L;
    /** Superblock kinds of the industrial, flooded, civic and quarantine districts. */
    public static final long DISTRICT_KINDS = 0x5644_4b49_4e44_5321L;
    /** Parcel uses of those districts. */
    public static final long DISTRICT_USES = 0x5644_5553_4553_5f21L;
    /** Storey envelopes of those districts. */
    public static final long DISTRICT_STOREYS = 0x5644_5354_4f52_4559L;
    /** Patches of contaminated ground and overgrowth (two-by-two cells). */
    public static final long OVERLAY_PATCH = 0x564f_5650_4154_4348L;
    /** Which lawn cells grow tall grass or a bush. */
    public static final long OVERLAY_PLANT = 0x564f_5650_4c41_4e54L;
    /** Where powder kegs were left in an industrial yard. */
    public static final long OVERLAY_KEG = 0x564f_564b_4547_5321L;

    private UrbanSalts() {
    }
}
