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
    /** Template revision six: which family a commercial lot carries in its district. */
    public static final long DISTRICT_FAMILY = 0x5644_4641_4d49_4c59L;
    /** Which residential lots of a district become its schools, posts, clinics and offices. */
    public static final long DISTRICT_SITE = 0x5644_5349_5445_5321L;
    /** A district's abandonment variant for its specialist buildings and walk-ups. */
    public static final long DISTRICT_VARIANT = 0x5644_5641_5249_414eL;
    /** Encounter catalogue three: a region's threat budget and which buildings spend it. */
    public static final long DISTRICT_THREATS = 0x5644_5448_5245_4154L;
    /** Milestone 14: whether a candidate avenue carries a transit tunnel. */
    public static final long TRANSIT_SEGMENT = 0x5654_5345_474d_4e54L;
    /** Whether a tunnel end has fallen in. */
    public static final long TRANSIT_PLUG = 0x5654_504c_5547_5321L;
    /** Low rubble mounds inside a tunnel. */
    public static final long TRANSIT_MOUND = 0x5654_4d4f_554e_4421L;
    /** Where along its avenue a station is sited, and its abandonment variant. */
    public static final long TRANSIT_STATION = 0x5654_5354_4154_4e21L;
    /** Template seven: which strip pairs share a service passage under their courtyard passage. */
    public static final long ROUTE_PASSAGE = 0x5652_5041_5353_4721L;
    /** Which equal roofs are joined by a parapet opening or a bridge. */
    public static final long ROUTE_CONNECT = 0x5652_434f_4e4e_4354L;
    /** Which buildings carry a fire escape up their rear wall. */
    public static final long ROUTE_ESCAPE = 0x5652_4553_4341_5045L;
    /** Which apartment of a residential region hides a cellar. */
    public static final long ROUTE_REFUGE = 0x5652_5245_4655_4745L;

    // Layout revision 11 (milestone 15): alien ecology and city wildlife.
    /** Which courtyards and fenced square of a quarantine region hold a growth site. */
    public static final long GROWTH_SITE = 0x5647_524f_5754_4853L;
    /** Where a park's Murkhares and its Glowdeer live. */
    public static final long WILDLIFE_PARK = 0x5657_494c_4450_4b21L;
    /** Whether a residential courtyard keeps a Murkhare. */
    public static final long WILDLIFE_COURTYARD = 0x5657_494c_4443_5921L;
    /** Whether a superblock's tallest walk-up keeps a flock of Skitterwings on its roof. */
    public static final long WILDLIFE_ROOF = 0x5657_494c_4452_4f21L;
    /** Encounter catalogue five: which budgeted buildings keep a Breacher at their street door. */
    public static final long BREACHER = 0x5642_5245_4143_4852L;

    // Layout revision 12 (milestone 16): survivor camps and hidden civilians.
    /** Which held walk-up of a region is its survivor camp. */
    public static final long SITE_CAMP = 0x5653_4954_4543_414dL;
    /** How many people a camp or a hidden refuge shelters. */
    public static final long SITE_PEOPLE = 0x5653_4954_4550_504cL;
    /** A resident's name. */
    public static final long SITE_NAME = 0x5653_4954_454e_414dL;
    /** A camp trader's two standing offers. */
    public static final long SITE_TRADE = 0x5653_4954_4554_5244L;
    /** What a site's stash holds when its people are first met. */
    public static final long SITE_STASH = 0x5653_4954_4553_5448L;

    // Layout revision 13 (milestone 17): guarded strongholds and a raider yard.
    /** Which courtyard block of a civic region is held as its stronghold. */
    public static final long COMPOUND_STRONGHOLD = 0x5643_4d50_5354_524eL;
    /** Which industrial region beyond the third ring keeps a raider yard, and which depot row. */
    public static final long COMPOUND_RAIDERS = 0x5643_4d50_5241_4944L;
    /** Which passage or alley mouth of a compound carries its gate. */
    public static final long COMPOUND_GATE = 0x5643_4d50_4741_5445L;
    /** How many people a stronghold or a raider yard holds. */
    public static final long COMPOUND_PEOPLE = 0x5643_4d50_5045_4f50L;

    // Layout revision 14 (milestone 18): the burnt-out cars of the local streets.
    /** Whether a local street segment holds a wreck. */
    public static final long VEHICLE_SEGMENT = 0x5656_4548_5345_474dL;
    /** Where along its segment a wreck stands. */
    public static final long VEHICLE_OFFSET = 0x5656_4548_4f46_5354L;
    /** Which side of the centre line a wreck's lane is. */
    public static final long VEHICLE_SIDE = 0x5656_4548_5349_4445L;
    /** Which end of a wreck is its engine. */
    public static final long VEHICLE_FRONT = 0x5656_4548_4652_4e54L;
    /** Whether a wreck's cabin burnt out. */
    public static final long VEHICLE_BURNT = 0x5656_4548_4255_524eL;
    /** A generated fixture's wear, which decides how much dismantling it gives back. */
    public static final long SALVAGE_CONDITION = 0x5653_414c_5643_4f4eL;

    // Layout revision 16 (milestone 20): urban missions and the long-term objective.
    /** Which of the precinct's candidate roofs carries the relay mast. */
    public static final long RELAY_SITE = 0x5652_454c_4159_5354L;

    private UrbanSalts() {
    }
}
