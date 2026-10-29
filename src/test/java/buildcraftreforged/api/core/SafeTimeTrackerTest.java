package buildcraftreforged.api.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

class SafeTimeTrackerTest {

    @Test
    void firstCallFiresAndReportsNoDuration() {
        SafeTimeTracker tracker = new SafeTimeTracker();
        assertTrue(tracker.markTimeIfDelay(100, 20));
        // currentTime - Long.MIN_VALUE overflows to a negative duration, which durationOfLastDelay reports as 0.
        assertEquals(0, tracker.durationOfLastDelay());
    }

    @Test
    void firesOnlyOnceTheDelayHasPassed() {
        SafeTimeTracker tracker = new SafeTimeTracker();
        tracker.markTimeIfDelay(100, 20);

        assertFalse(tracker.markTimeIfDelay(110, 20));
        assertFalse(tracker.markTimeIfDelay(119, 20));
        assertTrue(tracker.markTimeIfDelay(120, 20));
        assertEquals(20, tracker.durationOfLastDelay());

        assertTrue(tracker.markTimeIfDelay(145, 20));
        assertEquals(25, tracker.durationOfLastDelay());
    }

    @Test
    void zeroDelayFiresEveryTick() {
        SafeTimeTracker tracker = new SafeTimeTracker();
        tracker.markTime(10);
        assertTrue(tracker.markTimeIfDelay(10, 0));
        assertEquals(0, tracker.durationOfLastDelay());
        assertTrue(tracker.markTimeIfDelay(11, 0));
        assertEquals(1, tracker.durationOfLastDelay());
    }

    @Test
    void timeGoingBackwardsResetsTheMark() {
        SafeTimeTracker tracker = new SafeTimeTracker();
        tracker.markTimeIfDelay(1000, 20);

        assertFalse(tracker.markTimeIfDelay(50, 20));
        assertFalse(tracker.markTimeIfDelay(69, 20));
        assertTrue(tracker.markTimeIfDelay(70, 20));
        assertEquals(20, tracker.durationOfLastDelay());
    }

    @Test
    void markTimeRestartsTheDelay() {
        SafeTimeTracker tracker = new SafeTimeTracker();
        tracker.markTime(200);

        assertFalse(tracker.markTimeIfDelay(210, 20));
        assertTrue(tracker.markTimeIfDelay(220, 20));
    }

    @Test
    void noLevelNeverFires() {
        SafeTimeTracker tracker = new SafeTimeTracker();
        assertFalse(tracker.markTimeIfDelay((Level) null, 0));
    }
}
