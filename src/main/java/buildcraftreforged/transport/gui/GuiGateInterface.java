/**
 * Copyright (c) SpaceToad, 2011 http://www.mod-buildcraft.com
 *
 * BuildCraft is distributed under the terms of the Minecraft Mod Public License
 * 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 *
 * Modified by Buildcraft Reforged, 2026: the screen of the gate menu; trigger and action icons are GUI sprites drawn
 * by id; the parameter comes from the carried stack on the server too; the Inventory label is the menu's own;
 * mouse buttons arrive in 1.6.4 numbering through GuiBuildCraft.toLegacyButton. Client only.
 */
package buildcraftreforged.transport.gui;

import buildcraftreforged.api.gates.IAction;
import buildcraftreforged.api.gates.ITrigger;
import buildcraftreforged.api.gates.ITriggerParameter;
import buildcraftreforged.core.gui.GuiAdvancedInterface;
import buildcraftreforged.transport.Gate;
import buildcraftreforged.transport.Gate.GateKind;
import java.util.Iterator;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/* Client only. */
public class GuiGateInterface extends GuiAdvancedInterface<ContainerGateInterface> {

    private final ContainerGateInterface _container;
    private final Gate gate;
    private int nbEntries;

    /** Draws an action or trigger icon, a GUI sprite named by its id. */
    abstract class IconSlot extends AdvancedSlot {

        IconSlot(int x, int y) {
            super(x, y);
        }

        abstract @Nullable Identifier getSpriteId();

