package com.veylon.world.urban;

import com.veylon.item.Inventory;
import com.veylon.item.ItemType;
import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;
import com.veylon.world.Chunk;

import java.util.ArrayList;

/** Pure, finite starter constraints. No retries of the city, content injection or mutable RNG. */
public record UrbanStarterSite(BuildingPlan refuge, Vec3i fire, Vec3i collector) {
    public static boolean isNeighbour(ParcelPlan parcel) {
        return UrbanIds.regionX(parcel.id()) == 0 && UrbanIds.regionZ(parcel.id()) == 0
                && parcel.superblock() == 5;
    }

    public static UrbanStarterSite of(UrbanWorldState urban) {
        DistrictPlan district = urban.regionPlan(0, 0);
        ParcelPlan parcel = district.refuge();
        ParcelPlan.Rect court = district.superblock(parcel.superblock()).open();
        int x = (court.x0() + court.x1()) / 2, z = (court.z0() + court.z1()) / 2;
        return new UrbanStarterSite(urban.buildingAt(parcel.x0(), parcel.z0()),
                new Vec3i(x, UrbanDatum.STREET_Y + 1, z - 2),
                new Vec3i(x, UrbanDatum.STREET_Y + 1, z + 2));
    }

    /** Replace existing facade props, retaining their proven interaction lanes and identities. */
    static BuildingPlan furnish(BuildingPlan original) {
        var voxels = original.voxels().edit();
        var fixtures = new ArrayList<BuildingPlan.Fixture>();
        boolean bench = false;
        for (var fixture : original.fixtures()) {
            BlockType block = fixture.block();
            if (original.parcel().use() == ParcelPlan.Use.REFUGE && fixture.position().y() == 41
                    && fixture.kind() == BuildingPlan.FixtureKind.FURNITURE && block == BlockType.TABLE) {
                block = bench ? BlockType.TORCH : BlockType.WORKBENCH;
                bench = true;
                voxels.set(fixture.position().x(), fixture.position().y(), fixture.position().z(), block);
            }
            fixtures.add(new BuildingPlan.Fixture(fixture.id(), fixture.kind(), fixture.position(),
                    fixture.height(), block, fixture.target(), fixture.locked()));
        }
        if (original.parcel().use() == ParcelPlan.Use.REFUGE && !bench) {
            throw new IllegalStateException("starter refuge has no reachable work surface");
        }
        return new BuildingPlan(original.id(), UrbanRevision.BUILDING_TEMPLATE_STARTER,
                original.template() + "-starter", original.family(), original.variant(), original.parcel(),
                original.storeys(), original.basement(), original.floors(), fixtures, original.approaches(),
                original.stairs(), voxels.build());
    }

    /** Each chunk owns its two courtyard utility cells; recorded edits subsequently override them. */
    public void stamp(Chunk chunk) {
        stamp(chunk, fire, BlockType.CAMPFIRE);
        stamp(chunk, collector, BlockType.RAIN_COLLECTOR);
    }

    private static void stamp(Chunk chunk, Vec3i p, BlockType block) {
        if (Math.floorDiv(p.x(), 16) == chunk.cx && Math.floorDiv(p.z(), 16) == chunk.cz) {
            chunk.set(Math.floorMod(p.x(), 16), p.y(), Math.floorMod(p.z(), 16), block);
        }
    }

    /** Ground-floor reservations only. Ordinary search/depletion ledger owns these finite supplies. */
    public static Inventory supplies(BuildingPlan building, BuildingPlan.Fixture fixture) {
        if (!isNeighbour(building.parcel()) || fixture.position().y() != 41) { return null; }
        Inventory supplies = new Inventory(UrbanContainers.SLOTS);
        switch (BuildingFurnishing.containerUse(building, fixture)) {
            case KITCHEN_CABINET, FRIDGE, PANTRY_SHELF -> {
                supplies.add(ItemType.DRIED_BERRY, 3); supplies.add(ItemType.WATERSKIN_CLEAN, 1);
                supplies.add(ItemType.PLANK, 2); supplies.add(ItemType.SCRAP, 2);
            }
            case BATHROOM_CABINET -> {
                supplies.add(ItemType.BANDAGE, 2); supplies.add(ItemType.ANTISEPTIC, 1);
                supplies.add(ItemType.WATERSKIN_CLEAN, 1); supplies.add(ItemType.FIBER, 4);
            }
            case WARDROBE -> {
                supplies.add(ItemType.FIBER, 8); supplies.add(ItemType.RAIN_CLOAK, 1);
                supplies.add(ItemType.BANDAGE, 2);
            }
            case UTILITY_LOCKER -> {
                supplies.add(ItemType.IRON_PICKAXE, 1); supplies.add(ItemType.SCRAP, 4);
                supplies.add(ItemType.PLANK, 4); supplies.add(ItemType.TORCH, 2);
            }
            case SHOP_SHELF -> {
                supplies.add(ItemType.DRIED_MEAT, 3); supplies.add(ItemType.WATERSKIN_CLEAN, 1);
                supplies.add(ItemType.FIBER, 4); supplies.add(ItemType.PLANK, 4);
            }
            case STOCKROOM_CRATE -> {
                supplies.add(ItemType.SCRAP, 4); supplies.add(ItemType.PLANK, 4);
                supplies.add(ItemType.FIBER, 4); supplies.add(ItemType.WATERSKIN_CLEAN, 1);
            }
            case DRESSER_DESK -> {
                supplies.add(ItemType.DRIED_BERRY, 2); supplies.add(ItemType.IRON_PICKAXE, 1);
                supplies.add(ItemType.TORCH, 2); supplies.add(ItemType.SCRAP, 2);
            }
        }
        return supplies;
    }

