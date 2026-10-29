package buildcraftreforged.gametest;

import buildcraftreforged.BuildcraftReforged;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import jdk.jfr.FlightRecorder;
import jdk.jfr.Recording;
import jdk.jfr.RecordingState;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedFrame;
import jdk.jfr.consumer.RecordedMethod;
import jdk.jfr.consumer.RecordingFile;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * Audit (PLAN.md section 19): nothing opens a socket or a URL connection during startup and the first 1000 server
 * ticks. 4.2.2's version check ({@code core/Version.java}) is not ported, and this keeps it that way.
 * <p>
 * Java 25 has no SecurityManager to block sockets, so the {@code runGameTestServer} JVM starts a Flight Recorder
 * recording (see {@code build.gradle.kts}) that traces socket connects and binds, datagram sends and HTTP/FTP URL
 * connections from JVM start, plus every socket read and write. At tick 1000 this test reads the recording and fails
 * on any such event, printing its stack trace.
 */
final class NoSocketsTest {
    static final int TICKS = 1000;
    private static final String RECORDING = "buildcraftreforged-sockets";
    private static final Set<String> SOCKET_EVENTS = Set.of("jdk.MethodTrace", "jdk.SocketRead", "jdk.SocketWrite");
    private static final int FRAMES = 48;

    private NoSocketsTest() {
    }

    static void noSockets(GameTestHelper helper) {
        Recording recording = FlightRecorder.isAvailable() ? findRecording() : null;
        if (recording == null || recording.getState() != RecordingState.RUNNING
                || !recording.getSettings().getOrDefault("jdk.MethodTrace#filter", "").contains("sun.nio.ch.Net::connect")) {
            helper.fail("Flight Recorder recording '" + RECORDING + "' is not running; run this test with ./gradlew runGameTestServer");
            return;
        }
        long remaining = TICKS - helper.getLevel().getServer().getTickCount();
        helper.runAfterDelay(Math.max(remaining, 1), () -> {
            List<String> events = socketEvents();
            if (!events.isEmpty()) {
                events.forEach(event -> BuildcraftReforged.LOGGER.error("Network access during startup:\n{}", event));
                helper.fail(events.size() + " socket or URL connection events during startup and the first " + TICKS
                        + " ticks; first: " + events.getFirst().lines().limit(4).toList());
                return;
            }
            BuildcraftReforged.LOGGER.info("No socket or URL connection events up to server tick {}",
                    helper.getLevel().getServer().getTickCount());
            helper.succeed();
        });
    }

    private static Recording findRecording() {
        for (Recording recording : FlightRecorder.getFlightRecorder().getRecordings()) {
            if (RECORDING.equals(recording.getName())) {
                return recording;
            }
        }
        return null;
    }

    private static List<String> socketEvents() {
        try {
            Path file = Files.createTempFile("buildcraftreforged-sockets", ".jfr");
            try {
                try (Recording snapshot = FlightRecorder.getFlightRecorder().takeSnapshot()) {
                    snapshot.dump(file);
                }
                List<String> events = new ArrayList<>();
                for (RecordedEvent event : RecordingFile.readAllEvents(file)) {
                    if (SOCKET_EVENTS.contains(event.getEventType().getName())) {
                        events.add(describe(event));
                    }
                }
                return events;
            } finally {
                Files.deleteIfExists(file);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String describe(RecordedEvent event) {
        StringBuilder text = new StringBuilder(event.getEventType().getName());
        if (event.hasField("method")) {
            RecordedMethod method = event.getValue("method");
            text.append(' ').append(method.getType().getName()).append("::").append(method.getName());
        }
        if (event.hasField("host")) {
            text.append(" host=").append(event.getString("host")).append(" port=").append(event.getInt("port"));
        }
        if (event.getThread() != null) {
            text.append(" thread=").append(event.getThread().getJavaName());
        }
        if (event.getStackTrace() != null) {
            List<RecordedFrame> frames = event.getStackTrace().getFrames();
            for (RecordedFrame frame : frames.subList(0, Math.min(FRAMES, frames.size()))) {
                RecordedMethod method = frame.getMethod();
                text.append("\n    at ").append(method.getType().getName()).append('.').append(method.getName())
                        .append(':').append(frame.getLineNumber());
            }
        }
        return text.toString();
    }
}
