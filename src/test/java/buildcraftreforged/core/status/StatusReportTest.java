package buildcraftreforged.core.status;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

class StatusReportTest {

    private static StatusReport roundTrip(StatusReport report) {
        ByteBuf buf = Unpooled.buffer();
        StatusReport.STREAM_CODEC.encode(buf, report);
        StatusReport decoded = StatusReport.STREAM_CODEC.decode(buf);
        assertEquals(0, buf.readableBytes(), "codec left bytes unread");
        return decoded;
    }

    @Test
    void allSixLinesRoundTrip() {
        StatusReport report = new StatusReport(MachineStatus.OVERHEATING, StatusReason.OUTPUT_FULL,
                new StatusReport.Flow(12, 4, 500, 0), new StatusReport.Power(12.5F, true, 4000, 10000),
                new StatusReport.Heat(HeatStage.RED, 245), "Builder");
        assertEquals(report, roundTrip(report));
    }

    @Test
    void absentLinesStayAbsent() {
        StatusReport report = new StatusReport(MachineStatus.IDLE, StatusReason.NONE, null, null, null, null);
        StatusReport decoded = roundTrip(report);
        assertEquals(report, decoded);
        assertNull(decoded.flow());
        assertNull(decoded.owner());
    }

    @Test
    void noCountdownRoundTrips() {
        StatusReport report = new StatusReport(MachineStatus.WORKING, StatusReason.NONE, null, null,
                new StatusReport.Heat(HeatStage.BLUE, StatusReport.Heat.NO_COUNTDOWN), null);
        assertEquals(report, roundTrip(report));
        assertEquals(StatusReport.Heat.NO_COUNTDOWN, new StatusReport.Heat(HeatStage.BLUE, -40).overheatSeconds());
    }

    @Test
    void unknownStateFailsToDecode() {
        ByteBuf buf = Unpooled.buffer();
        buf.writeByte(MachineStatus.values().length);
        assertThrows(RuntimeException.class, () -> StatusReport.STREAM_CODEC.decode(buf));
    }

    @Test
    void longOwnerIsCut() {
        String owner = "x".repeat(StatusReport.MAX_OWNER_LENGTH + 10);
        StatusReport report = new StatusReport(MachineStatus.IDLE, StatusReason.NONE, null, null, null, owner);
        assertEquals(StatusReport.MAX_OWNER_LENGTH, roundTrip(report).owner().length());
    }

    @Test
    void defaultsFollowTheMachine() {
        class Machine implements IStatusProvider, buildcraftreforged.core.IMachine {
            boolean active;
            boolean blocked;

            @Override
            public boolean isActive() {
                return active;
            }

            @Override
            public boolean isBlocked() {
                return blocked;
            }

            @Override
            public boolean manageFluids() {
                return false;
            }

            @Override
            public boolean manageSolids() {
                return false;
            }

            @Override
            public boolean allowAction(buildcraftreforged.api.gates.IAction action) {
                return false;
            }
        }
        Machine machine = new Machine();
        assertEquals(MachineStatus.IDLE, machine.getStatusState());
        machine.active = true;
        assertEquals(MachineStatus.WORKING, machine.getStatusState());
        machine.blocked = true;
        assertEquals(MachineStatus.BLOCKED, machine.getStatusState());
        StatusReport report = StatusReport.of(machine);
        assertEquals(StatusReason.NONE, report.reason());
        assertNull(report.flow());
        assertNull(report.power());
        assertNull(report.heat());
        assertNull(report.owner());
    }

    @Test
    void watchPacketRoundTrips() {
        ByteBuf buf = Unpooled.buffer();
        PacketStatusWatch.STREAM_CODEC.encode(buf, new PacketStatusWatch(9, true));
        assertEquals(new PacketStatusWatch(9, true), PacketStatusWatch.STREAM_CODEC.decode(buf));
    }
}