    /**
     * Content revision four's starter reservations (milestone 23): the DESIGN §4.4 guarantees with a
     * modest margin, placed so that the first upgrade takes a trip out of the refuge. Every container of
     * the refuge building is reserved, on every storey: a day of food and water, first aid, fibre, wood
     * for the fire and a torch, but no scrap, so the refuge alone never funds the reinforced spear. On the
     * neighbours' ground floors each kitchen keeps a serving and a skin of water, each bathroom a bandage
     * and rags, each wardrobe fibre and each utility locker scrap; a building's first locker keeps a pry
     * tool, scrap and offcuts enough for the spear; the shop's shelves keep dried meat and water and its
     * first stockroom crate scrap and planks. The neighbours' upper floors were picked over to half their
     * contents before the survivor came (DESIGN §8, S5): half their containers, chosen by a pure hash, are
     * empty, and the rest roll their content-four tables like every other container in the city.
     *
     * @return the reservation (possibly empty), or null for a container that rolls its table
     */
    public static Inventory economySupplies(long seed, BuildingPlan building, BuildingPlan.Fixture fixture) {
        if (!isNeighbour(building.parcel())) { return null; }
        boolean refuge = building.parcel().use() == ParcelPlan.Use.REFUGE;
        boolean ground = fixture.position().y() == UrbanDatum.STREET_Y + 1;
        if (!refuge && !ground) {
            return UrbanHash.range(0, 1, seed, UrbanRevision.ECONOMY_CONTENT, UrbanSalts.STARTER_PICKED_OVER,
                    fixture.id(), 0, 0) == 0 ? new Inventory(UrbanContainers.SLOTS) : null;
        }
        BuildingFurnishing.ContainerUse use = BuildingFurnishing.containerUse(building, fixture);
        Inventory supplies = new Inventory(UrbanContainers.SLOTS);
        if (refuge) {
            switch (use) {
                case KITCHEN_CABINET, FRIDGE, PANTRY_SHELF -> {
                    supplies.add(ItemType.DRIED_BERRY, ground ? 2 : 1);
                    if (ground) { supplies.add(ItemType.WATERSKIN_CLEAN, 1); }
                }
                case BATHROOM_CABINET -> {
                    supplies.add(ItemType.BANDAGE, 1);
                    if (ground) { supplies.add(ItemType.ANTISEPTIC, 1); }
                }
                case WARDROBE -> supplies.add(ItemType.FIBER, 2);
                case UTILITY_LOCKER -> {
                    supplies.add(ItemType.PLANK, 2);
                    if (ground) { supplies.add(ItemType.TORCH, 1); }
                }
                case DRESSER_DESK -> supplies.add(ItemType.TORCH, 1);
                default -> { }
            }
            return supplies;
        }
        switch (use) {
            case KITCHEN_CABINET, FRIDGE, PANTRY_SHELF -> {
                supplies.add(ItemType.DRIED_BERRY, 1); supplies.add(ItemType.WATERSKIN_CLEAN, 1);
            }
            case BATHROOM_CABINET -> {
                supplies.add(ItemType.BANDAGE, 1); supplies.add(ItemType.FIBER, 2);
            }
            case WARDROBE -> supplies.add(ItemType.FIBER, 3);
            case UTILITY_LOCKER -> {
                if (firstOnItsFloor(building, fixture, use)) {
                    supplies.add(ItemType.IRON_PICKAXE, 1); supplies.add(ItemType.SCRAP, 3);
                    supplies.add(ItemType.PLANK, 4);
                } else {
                    supplies.add(ItemType.SCRAP, 2);
                }
            }
            case SHOP_SHELF -> {
                supplies.add(ItemType.DRIED_MEAT, 1); supplies.add(ItemType.WATERSKIN_CLEAN, 1);
            }
            case STOCKROOM_CRATE -> {
                if (!firstOnItsFloor(building, fixture, use)) { return null; }
                supplies.add(ItemType.SCRAP, 2); supplies.add(ItemType.PLANK, 2);
            }
            default -> { return null; }
        }
        return supplies;
    }

    /** Whether a container is the lowest-id one of its use on its storey of its building. */
    private static boolean firstOnItsFloor(BuildingPlan building, BuildingPlan.Fixture fixture,
                                           BuildingFurnishing.ContainerUse use) {
        for (BuildingPlan.Fixture other : building.fixtures()) {
            if (other.kind() == BuildingPlan.FixtureKind.CONTAINER && other.id() < fixture.id()
                    && other.position().y() == fixture.position().y()
                    && BuildingFurnishing.containerUse(building, other) == use) {
                return false;
            }
        }
        return true;
    }
}
