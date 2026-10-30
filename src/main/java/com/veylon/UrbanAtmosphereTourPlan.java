package com.veylon;

import com.veylon.util.Vec3i;
import com.veylon.world.World;
import com.veylon.world.urban.BuildingPlan;
import com.veylon.world.urban.UrbanDistrict;
import com.veylon.world.urban.UrbanIds;
import com.veylon.world.urban.UrbanTraces;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Where the milestone-22 atmosphere tour stands and what it looks at, read from the plans the
 * earlier tours already use: a street stop in each district ({@link UrbanDistrictTourPlan}), the
 * rooms of a generated walk-up with a basement ({@link UrbanApartmentQaScene#createViews}), the
 * hidden cellar of the refuge nearest the spawn ({@link UrbanSurvivorTourPlan}), the gate of the
 * nearest stronghold ({@link UrbanStrongholdTourPlan}) and the first alien growth site
 * ({@link UrbanEcologyTourPlan}), and the chalk mark beside that refuge's street door
 * ({@link UrbanTraces}). Pure: it builds plans and never changes a block.
 */
record UrbanAtmosphereTourPlan(Map<UrbanDistrict, Stop> streets, Map<String, Stop> apartment, String building,
                               Stop cellar, Stop gate, Stop growth, Stop mark) {

    /** A standing cell (feet) and the cell looked at, aimed {@code height} above its floor. */
    record Stop(Vec3i stand, Vec3i subject, float height) { }

    /** The apartment views the tour uses, by their names in the apartment scene. */
    static final List<String> APARTMENT_VIEWS = List.of("entrance", "kitchen", "upper-floor", "stairs", "basement");

    static UrbanAtmosphereTourPlan of(World world) {
        Map<UrbanDistrict, Stop> streets = new EnumMap<>(UrbanDistrict.class);
        UrbanDistrictTourPlan districts = UrbanDistrictTourPlan.of(world);
        for (var entry : districts.stops().entrySet()) {
            UrbanDistrictTourPlan.Stop s = entry.getValue();
            streets.put(entry.getKey(), new Stop(s.stand(), s.subject(), s.height()));
        }
        BuildingPlan flats = UrbanApartmentQaScene.selectBuilding(world);
        Map<String, Stop> apartment = new java.util.LinkedHashMap<>();
        List<UrbanApartmentQaScene.View> views = new ArrayList<>(UrbanApartmentQaScene.createViews(flats));
        for (String name : APARTMENT_VIEWS) {
            UrbanApartmentQaScene.View v = views.stream().filter(view -> view.name().equals(name)).findFirst()
                    .orElseThrow(() -> new IllegalStateException("the apartment has no " + name + " view"));
            apartment.put(name, new Stop(v.feet(), v.lookAt(), name.equals("stairs") ? 1.2f : .6f));
        }
        UrbanSurvivorTourPlan.Refuge refuge = UrbanSurvivorTourPlan.of(world).refuge();
        Stop cellar = new Stop(refuge.inside(), refuge.site().safe(), .6f);
        UrbanStrongholdTourPlan.Stronghold hold = UrbanStrongholdTourPlan.of(world).stronghold();
        Stop gate = new Stop(hold.street(), hold.signStand(), 1.6f);
        UrbanEcologyTourPlan.Site site = UrbanEcologyTourPlan.of(world).first();
        Stop growth = new Stop(site.stand(), site.site().node(), .5f);
        return new UrbanAtmosphereTourPlan(streets, apartment, UrbanIds.format(flats.id()), cellar, gate, growth,
                mark(world, refuge.site()));
    }

    /**
     * The chalk mark beside the refuge building's street door (milestone 22), seen from two cells out
     * in the street.
     */
    private static Stop mark(World world, com.veylon.world.urban.SurvivorSites.Site refuge) {
        BuildingPlan b = world.urban.buildingAt(refuge.anchor().x(), refuge.anchor().z());
        List<UrbanTraces.Trace> traces = new ArrayList<>();
        UrbanTraces.collect(world.urban, com.veylon.world.urban.StreetNetwork.regionOf(refuge.anchor().x()),
                com.veylon.world.urban.StreetNetwork.regionOf(refuge.anchor().z()), b.parcel().x0() - 1, b.parcel().z0() - 1,
                b.parcel().x1() + 1, b.parcel().z1() + 1, traces);
        if (traces.isEmpty()) { throw new IllegalStateException("the refuge's building has no knock mark"); }
        UrbanTraces.Trace t = traces.getFirst();
        int[] out = OUTWARD[t.face()];
        return new Stop(new Vec3i(t.x() + 2 * out[0], UrbanTraces.EYE_Y - 1, t.z() + 2 * out[1]),
                new Vec3i(t.x(), t.y(), t.z()), .5f);
    }

    /** The outward step of the mesher's side faces 2 to 5: north, south, west, east. */
    private static final int[][] OUTWARD = {{0, 0}, {0, 0}, {0, -1}, {0, 1}, {-1, 0}, {1, 0}};
}
