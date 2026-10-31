package buildcraftreforged.core.status;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class FlowMeterTest {
    private long time = 1000;
    private final FlowMeter meter = new FlowMeter(() -> time);

    @Test
    void emptyMeterReadsZero() {
        assertEquals(StatusReport.Flow.NONE, meter.snapshot());
        assertEquals(0, meter.energyInPerSecond());
    }

    @Test
    void countsTheLastSecond() {
        for (int tick = 0; tick < FlowMeter.WINDOW; tick++) {
            meter.itemsIn(2);
            meter.itemsOut(1);
            meter.fluidIn(50);
            meter.fluidOut(25);
            meter.energyIn(10);
            meter.energyOut(3);
            time++;
        }
        time--;
        assertEquals(new StatusReport.Flow(40, 20, 1000, 500), meter.snapshot());
        assertEquals(200, meter.energyInPerSecond());
        assertEquals(60, meter.energyOutPerSecond());

        // One tick later the oldest tick has left the window.
        time++;
        assertEquals(new StatusReport.Flow(38, 19, 950, 475), meter.snapshot());
    }

    @Test
    void forgetsAfterASecond() {
        meter.itemsIn(5);
        time += FlowMeter.WINDOW - 1;
        assertEquals(5, meter.snapshot().itemsIn());
        time++;
        assertEquals(0, meter.snapshot().itemsIn());
    }

    @Test
    void reusedBucketStartsAtZero() {
        meter.itemsIn(5);
        time += FlowMeter.WINDOW;
        meter.itemsIn(1);
        assertEquals(1, meter.snapshot().itemsIn());
    }

    @Test
    void ignoresNothingAndNegatives() {
        meter.itemsIn(0);
        meter.fluidOut(-5);
        assertEquals(StatusReport.Flow.NONE, meter.snapshot());
    }

    @Test
    void powerRatesArePerTick() {
        for (int tick = 0; tick < FlowMeter.WINDOW; tick++) {
            meter.energyIn(3);
            time++;
        }
        time--;
        StatusReport.Power drawn = StatusReport.Power.drawn(meter, 500, 1000);
        assertEquals(3F, drawn.rate());
        assertEquals(false, drawn.producing());
        assertEquals(0F, StatusReport.Power.produced(meter, 500, 1000).rate());
    }
}
