package com.veylon.ai;

import com.veylon.util.Vec3i;
import java.util.LinkedHashMap;
import java.util.List;

/** World-owned transient local routes. Edits invalidate only intersecting route envelopes. */
public final class NavigationCache {
    public static final int MAX_ROUTES = 64;
    public final PathBudget budget = new PathBudget();
    private record Key(Vec3i from, Vec3i to) { }
    private record Route(List<Vec3i> cells, int x0, int y0, int z0, int x1, int y1, int z1) { }
    private final LinkedHashMap<Key, Route> routes = new LinkedHashMap<>();

    List<Vec3i> get(Vec3i from, Vec3i to) {
        Route route = routes.get(new Key(from, to));
        return route == null ? null : route.cells;
    }

    void put(Vec3i from, Vec3i to, List<Vec3i> cells) {
        int x0 = from.x(), x1 = x0, y0 = from.y(), y1 = y0, z0 = from.z(), z1 = z0;
        for (Vec3i p : cells) {
            x0 = Math.min(x0, p.x()); x1 = Math.max(x1, p.x());
            y0 = Math.min(y0, p.y()); y1 = Math.max(y1, p.y());
            z0 = Math.min(z0, p.z()); z1 = Math.max(z1, p.z());
        }
        if (routes.size() >= MAX_ROUTES) { routes.remove(routes.keySet().iterator().next()); }
        routes.put(new Key(from, to), new Route(cells, x0 - 1, y0 - 1, z0 - 1, x1 + 1, y1 + 2, z1 + 1));
    }

    public boolean valid(List<Vec3i> cells) {
        for (Route route : routes.values()) { if (route.cells == cells) { return true; } }
        return false;
    }

    public void changed(Vec3i cell) { changed(cell.x(), cell.y(), cell.z()); }
    public void changed(int x, int y, int z) {
        routes.values().removeIf(r -> x >= r.x0 && x <= r.x1 && y >= r.y0 && y <= r.y1
                && z >= r.z0 && z <= r.z1);
    }
    public int size() { return routes.size(); }
}