        @Override
        public void drawSprite(int cornerX, int cornerY) {
            Identifier sprite = getSpriteId();
            if (isDefined() && sprite != null) {
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, cornerX + x, cornerY + y, 16, 16);
            }
        }
    }

    class TriggerSlot extends IconSlot {

        int slot;

        public TriggerSlot(int x, int y, int slot) {
            super(x, y);

            this.slot = slot;
        }

        @Override
        public String getDescription() {
            ITrigger trigger = gate.getTrigger(slot);
            if (trigger != null)
                return trigger.getDescription();
            else
                return "";
        }

        @Override
        @Nullable Identifier getSpriteId() {
            ITrigger trigger = gate.getTrigger(slot);
            return trigger != null ? trigger.getIcon() : null;
        }

        @Override
        public boolean isDefined() {
            return gate.getTrigger(slot) != null;
        }

        public @Nullable ITrigger getTrigger() {
            return gate.getTrigger(slot);
        }
    }

    class ActionSlot extends IconSlot {

        int slot;

        public ActionSlot(int x, int y, int slot) {
            super(x, y);

            this.slot = slot;
        }

        @Override
        public String getDescription() {
            IAction action = gate.getAction(slot);
            if (action != null)
                return action.getDescription();
            else
                return "";
        }

        @Override
        @Nullable Identifier getSpriteId() {
            IAction action = gate.getAction(slot);
            return action != null ? action.getIcon() : null;
        }

        @Override
        public boolean isDefined() {
            return gate.getAction(slot) != null;
        }

        public @Nullable IAction getAction() {
            return gate.getAction(slot);
        }
    }

    class TriggerParameterSlot extends AdvancedSlot {

        int slot;

        public TriggerParameterSlot(int x, int y, int slot) {
            super(x, y);

            this.slot = slot;
        }

        @Override
        public boolean isDefined() {
            return gate.getTriggerParameter(slot) != null;
        }

        @Override
        public ItemStack getItemStack() {
            ITriggerParameter parameter = gate.getTriggerParameter(slot);
            if (parameter != null)
                return parameter.getItemStack();
            else
                return ItemStack.EMPTY;
        }

        public @Nullable ITriggerParameter getTriggerParameter() {
            return gate.getTriggerParameter(slot);
        }
    }

    public GuiGateInterface(ContainerGateInterface container, Inventory playerInventory, Component title) {
        super(container, null, container.gate.getGuiFile(), playerInventory, title, 176,
                container.gate.getGuiHeight());

        _container = container;
        gate = container.gate;

        int position = 0;

        if (gate.kind == GateKind.Single) {
            nbEntries = 1;

            slots = new GuiAdvancedInterface.AdvancedSlot[2];
            slots[0] = new TriggerSlot(62, 26, 0);
            slots[1] = new ActionSlot(98, 26, 0);
        } else if (gate.kind == GateKind.AND_2 || gate.kind == GateKind.OR_2) {
            nbEntries = 2;

            slots = new GuiAdvancedInterface.AdvancedSlot[4];

            slots[0] = new TriggerSlot(62, 26, 0);
            slots[1] = new TriggerSlot(62, 44, 1);
            slots[2] = new ActionSlot(98, 26, 0);
            slots[3] = new ActionSlot(98, 44, 1);
        } else if (gate.kind == GateKind.AND_3 || gate.kind == GateKind.OR_3) {
            nbEntries = 4;

            slots = new GuiAdvancedInterface.AdvancedSlot[12];

            for (int k = 0; k < 4; ++k) {
                slots[position] = new TriggerSlot(53, 26 + 18 * k, position);
                position++;
            }

            for (int k = 0; k < 4; ++k) {
                slots[position] = new ActionSlot(107, 26 + 18 * k, position - 4);
                position++;
            }

            for (int k = 0; k < 4; ++k) {
                slots[position] = new TriggerParameterSlot(71, 26 + 18 * k, position - 8);
                position++;

            }
        } else {
            nbEntries = 8;

            slots = new GuiAdvancedInterface.AdvancedSlot[24];

            for (int k = 0; k < 4; ++k) {
                slots[position] = new TriggerSlot(8, 26 + 18 * k, position);
                position++;
                slots[position] = new TriggerSlot(98, 26 + 18 * k, position);
                position++;
            }

            for (int k = 0; k < 4; ++k) {
                slots[position] = new ActionSlot(62, 26 + 18 * k, position - 8);
                position++;
                slots[position] = new ActionSlot(152, 26 + 18 * k, position - 8);
                position++;
            }

            for (int k = 0; k < 4; ++k) {
                slots[position] = new TriggerParameterSlot(26, 26 + 18 * k, position - 16);
                position++;
                slots[position] = new TriggerParameterSlot(116, 26 + 18 * k, position - 16);
                position++;
            }
        }

    }

    @Override
    protected void drawGuiContainerForegroundLayer(int par1, int par2) {
        String name = getTitle().getString();

        drawString(name, getCenteredOffset(name), 10, 0x404040);
        drawString(playerInventoryTitle.getString(), 8, imageHeight - 97, 0x404040);

        drawForegroundSelection(par1, par2);
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float f, int x, int y) {

        Identifier texture = gate.getGuiFile();

        setColor(1.0F, 1.0F, 1.0F, 1.0F);
        bindTexture(texture);

        int cornerX = (width - imageWidth) / 2;
        int cornerY = (height - imageHeight) / 2;
        drawTexturedModalRect(cornerX, cornerY, 0, 0, imageWidth, imageHeight);

        int triggerTracker = 0;
        for (int s = 0; s < slots.length; ++s) {
            AdvancedSlot slot = slots[s];

            if (slot instanceof TriggerSlot) {
                ITrigger trigger = ((TriggerSlot) slot).getTrigger();

                if (_container.getGateOrdinal() >= GateKind.AND_3.ordinal()) {

                    if (_container.triggerState[triggerTracker++]) {
                        bindTexture(texture);

                        drawTexturedModalRect(cornerX + slot.x + 35, cornerY + slot.y + 6, 176, 18, 18, 4);
                    }

                    if (trigger == null || !trigger.hasParameter()) {
                        bindTexture(texture);

                        drawTexturedModalRect(cornerX + slot.x + 17, cornerY + slot.y - 1, 176, 0, 18, 18);
                    }
                } else if (_container.triggerState[triggerTracker++]) {
                    bindTexture(texture);

                    drawTexturedModalRect(cornerX + slot.x + 17, cornerY + slot.y + 6, 176, 18, 18, 4);
                }
            }
        }

        drawBackgroundSlots();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        boolean handled = super.mouseClicked(event, doubleClick);

        int k = toLegacyButton(event.button());
        int cornerX = (width - imageWidth) / 2;
        int cornerY = (height - imageHeight) / 2;

        int position = getSlotAtLocation((int) event.x() - cornerX, (int) event.y() - cornerY);

        AdvancedSlot slot = null;

        if (position < 0)
            return handled;

        slot = slots[position];

        if (slot instanceof TriggerSlot && _container.hasTriggers()) {
            TriggerSlot triggerSlot = (TriggerSlot) slot;

            ITrigger changed = null;
            if (triggerSlot.getTrigger() == null) {

                if (k == 0) {
                    changed = _container.getFirstTrigger();
                } else {
                    changed = _container.getLastTrigger();
                }

            } else {
                Iterator<ITrigger> it = _container.getTriggerIterator(k != 0);

                for (; it.hasNext();) {
                    ITrigger trigger = it.next();

                    if (!it.hasNext()) {
                        changed = null;
                        break;
                    }

                    if (trigger == triggerSlot.getTrigger()) {
                        changed = it.next();
                        break;
                    }
                }
            }

            _container.setTrigger(position, changed, true);
        } else if (slot instanceof ActionSlot) {
            ActionSlot actionSlot = (ActionSlot) slot;

            IAction changed = null;
            if (actionSlot.getAction() == null) {

                if (k == 0) {
                    changed = _container.getFirstAction();
                } else {
                    changed = _container.getLastAction();
                }

            } else {
                Iterator<IAction> it = _container.getActionIterator(k != 0);

                for (; it.hasNext();) {
                    IAction action = it.next();

                    if (!it.hasNext()) {
                        changed = null;
                        break;
                    }

                    if (action == actionSlot.getAction()) {
                        changed = it.next();
                        break;
                    }
                }
            }

            _container.setAction(position - nbEntries, changed, true);
        } else if (slot instanceof TriggerParameterSlot) {
            TriggerSlot trigger = (TriggerSlot) slots[position - nbEntries * 2];

            if (trigger.isDefined() && trigger.getTrigger().hasParameter()) {
                _container.setTriggerParameterFromCarried(position - nbEntries * 2, _container.getCarried(), true);
            }
        }

        return true;
    }
}
