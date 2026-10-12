package com.veylon.ai;

/** Main-thread allowance shared by every population for one entity fast tick. */
public final class PathBudget {
    public static final int MAX_QUERIES = 4;
    public static final int MAX_EXPANSIONS = 2800;
    private boolean ticking;
    private int queries, expansions, deferred, failures, hits, unsupported;

    public void beginTick() {
        ticking = true;
        queries = expansions = deferred = failures = hits = unsupported = 0;
    }

    int query(int requested) {
        if (!ticking) { return Math.min(Math.max(0, requested), Pathfinder.DEFAULT_BUDGET); }
        if (queries >= MAX_QUERIES || expansions >= MAX_EXPANSIONS) {
            deferred++;
            return 0;
        }
        queries++;
        return Math.min(Math.max(0, requested), MAX_EXPANSIONS - expansions);
    }

    void expanded() { if (ticking) { expansions++; } }
    void failed() { if (ticking) { failures++; } }
    void hit() { hits++; }
    void unsupported() { unsupported++; }
    public int queries() { return queries; }
    public int expansions() { return expansions; }
    public int deferred() { return deferred; }
    public int failures() { return failures; }
    public int cacheHits() { return hits; }
    public int unsupportedEntries() { return unsupported; }
}
