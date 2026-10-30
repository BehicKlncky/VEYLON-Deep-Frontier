package com.veylon;

import com.veylon.util.Vec3i;
import com.veylon.world.World;
import com.veylon.world.urban.BuildingPlan;
import com.veylon.world.urban.UrbanDistrict;
import com.veylon.world.urban.UrbanIds;

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
 * ({@link UrbanEcologyTourPlan}). Pure: it builds plans and never changes a block.
 */
record UrbanAtmosphereTourPlan(Map<UrbanDistrict, Stop> streets, Map<String, Stop> apartment, String building,
                               Stop cellar, Stop gate, Stop growth) {

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
        return new UrbanAtmosphereTourPlan(streets, apartment, UrbanIds.format(flats.id()), cellar, gate, growth);
    }
}
