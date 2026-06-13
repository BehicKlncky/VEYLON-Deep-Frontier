package com.veylon.ui;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/** Rolling world-event log shown in the HUD and the simulation panel. */
public class EventLog {

    private static final int MAX = 120;

    private final Deque<String> lines = new ArrayDeque<>();

    public void add(String line) {
        lines.addLast(line);
        while (lines.size() > MAX) {
            lines.removeFirst();
        }
    }

    public List<String> recent(int n) {
        List<String> out = new ArrayList<>();
        var it = lines.descendingIterator();
        while (it.hasNext() && out.size() < n) {
            out.add(it.next());
        }
        java.util.Collections.reverse(out);
        return out;
    }

    public List<String> all() {
        return new ArrayList<>(lines);
    }

    public void clear() {
        lines.clear();
    }
}
