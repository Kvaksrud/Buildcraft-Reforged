/*
 * Buildcraft Reforged, 2026: new file, no 4.2.2 original. The shared status ledger (plan section 10), drawn like 4.2.2's
 * GuiEngine.EngineLedger.
 */
package buildcraftreforged.core.gui;

import buildcraftreforged.BuildcraftReforged;
import buildcraftreforged.core.status.StatusReason;
import buildcraftreforged.core.status.StatusReport;
import buildcraftreforged.core.status.StatusWidget;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.resources.Identifier;

/**
 * The status panel as a ledger: State, Reason, Flow, Power, Heat and Owner, each shown only when the block has it. A
 * machine screen adds it in {@code initLedgers}:
 *
 * <pre>{@code ledgerManager.add(new StatusLedger<>(this, StatusWidget.find(container)));}</pre>
 *
 * Opening it asks the server for reports and closing it stops them ({@link StatusWidget#setWatched}).
 */
/* Client only. */
public class StatusLedger<T extends BuildCraftContainer> extends GuiBuildCraft<T>.Ledger {
    public static final Identifier ICON = Identifier.fromNamespaceAndPath(BuildcraftReforged.MOD_ID, "icons/status");
    public static final int WIDTH = 168;
    public static final int LINE_HEIGHT = 11;
    /** Rows above the first line: icon and header. */
    public static final int HEADER_HEIGHT = 22;
    /** The most rows a report fills: State, Reason, Items, Fluid, two Power rows, Heat, Owner. */
    public static final int MAX_ROWS = 8;
    public static final int MAX_HEIGHT = HEADER_HEIGHT + MAX_ROWS * LINE_HEIGHT + 4;
    private static final int LABEL_WIDTH = 44;

    private final GuiBuildCraft<T> gui;
    private final StatusWidget widget;
    private final String header;
    int headerColour = 0xe1c92f;
    int subheaderColour = 0xaaafb8;

    public StatusLedger(GuiBuildCraft<T> gui, StatusWidget widget) {
        this(gui, widget, I18n.get("gui.status"));
    }

    public StatusLedger(GuiBuildCraft<T> gui, StatusWidget widget, String header) {
        gui.super();
        this.gui = gui;
        this.widget = widget;
        this.header = header;
        maxWidth = WIDTH;
        maxHeight = heightFor(0);
        overlayColor = 0x5a7fa0;
    }

    private static int heightFor(int rows) {
        return HEADER_HEIGHT + Math.max(1, rows) * LINE_HEIGHT + 4;
    }

    @Override
    public void update() {
        maxHeight = heightFor(lines(widget.report).size());
        super.update();
        if (isOpen() && currentHeight > maxHeight) {
            currentHeight = maxHeight;
        }
    }

    @Override
    public void setFullyOpen() {
        super.setFullyOpen();
        widget.setWatched(true);
    }

    @Override
    public void toggleOpen() {
        super.toggleOpen();
        widget.setWatched(isOpen());
    }

    @Override
    public void draw(int x, int y) {
        drawBackground(x, y);
        drawIcon(ICON, x + 3, y + 4);

        if (!isFullyOpened()) {
            return;
        }

        gui.drawStringWithShadow(header, x + 22, y + 8, headerColour);
        StatusReport report = widget.report;
        int lineY = y + HEADER_HEIGHT;
        if (report == null) {
            gui.drawString(I18n.get("gui.status.waiting"), x + 8, lineY, 0x404040);
            return;
        }
        Font font = Minecraft.getInstance().font;
        for (Line line : lines(report)) {
            gui.drawStringWithShadow(line.label, x + 8, lineY, subheaderColour);
            String value = font.plainSubstrByWidth(line.value, WIDTH - 12 - LABEL_WIDTH);
            gui.drawString(value, x + 8 + LABEL_WIDTH, lineY, line.colour);
            lineY += LINE_HEIGHT;
        }
    }

    @Override
    public String getTooltip() {
        StatusReport report = widget.report;
        if (report == null) {
            return I18n.get("gui.status");
        }
        return I18n.get("gui.status") + ": " + I18n.get(report.state().getTranslationKey());
    }

    /** One row of the panel. */
    public record Line(String label, String value, int colour) {
    }

    /** The rows a report fills, in panel order. Empty for no report. */
    public static List<Line> lines(StatusReport report) {
        List<Line> lines = new ArrayList<>();
        if (report == null) {
            return lines;
        }
        lines.add(new Line(I18n.get("gui.status.line.state"), I18n.get(report.state().getTranslationKey()),
                report.state().colour));
        if (report.reason() != StatusReason.NONE) {
            lines.add(new Line(I18n.get("gui.status.line.reason"), I18n.get(report.reason().getTranslationKey()),
                    0xb02020));
        }
        StatusReport.Flow flow = report.flow();
        if (flow != null) {
            if (flow.hasItems() || !flow.hasFluid()) {
                lines.add(new Line(I18n.get("gui.status.line.items"),
                        I18n.get("gui.status.flow.items", flow.itemsIn(), flow.itemsOut()), 0x000000));
            }
            if (flow.hasFluid()) {
                lines.add(new Line(I18n.get("gui.status.line.fluid"), I18n.get("gui.status.flow.fluid",
                        perTick(flow.fluidIn()), perTick(flow.fluidOut())), 0x000000));
            }
        }
        StatusReport.Power power = report.power();
        if (power != null) {
            lines.add(new Line(I18n.get("gui.status.line.power"),
                    I18n.get(power.producing() ? "gui.status.power.produced" : "gui.status.power.drawn",
                            String.format(java.util.Locale.ROOT, "%.1f", power.rate())), 0x000000));
            lines.add(new Line("", I18n.get("gui.status.power.stored", power.stored(), power.capacity()), 0x000000));
        }
        StatusReport.Heat heat = report.heat();
        if (heat != null) {
            String stage = I18n.get(heat.stage().getTranslationKey());
            lines.add(new Line(I18n.get("gui.status.line.heat"), heat.hasCountdown()
                    ? I18n.get("gui.status.heat.countdown", stage, heat.overheatSeconds()) : stage, heat.stage().colour));
        }
        if (report.owner() != null) {
            lines.add(new Line(I18n.get("gui.status.line.owner"), report.owner(), 0x000000));
        }
        return lines;
    }

    /** mB per second to mB/t, one decimal. */
    private static String perTick(int mbPerSecond) {
        return String.format(java.util.Locale.ROOT, "%.1f", mbPerSecond / 20F);
    }
}
