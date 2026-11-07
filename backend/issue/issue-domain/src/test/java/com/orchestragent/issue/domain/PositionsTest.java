package com.orchestragent.issue.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.OptionalDouble;
import org.junit.jupiter.api.Test;

class PositionsTest {

    @Test
    void anEmptyColumnStartsAtOneStep() {
        assertThat(Positions.append(null)).isEqualTo(1024);
        assertThat(Positions.between(null, null)).hasValue(1024);
    }

    @Test
    void appendingGoesOneStepBelowTheLastIssue() {
        assertThat(Positions.append(3000.0)).isEqualTo(4024);
    }

    @Test
    void droppingBetweenTwoIssuesLandsHalfwayBetweenThem() {
        assertThat(Positions.between(1024.0, 2048.0)).hasValue(1536);
        assertThat(Positions.between(1.0, 2.0)).hasValue(1.5);
    }

    @Test
    void droppingAtTheTopGoesOneStepAboveTheFirstIssue() {
        assertThat(Positions.between(null, 1024.0)).hasValue(0);
        assertThat(Positions.between(null, 0.0)).hasValue(-1024);
    }

    @Test
    void droppingAtTheBottomGoesOneStepBelowTheLastIssue() {
        assertThat(Positions.between(2048.0, null)).hasValue(3072);
    }

    @Test
    void theResultAlwaysSortsStrictlyBetweenItsNeighbours() {
        double above = 1024;
        double below = 2048;
        // Keep dropping the new issue directly below the first one: each result must stay inside the gap.
        for (int i = 0; i < 20; i++) {
            OptionalDouble next = Positions.between(above, below);
            assertThat(next).isPresent();
            assertThat(next.getAsDouble()).isGreaterThan(above).isLessThan(below);
            below = next.getAsDouble();
        }
    }

    @Test
    void reportsThatTheColumnNeedsRenumberingWhenThereIsNoRoomLeft() {
        assertThat(Positions.between(1.0, 1.0 + 1e-9)).isEmpty();
        assertThat(Positions.between(1.0, 1.0000001)).isEmpty();
    }

    @Test
    void runsOutOfRoomAfterEnoughHalvingButNeverReturnsAnEqualPosition() {
        double above = 0;
        double below = 1024;
        int steps = 0;
        OptionalDouble next;
        while ((next = Positions.between(above, below)).isPresent()) {
            assertThat(next.getAsDouble()).isGreaterThan(above).isLessThan(below);
            below = next.getAsDouble();
            steps++;
            assertThat(steps).as("halving must terminate").isLessThan(100);
        }
        assertThat(steps).isGreaterThan(20);
    }

    @Test
    void rejectsNeighboursInTheWrongOrder() {
        assertThatThrownBy(() -> Positions.between(2.0, 1.0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Positions.between(1.0, 1.0)).isInstanceOf(IllegalArgumentException.class);
    }
}
