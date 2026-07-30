package com.veylon.simulation;

import com.veylon.Game;
import com.veylon.item.Inventory;
import com.veylon.item.ItemStack;
import com.veylon.item.ItemType;
import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;
import com.veylon.world.RackBatch;

import java.util.Iterator;
import java.util.Map;

/**
 * Ticks per-item state: food spoilage in the player inventory and crates,
 * drying-rack progress and rain-collector fill. Runs on the slow tick.
 */
public class ItemConditionSystem implements SlowTickSystem {

    public int itemsSpoiled;

    @Override
    public void reset() {
        itemsSpoiled = 0;
    }

    @Override
    public void slowTick(Game g, float dt) {
        // Cold air preserves food; heat spoils it faster.
        float envRate = spoilRate(g.player.envTemp);
        tickInventory(g, g.player.inventory, envRate * dt, true);

        for (Map.Entry<Vec3i, Inventory> e : g.world.crateContents.entrySet()) {
            Vec3i p = e.getKey();
            float crateTemp = g.temperature.envTempAt(g, p.x() + 0.5f, p.y() + 0.5f, p.z() + 0.5f);
            tickInventory(g, e.getValue(), spoilRate(crateTemp) * dt, false);
        }

        tickRacks(g, dt);
        tickCollectors(g, dt);
    }

    /** Spoil-speed multiplier by ambient temperature. */
    private float spoilRate(float temp) {
        if (temp < 0) {
            return 0.25f;
        }
        if (temp < 8) {
            return 0.5f;
        }
        if (temp > 28) {
            return 1.6f;
        }
        return 1f;
    }

    private void tickInventory(Game g, Inventory inv, float elapsed, boolean notify) {
        for (int i = 0; i < inv.size(); i++) {
            ItemStack s = inv.get(i);
            if (s == null || !s.type.spoils()) {
                continue;
            }
            s.freshness -= elapsed;
            if (s.freshness <= 0) {
                itemsSpoiled += s.count;
                ItemType rotted = switch (s.type) {
                    case RAW_MEAT, COOKED_MEAT, DRIED_MEAT -> ItemType.SPOILED_MEAT;
                    default -> null; // berries/herbs rot away entirely
                };
                if (notify) {
                    g.log(s.count + "x " + s.type.displayName
                            + (rotted != null ? " spoiled." : " rotted away."));
                }
                if (rotted != null) {
                    ItemStack ns = new ItemStack(rotted, s.count);
                    inv.set(i, ns);
                } else {
                    inv.set(i, null);
                }
            }
        }
    }

    private void tickRacks(Game g, float dt) {
        for (Iterator<Map.Entry<Vec3i, RackBatch>> it = g.world.rackBatches.entrySet().iterator();
             it.hasNext(); ) {
            Map.Entry<Vec3i, RackBatch> e = it.next();
            Vec3i p = e.getKey();
            if (g.world.getBlock(p.x(), p.y(), p.z()) != BlockType.DRYING_RACK) {
                it.remove();
                continue;
            }
            RackBatch batch = e.getValue();
            if (batch.done()) {
                continue;
            }
            // Rain stalls drying for exposed racks; warm dry air speeds it up.
            float rate = 1f;
            if (g.weather.isPrecip() && g.world.skyLight(p.x(), p.y() + 1, p.z()) > 0.9f) {
                rate = 0.15f;
            } else if (g.player.envTemp > 24) {
                rate = 1.4f;
            }
            boolean wasDone = batch.done();
            batch.progress += dt * rate;
            if (!wasDone && batch.done()
                    && p.distSq(g.player.pos.x, g.player.pos.y, g.player.pos.z) < 40 * 40) {
                g.log("The drying rack has finished: " + batch.count + "x "
                        + batch.output().displayName + " ready.");
            }
        }
    }

    private void tickCollectors(Game g, float dt) {
        if (!g.weather.isPrecip() || g.weather.effective() == WeatherSystem.Weather.SNOW) {
            return;
        }
        float gain = 0.08f * g.weather.intensity() * dt;
        for (Map.Entry<Vec3i, Float> e : g.world.collectorWater.entrySet()) {
            Vec3i p = e.getKey();
            if (g.world.getBlock(p.x(), p.y(), p.z()) != BlockType.RAIN_COLLECTOR) {
                continue;
            }
            if (g.world.skyLight(p.x(), p.y() + 1, p.z()) > 0.9f) {
                e.setValue(Math.min(3f, e.getValue() + gain));
            }
        }
    }
}
