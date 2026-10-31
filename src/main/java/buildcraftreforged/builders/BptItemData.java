/*
 * Buildcraft Reforged, 2026: new file, no 4.2.2 original. 4.2.2 kept a blueprint item's library number in the item
 * damage and its name in the "BptName" NBT tag (BuildCraftBuilders.getBptItemStack); plan section 6 moves both, with
 * the author, into this data component.
 */
package buildcraftreforged.builders;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * What a used Blueprint or Template item carries: the number of its blueprint in the server's library
 * ({@link BuildCraftBuilders#getBptRootIndex}), and the blueprint's name and author for the tooltip. A clean item has
 * no component (4.2.2: damage 0).
 *
 * @param id the library number, 4.2.2's item damage; 1 or more
 * @param name the blueprint's name, 4.2.2's "BptName" tag; empty when it has none
 * @param author the blueprint's author; empty when unknown
 */
public record BptItemData(int id, String name, String author) {
    public static final int MAX_STRING_LENGTH = 256;

    public static final Codec<BptItemData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.intRange(1, Integer.MAX_VALUE).fieldOf("id").forGetter(BptItemData::id),
            Codec.string(0, MAX_STRING_LENGTH).optionalFieldOf("name", "").forGetter(BptItemData::name),
            Codec.string(0, MAX_STRING_LENGTH).optionalFieldOf("author", "").forGetter(BptItemData::author))
            .apply(instance, BptItemData::new));

    public static final StreamCodec<ByteBuf, BptItemData> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, BptItemData::id,
            ByteBufCodecs.stringUtf8(MAX_STRING_LENGTH), BptItemData::name,
            ByteBufCodecs.stringUtf8(MAX_STRING_LENGTH), BptItemData::author,
            BptItemData::new);
}
