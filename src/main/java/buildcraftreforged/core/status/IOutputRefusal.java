/*
 * Buildcraft Reforged, 2026: new file, no 4.2.2 original. Blocked output (#63, docs/TRANSPORT.md 6.3).
 */
package buildcraftreforged.core.status;

import net.neoforged.neoforge.transfer.item.ItemResource;

/**
 * An item handler that can say why it takes nothing, for {@link MachineOutput}'s Reason line. Item pipes implement it
 * (their handler routes through a network, so "full" and "nothing to route to" differ); a plain inventory that refuses
 * is full.
 */
public interface IOutputRefusal {

    /**
     * Why an insert of {@code resource} moves nothing now: {@link StatusReason#OUTPUT_FULL} when something connected
     * could take it but is full or saturated, {@link StatusReason#NO_DESTINATION} when nothing connected takes items at
     * all. Called outside any transaction, only after an insert moved nothing; changes nothing.
     */
    StatusReason whyRefused(ItemResource resource);
}
