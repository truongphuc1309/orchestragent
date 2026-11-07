package com.orchestragent.issue.domain;

import java.util.OptionalDouble;

/**
 * Ordering of issues inside one board column. A position is a plain number; an issue dropped between two
 * others gets the number halfway between them, so a drag changes only one row. Repeated halving eventually
 * runs out of room, and then the column is renumbered with {@link #STEP} gaps.
 */
public final class Positions {

    /** Gap between neighbours after appending or renumbering. */
    public static final double STEP = 1024;

    /** Below this gap two positions are too close to split safely. */
    static final double MIN_GAP = 1e-6;

    private Positions() {
    }

    /** Position for an issue added below the current last one ({@code null} when the column is empty). */
    public static double append(Double last) {
        return last == null ? STEP : last + STEP;
    }

    /**
     * Position between two neighbours. {@code above} is the issue that will sit directly above (null at the
     * top), {@code below} the one directly below (null at the bottom).
     *
     * @return empty when there is no room left between them and the column needs renumbering
     * @throws IllegalArgumentException when {@code above} is not really above {@code below}
     */
    public static OptionalDouble between(Double above, Double below) {
        if (above == null && below == null) {
            return OptionalDouble.of(STEP);
        }
        if (above == null) {
            return OptionalDouble.of(below - STEP);
        }
        if (below == null) {
            return OptionalDouble.of(above + STEP);
        }
        if (above >= below) {
            throw new IllegalArgumentException("The upper neighbour must come before the lower one");
        }
        if (below - above < MIN_GAP) {
            return OptionalDouble.empty();
        }
        return OptionalDouble.of(above + (below - above) / 2);
    }
}
