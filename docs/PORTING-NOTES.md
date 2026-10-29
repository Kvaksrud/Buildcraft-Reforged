# Porting notes

How 1.6.4 Forge concepts map onto NeoForge 26.x, and the concrete decisions each port makes on top of that. The table
is `docs/PLAN.md` section 6; the log below it records what each port actually chose. Keep both current.

## API mapping (plan section 6)

Rule: port class by class, keeping names, field names, constants and control flow; replace only the platform API
underneath. Transport is the exception and follows `docs/TRANSPORT.md` (plan section 8).

| 1.6.4 Forge concept | NeoForge 26.x replacement | Notes |
|---|---|---|
| Numeric block/item ids, `Configuration` id sections | `DeferredRegister` for blocks, items, block entities, menus, fluids, recipe types | Config id section removed. Registry names are the 4.2.2 unlocalised names, lower-cased (`engine_wood`, `quarry`). |
| Block metadata | `BlockState` properties | Engines: facing. Pipes: waterlogged plus model data. |
| `TileEntity` + `updateEntity()` | `BlockEntity` + `BlockEntityTicker` | Keep the tick cadence and `SafeTimeTracker` logic. |
| `readFromNBT`/`writeToNBT` | the current block-entity save API (`ValueInput`/`ValueOutput` in 1.21.6+; confirm the 26.x form) | Keep the same tag names where the data survives. |
| `IInventory`, `ISidedInventory` | Vanilla `Container`, `WorldlyContainer`; exposed as `ResourceHandler<ItemResource>` via `Capabilities.Item.BLOCK` (26.x has no `IItemHandler`) | Internal `SimpleInventory` port keeps slot semantics; handlers per side exactly as 4.2.2 `ISidedInventory` did. See Inventory (#23). |
| Forge `IFluidHandler`, `FluidStack`, `FluidRegistry` | NeoForge `IFluidHandler` capability, `FluidStack`, `Fluid` + `FluidType` + `LiquidBlock` + bucket | Oil and Fuel registered here; mB values unchanged. |
| `PowerHandler`, `IPowerReceptor`, `IPowerEmitter` | Ported `PowerHandler` holding FE, exposed as `IEnergyStorage` via `Capabilities.Energy.BLOCK` | Section 7. |
| `Packet`, `PacketPayload`, `@TileNetworkData` | `CustomPacketPayload` + `StreamCodec`, one channel | Explicit codecs instead of the reflection-based payload builder. |
| `GuiContainer` / `Container` / `IGuiHandler` | `AbstractContainerScreen` / `AbstractContainerMenu` / `MenuType` + `IMenuProvider` | 31 GUIs. Same textures, same slot positions, same ledgers. Status panel added as a shared ledger (section 10). |
| `ISimpleBlockRenderingHandler`, `TileEntitySpecialRenderer` | JSON models + blockstates for static blocks; `BlockEntityRenderer` for engines, quarry arm, lasers, markers, refinery, tank fluid; pipes per section 8 | Keep the 16× textures. |
| Animated textures | `.png.mcmeta` | Oil, fuel, laser, engine chamber. |
| Java recipe registration | `data/buildcraftreforged/recipe/*.json` via datagen; custom `RecipeType` + serializer for Assembly Table and Refinery | Assembly recipes are JSON with energy cost in FE. |
| Ore Dictionary | Common tags (`c:ingots/iron` etc.) **only in the recipes where 4.2.2 used the Ore Dictionary** (core recipes, assembly recipes, Advanced Crafting Table matching); exact vanilla items everywhere else | Decided 29 Oct 2026. |
| `BuildCraftAPI` fuels, `IronEngineFuel`, `IronEngineCoolant` | Ported registries, data-driven via tags or JSON where easy | Coolant: water and ice as in 4.2.2. |
| `BiomeGenOilDesert`, `BiomeGenOilOcean`, `GenLayerBiomeReplacer`, `OilPopulate` | Placed feature with region-scale density inside vanilla desert and ocean biomes; spouts as in 4.2.2 | Decided 29 Oct 2026. Same look in the world, no new biome ids, no dependency. |
| `ForgeChunkManager` (quarry) | NeoForge chunk-loading `TicketController` | Quarry only, as in 4.2.2. |
| No fake player in 4.2.2 | `FakePlayerFactory` with the **owner's game profile**, recorded when the block is placed; block breaking goes through `BlockEvent.BreakEvent` and placement through the standard checks | Decided 29 Oct 2026. Claim mods and team mods (FTB Chunks, FTB Teams and similar) see the owner, so a player's own quarry works inside their team's claim and stops at anyone else's. Owner shown in the status panel. |
| `Configuration` | `ModConfigSpec` (common + client) | Same option names where they survive: `mining.multiplier`, oil generation chances, plus the new `engines.*` and `transport.*` sections. |
| `en_US.properties` | `en_us.json` | Keys unchanged; the separate BuildCraft-Localization repo is out of scope until its licence is checked. |
| Item NBT (gates, blueprints, facades, pipe wire) | Data components | Gate: trigger/action lists; facade: captured `BlockState`; blueprint: library id + author + name. |
| `ItemWrench` | Same, plus `c:tools/wrench` tag in both directions | Decided 29 Oct 2026. Other mods' wrenches rotate our blocks; ours works on theirs. |
| No comparator support in 4.2.2 | `hasAnalogOutputSignal` on tanks, machines with inventories, engines (heat) | Decided 29 Oct 2026. |
| No waterlogging | Pipes and quarry frames are `SimpleWaterloggedBlock` | Decided 29 Oct 2026. |
| NEI plugins (external, 1.6.4) | JEI plugin (EMI reads JEI plugins) for assembly, refinery, gate and module recipes | Decided 29 Oct 2026. Optional dependency, no gameplay effect. |

When a port changes a row (a different replacement, a confirmed 26.x API form), edit the row here and in
`docs/PLAN.md` section 6 in the same pull request, and add a log entry saying why.

## Log

Each port appends one `##` section per topic below this one, newest last, titled with the topic and the issue number
(`## Registries (#18)`). Write what was decided and why, in bullets, naming the classes involved. Record only choices
a later port needs to know; bug fixes go in `docs/BUGFIXES.md`, parity ticks in `docs/PARITY.md`.

## Registries (#18)

- **One registry class per module package**: `core/CoreRegistry`, `energy/EnergyRegistry`, `factory/FactoryRegistry`,
  `silicon/SiliconRegistry`, `transport/TransportRegistry`, `builders/BuildersRegistry`. Each is the registration half
  of the 4.2.2 module class (`BuildCraftCore`, `BuildCraftEnergy`, ...). Content for a module goes in that module's
  class only; `BuildcraftReforged` calls each class's `register(modBus)` and needs no change when entries are added.
- **Headers**: #1 is decided; the 29 old-header files are MMPL. A registry class that ports entries from its 4.2.2
  module class carries the normalised MMPL header from upstream `360d782ea` (as in `reference/`, see
  `reference/README.md`) plus a "Modified by" line, as `CoreRegistry` does. Add it to the other registry classes when
  their first ported entry lands.
- **`core/ModuleRegisters`** bundles every `DeferredRegister` a module needs, all in the `buildcraftreforged`
  namespace: blocks, items, block entity types, menus, fluids, fluid types, recipe types, recipe serializers, sound
  events, data components, creative tabs. Each registry class owns one instance (`REGISTERS`). NeoForge allows several
  registers for the same registry and namespace, so modules never share a register object. A registry this bundle
  lacks (entity types, attachment types, loot modifiers, ...) is added to `ModuleRegisters` and its `register()`.
- **Field names** are the 4.2.2 static field names (`CoreRegistry.diamondGearItem` for `BuildCraftCore.diamondGearItem`),
  holding a `DeferredItem`/`DeferredBlock`/`DeferredHolder`; call `.get()` where 4.2.2 used the field directly.
- **Registry names** are the 4.2.2 unlocalised names converted from camel case to snake case by
  `RegistryNames.of(...)`: `woodenGearItem` → `wooden_gear_item`, `engineWood` → `engine_wood`,
  `spring.oil` → `spring_oil`. Registry names must be unique across all modules.
- **Translation keys stay the 4.2.2 ones.** Items call `Item.Properties.overrideDescription("item." + unlocalisedName)`
  and blocks `BlockBehaviour.Properties.overrideDescription("tile." + unlocalisedName)`, so
  `item.woodenGearItem` and `tile.engineWood` resolve against the converted `en_us.json`. Metadata variants that 4.2.2
  named with a suffix (`item.pipeGate.0`, `item.redstoneChipset.4`) become separate items or a data component, and
  pick the matching key.
- **Numeric ids and the `Configuration` id section** are gone; `DefaultProps` keeps only the non-id constants.

## Creative tabs (#18)

- `core/CreativeTabBuildCraft` keeps the 4.2.2 enum, `MACHINES` and `FACADES`, registered as
  `buildcraftreforged:machines` and `buildcraftreforged:facades`, titled with the 4.2.2 keys `tab.machines` and
  `tab.facades`. `FACADES` sorts right after `MACHINES`.
- Items join a tab with `CreativeTabBuildCraft.MACHINES.add(holder)` at registration time, in the order they should
  appear. This replaces `setCreativeTab(...)`, which 4.2.2's `ItemBuildCraft` called for every item.
- A tab with no items is hidden by vanilla, so `FACADES` stays invisible until facades exist. Its icon is plain stone
  bricks until `ItemFacade.getStack` is ported.
- The five gears (`BuildCraftCore`'s first items, and the `MACHINES` icon) are registered in `CoreRegistry` as plain
  `Item`s. They switch to `ItemBuildCraft` with #21.

## Localisation (#18)

- `en_US.properties` is converted to `assets/buildcraftreforged/lang/en_us.json` with all 179 keys unchanged and in the
  original order. `\n` escapes become real line breaks. `%d` became `%s` in `chat.pipe.power.iron.mode`, because
  Minecraft's translation formatter accepts only `%s` and `%n$s`.
- `core/utils/Localization` is not ported: `Component.translatable(key)` replaces `Localization.get(key)` on both
  sides, and `I18n.get(key)` where a client-only string is needed.
- Names 4.2.2 also set in code through `LanguageRegistry.addName` / `CoreProxy.addName` (in every module class) are
  dropped; `en_us.json` is the only source of names. Some differ from the properties text, and the properties text
  is what we keep: "Wood Gear" (code: "Wooden Gear"), "Stirling Engine" (code: "Steam Engine"), "Auto Workbench"
  (code: "Automatic Crafting Table"), "Chute" (code: "Hopper"), "Path Marker" (code: "Path Mark"). Parity review
  (P8) can check which one 1.6.4 actually showed.

## Config (#19)

- `core/BuildCraftConfiguration` holds two `ModConfigSpec`s: the common one (`config/buildcraftreforged-common.toml`)
  and the client one (`config/buildcraftreforged-client.toml`). It replaces 4.2.2's single `buildcraft/main.conf` and
  takes over the option reads from `BuildCraftCore`, `BuildCraftEnergy`, `BuildCraftFactory`, `BuildCraftBuilders` and
  `BuildCraftTransport`. Fields are named after the 4.2.2 static fields they were read into
  (`BuildCraftConfiguration.miningMultiplier` for `BuildCraftFactory.miningMultiplier`); a port calls `.get()` where
  4.2.2 read the field. Module ports should not copy the values into static fields, so edits on disk apply.
- FML 12 (NeoForge 26.3) has no `COMMON` type any more: the types are `LOCAL`, `CLIENT`, `SYNCED` and `STARTUP`. The
  common spec is registered as `LOCAL` (loaded at startup on each side, not synced) with the file name
  `buildcraftreforged-common.toml`. `pipes.durability` is block hardness, which the client also predicts; if a server
  value that differs from the client's causes visible break desync, P3 can move it to a `SYNCED` spec.
- **Names stay the 4.2.2 names**, including the typo `mining.cost.multipler`. 4.2.2 put every option in `general`;
  here the dotted name is the TOML path from the root, so `mining.cost.multipler` is key `multipler` in table
  `[mining.cost]` (TOML treats the two spellings as the same key). PLAN.md sections 6 and 19.3 call two options
  `mining.multiplier` and `worldgen.oilWellScalar`; they are `mining.cost.multipler` and `oilWellGenerationRate` here,
  following the "same names" rule. The `worldgen.oil` toggle from section 19.3 is 4.2.2's `modifyWorld`.
- `core/utils/ConfigUtils` is ported as a thin wrapper over the builder with the same `get(tag, ...)` signatures and
  `{t}` comment substitution, returning the spec values. The category argument, the `reset` overload (unused in 4.2.2)
  and the parse helpers are gone: the spec validates, clamps and rewrites bad values itself.
- 4.2.2 left most numbers unbounded. Bounds were added where a negative or zero value would break the code that reads
  it: `itemLifespan` ≥ 100 (4.2.2 clamped in code), `network.*` ≥ 1, the `fuel.*`, `oilWellGenerationRate` and
  `pipes.durability` multipliers ≥ 0, `filler.lifespan.*` ≥ 0.
- Defaults changed by the plan: `itemLifespan` 1200 → 6000 (section 19.3). `pumping.controlList` now uses dimension
  and fluid ids: default `+/*/*,+/minecraft:the_nether/minecraft:lava` (4.2.2: `+/*/*,+/-1/lava`); the
  `PumpDimensionList` port (P4) parses resource locations instead of integers. `woodenPipe.item.exclusion` and
  `woodenPipe.liquid.exclusion` list block ids (`minecraft:furnace`) instead of numeric `id:meta` or unlocalised names,
  and moved from the `block` section to the root.
- New options: `engines.explode` (default true) and `engines.overheatTimerSeconds` (default 300, range 1 to 86400) for
  section 9; `fluids.netherIgnition` (default true, section 19.2); `quarry.loadsChunks` (default false, section 19.3);
  a `transport` section for the P3 tier ladder (`transport.tiers`, section 8). It is defined but empty, and NightConfig
  does not write empty tables, so `[transport]` appears in the file once P3 adds its first value.
- `client.colorblindmode` is in the client file.

### Dropped options

| 4.2.2 option (category) | Read in | Why dropped |
|---|---|---|
| every `*.id` in `block` and `item` (`wrench.id`, `springBlock.id`, the five gear ids, `engine.id`, `oil.id`, `oilStill.id`, `fuel.id`, `bucketOil.id`, `bucketFuel.id`, `miningWell.id`, `drill.id`, `autoWorkbench.id`, `frame.id`, `quarry.id`, `pump.id`, `floodGate.id`, `tank.id`, `refinery.id`, `hopper.id`, `templateItem.id`, `blueprintItem.id`, `marker.id`, `pathMarker.id`, `filler.id`, `builder.id`, `architect.id`, `blueprintLibrary.id`, `laser.id`, `assemblyTable.id`, `redstoneChipset.id`, `pipe.id`, `pipeWaterproof.id`, `redPipeWire.id`, `bluePipeWire.id`, `greenPipeWire.id`, `yellowPipeWire.id`, `pipeGate.id`, `pipeGateAutarchic.id`, `pipeFacade.id`, `pipePlug.id`, `filteredBuffer.id`, one `<pipe name>.id` per pipe) | all five module classes | Numeric ids are gone; registries replace them (PLAN.md section 6). |
| `biomes.oilDesert`, `biomes.oilOcean` | `BuildCraftEnergy` | Oil biomes are replaced by a placed feature in vanilla deserts and oceans; no biome ids (PLAN.md section 6). |
| `update.check` (`general`) | `BuildCraftCore` | `Version.java` and every network call are removed (PLAN.md section 19). |
| `version.seen` (`vars`) | `core/Version` | Same as `update.check`. |
| `version` (`general`) | `BuildCraftConfiguration.save` | Version stamp written on every save; the mod version comes from `gradle.properties`. |
| `power.framework` (`general`) | `BuildCraftCore` | Read but never used in 4.2.2; the power framework is fixed (PLAN.md section 7). |
| `current.continuous` (`general`) | `BuildCraftCore` | Stored in `continuousCurrentModel`, which nothing in 4.2.2 reads. |
| `trackNetworkUsage` (`general`) | `BuildCraftCore` | Counted packet sizes in the reflection-based `ClassMapping`, which explicit `StreamCodec`s replace. |
| `pipes.groupItemsTrigger` (`general`) | `BuildCraftTransport` | Grouped items inside a pipe; the new transport has no in-pipe inventory (PLAN.md section 8). |

## Networking (#20)

- **One channel.** `core/network/PacketHandler.register(modBus)` registers every payload on one `PayloadRegistrar`,
  version `PacketHandler.PROTOCOL_VERSION` (`"1"`; bump it when any codec changes). Payload ids are
  `buildcraftreforged:<name>`. This replaces 4.2.2's `Packet250CustomPayload` channel, the `PacketIds` switch and the
  reflection payload builder (`TileNetworkData`, `ClassMapping`, `TilePacketWrapper`, `PacketPayload*`), none of which
  are ported. Each message is a `CustomPacketPayload` with an explicit `StreamCodec` field `STREAM_CODEC`.
- **Direction is enforced by NeoForge.** Client-bound payloads are registered with `playToClient` and handled in
  `PacketHandlerClient` (`@EventBusSubscriber(value = Dist.CLIENT)`, `RegisterClientPayloadHandlersEvent`); NeoForge
  refuses to decode one that arrives at the server. Server-bound payloads are GUI actions only. Handlers run on the main
  thread.
- **Payloads so far**:

  | Payload | Direction | 4.2.2 | Content |
  |---|---|---|---|
  | `PacketTileState` (`tile_state`) | to client | `PacketTileState`, `STATE_UPDATE` | position, up to 32 `(stateId, bytes)` |
  | `PacketGuiWidget` (`gui_widget`) | to client | `PacketGuiWidget`, `GUI_WIDGET` | container id, widget index, bytes |
  | `PacketGuiReturn` (`gui_return`) | to server | `PacketGuiReturn`, `GUI_RETURN` | container id, position, bytes |

  Each byte payload is at most `PacketData.MAX_PAYLOAD_BYTES` (32767); the codec refuses anything longer. The
  per-module 4.2.2 packets (gate, diamond/emerald pipe, refinery filter, assembly table, advanced workbench, architect,
  library) are ported with their GUIs: each becomes its own payload registered in `PacketHandler`, or rides on
  `PacketGuiReturn` when it only sets block entity fields.
- **Opaque bytes, typed buffers.** A state, widget or GUI return payload is written with
  `PacketData.write(registryAccess, buf -> ...)` into a `RegistryFriendlyByteBuf` (so `ItemStack` and `FluidStack`
  codecs work) and carried as a length-prefixed byte array; the receiver reads it with `PacketData.wrap(...)`. This
  keeps 4.2.2's "the tile writes its own bytes" shape (`IClientState.writeData/readData`,
  `IGuiReturnHandler.writeGuiData/readGuiData`, `Widget.handleClientPacketData`) with `DataOutputStream` and
  `DataInputStream` replaced by the buffer, and one bad state cannot shift the bytes of the next.

### Block entity state sync

- A block entity implements `ISyncedTile` (`getStateInstance(byte)`, `afterStateUpdated(byte)`) and keeps one
  `IClientState` per state id, as `TileGenericPipe` did. To push: `PacketTileState packet = new
  PacketTileState(getBlockPos(), level.registryAccess()); packet.addStateForSerialization((byte) 0, coreState);` then
  `PacketDistributor.sendToPlayersTrackingChunk((ServerLevel) level, new ChunkPos(getBlockPos()), packet)`.
- The client handler checks the position is loaded and the block entity is an `ISyncedTile`; an unknown state id is
  logged and skipped, and an exception while reading is logged, not thrown.
- The one-shot description data that 4.2.2 sent with `PacketTileUpdate` / `getDescriptionPacket` uses vanilla's
  `getUpdateTag` and `ClientboundBlockEntityDataPacket` instead; it needs no payload of ours.

### GUI return and widgets

- `core/gui/BuildCraftContainer` (only its networking half so far) is the base of every Buildcraft menu, as in
  4.2.2. Constructor `(MenuType, containerId, inventorySize)`. It keeps the widget list, `sendWidgetDataToClient`,
  `handleWidgetClientData` and the crafters list; crafters are the players from `PlayerContainerEvent.Open`/`Close`,
  which replace `addCraftingToCrafters`, and `broadcastChanges` calls `Widget.updateWidget(player)` for each.
- `core/gui/widgets/Widget` likewise has only geometry, `initWidget`, `updateWidget` and `handleClientPacketData` so
  far; drawing and mouse handling come with the GUI base classes.
- A menu whose GUI sends settings back overrides `BuildCraftContainer.getGuiReturnHandler()` to return its block
  entity. The block entity implements `IGuiReturnHandler`; `getLevel`, `getBlockPos` and `isRemoved` come from
  `BlockEntity`, so it only writes `writeGuiData` and `readGuiData`. The GUI sends with
  `new PacketGuiReturn(menu.containerId, blockEntity).sendPacket()` (4.2.2: `new PacketGuiReturn(tile).sendPacket()`).
- 4.2.2's dimension id and entity form are dropped: the server uses the sender's own level and open menu, and no
  4.2.2 GUI returned data for an entity.

### Validation of client packets (PLAN.md section 19.2)

Every server-bound packet is checked before anything changes; a packet that fails is logged at WARN
(`Dropped <name> packet from <player>: <reason>`) and dropped, and the sender stays connected.

1. **Size**: the codec refuses byte payloads over 32767 bytes and malformed lengths (NeoForge then drops the
   connection, as for any undecodable packet).
2. **Open menu**: `PacketHandler.checkMenuAccess(sender, containerId, pos)`: the container id is the sender's open
   menu (never 0, the player inventory), the menu is a `BuildCraftContainer` with a return handler, the handler is at
   the packet's position in the sender's level and not removed, and `menu.stillValid(player)` holds.
3. **Distance**: `player.isWithinBlockInteractionRange(pos, 1.0)` and the player is alive.
4. **Value ranges**: `readGuiData` reads every client value through `PacketData.readVarInt/readInt/readByte/
   readBoolean/readEnum/readUtf` with its allowed range (or checks it and throws `InvalidPacketException`), and
   changes nothing until all values are read. Any `InvalidPacketException` or runtime exception from a short or
   garbled buffer drops the packet.

Future server-bound payloads call `checkMenuAccess` and `PacketHandler.reject` the same way.

### Tests

`./gradlew test` runs JUnit 5 through moddev's `unitTest` (junit-fml, game loaded). `PacketCodecTest` round-trips
each codec and checks that truncated, oversized and over-long packets fail to decode; `GuiReturnValidationTest` checks
that a valid return is applied and that each failed check (wrong container, no menu, other position, removed block,
invalid menu, out of reach, out-of-range value, over-long string, truncated or garbage data) is logged and dropped
without touching the block entity. The tests use a `PacketHandler.Sender` stand-in for the player.

## core/utils and api/core (#22)

- **Coordinates stay `int x, y, z`** in `BlockUtil` and `Utils`, as in 4.2.2, so the quarry, filler, pump and builder
  loops port unchanged; the methods build the `BlockPos` inside. Block ids and metadata become `BlockState`: every
  4.2.2 overload taking `int blockID` takes a `BlockState` in the same position.
- **`ForgeDirection` → `Direction`, `UNKNOWN` → `null`.** The ordinals match (DOWN, UP, NORTH, SOUTH, WEST, EAST), so
  `MatrixTranformations.transform`'s ordinal arithmetic is unchanged. `Position.orientation` is `@Nullable`; its
  switches use `case null, default:` where 4.2.2 had `default:`, so a position without orientation still does not move.
- **NBT → `ValueInput`/`ValueOutput`**, the block entity save API: `Position(ValueInput)`/`Position.writeToNBT`,
  `INBTTagable`, and `Utils.writeInvToNBT`/`readInvFromNBT` (an `ItemStackWithSlot` list, the same `Slot` semantics) and
  `readStacksFromNBT`/`writeStacksToNBT` (an `ItemStack.OPTIONAL_CODEC` list, by index). Tag names are the 4.2.2 ones
  (`i`, `j`, `k`). Nest with `output.child(name)` where 4.2.2 nested compounds.
- **Null stacks → `ItemStack.EMPTY`** (`Utils.consumeItem`, `readStacksFromNBT`).
- **`IInventory` → `Container`** in `Utils` only; `getInventory` uses `ChestBlock.getContainer` for double chests. The
  inventory layer itself is #23.
- **`BCLog.logger` is the mod logger** (SLF4J, named `buildcraftreforged`); `BuildcraftReforged.LOGGER` points at it.
  `BCLog.initLog(version)` runs first in the mod constructor.
- **`StringUtils.localize`** is a thin wrapper over `Language.getInstance().getOrDefault(key)`, which reads
  `en_us.json` on the server too (NeoForge's `LanguageHook`) and the selected language on the client. New code should
  prefer `Component.translatable`.
- **`BuildCraftAPI.softBlocks`** is a `Set<Block>`; `LAST_ORIGINAL_BLOCK`/`LAST_ORIGINAL_ITEM` are gone with numeric
  ids. Nothing fills the set yet: 4.2.2 filled it in `BuildCraftCore.postInit` (every fluid block, every `IPlantable`,
  plus snow layer, vine and fire), which belongs to whichever issue ports that lifecycle method.
- **`StackWrapper`** compares item and data components (`ItemStack.isSameItemSameComponents`), with a matching
  `hashCode`. 4.2.2 ignored damage for items without subtypes; damage is a component now, so it always counts.
- **`SafeTimeTracker`** reads `Level.getGameTime()`. The comparison sits in package-private overloads taking the game
  time, so it is unit-tested without a level.
- **`BlockUtil` break path.** `getBuildCraftPlayer(ServerLevel)` stands in for `CoreProxy.getBuildCraftPlayer` and
  returns `FakePlayerFactory.getMinecraft` for now. It and the comment in `breakBlock` are marked `SEAM(#27)`: #27
  returns the owner-profile fake player there and fires `BlockEvent.BreakEvent` before breaking. Drops come from
  `Block.getDrops` with that player as breaker; `breakBlock` still spawns them itself with the 4.2.2 lifespan and
  pick-up delay, so NeoForge's `BlockDropsEvent` does not fire. `itemLifespan` and `dropBrokenBlocks` are read from
  `BuildCraftConfiguration` on each call; the oil check in `canChangeBlock` is `TODO(#42)`.
- **Fluid blocks.** `BlockFluid`/`IFluidBlock` → `LiquidBlock` (vanilla water and lava and NeoForge fluids alike), full
  means `LEVEL == 0`, the fluid is `LiquidBlock.fluid`. `drainBlock` returns a `FluidStack` of `FluidType.BUCKET_VOLUME`
  for any source block; waterlogged blocks are not drained (4.2.2 had none).
- **`explodeBlock`** has no `Explosion` to borrow: it plays the explosion sound, sends one explosion particle and
  `destroyBlock`s with drops (4.2.2 dropped with chance 1/3 through the explosion).
- **`CraftingHelper.findMatchingRecipe(CraftingInput, Level)`** returns the `RecipeHolder<CraftingRecipe>` from the
  server recipe manager, or null (also on the client). Its copy of the 1.6.4 repair handler is gone: vanilla's repair
  recipe is an ordinary recipe in the manager now.
- **Dropped:** `Localization` (#18), `IIconProvider` (icons are
  atlas sprites from models now), `Utils.getItemData` (data components), `Utils.handleBufferedDescription` (description data uses vanilla
  `getUpdateTag`, see Networking (#20)), the `@API` `package-info`. `EnumColor.DYES`
  keeps the Ore Dictionary names.
- **Waiting for their dependencies** (listed at the top of `Utils`): `addToRandomInventoryAround` (#23, #36),
  `addToRandomPipeAround` and `checkPipesConnections` (#34, #48), `checkLegacyPipesConnections` (#67), `createLaser`
  and `createLaserBox` (#29), `preDestroyBlock` (#21, #23).
- **Unit tests** for the maths helpers (`MathUtils`, `MatrixTranformations`, `Position`, `SafeTimeTracker`,
  `RevolvingList`, the pure `Utils` methods) run in the same JUnit setup as #20's tests.

## Base block, block entity and item classes (#21)

- **Ticking.** `BlockBuildCraft.getTicker` returns `BlockBuildCraft::updateEntity` for every block entity type, on both
  sides, every tick: 1.6.4's `updateEntity` cadence. It calls `TileBuildCraft.updateEntity` unless the tile's
  `canUpdate()` (1.6.4's `TileEntity.canUpdate`, now on `TileBuildCraft`) returns false; `TileAssemblyTable` and
  `TileAutoWorkbench` override it as in 4.2.2. `initialize()` still runs on the first tick, and again after
  `setRemoved` (4.2.2 `invalidate`) and `clearRemoved`.
- **Save/load.** `saveAdditional(ValueOutput)` / `loadAdditional(ValueInput)` replace `writeToNBT` / `readFromNBT`
  and keep the tag names (`owner`). A missing tag keeps the field's default, as 4.2.2's `hasKey` checks did.
- **Owner** stays the 4.2.2 string (player name, default `[BuildCraft]`). #27 adds the owner's game profile for the
  fake player next to it.
- **Removal.** 26.x has no `breakBlock` with the block entity still present; vanilla calls
  `BlockEntity.preRemoveSideEffects` on the server before removing it. `TileBuildCraft` overrides it with 4.2.2
  `Utils.preDestroyBlock`: drop the inventory unless an `IDropControlInventory` says no, then `destroy()`. So
  `Utils.preDestroyBlock` is not ported as a static (#22 lists it as waiting on #21; this is its home). `destroy()`
  now runs on the server only; no 4.2.2 `destroy()` does client work. #23 added the `InvUtils` drop.
- **Active-machine light.** 4.2.2 added 8 to the light value while an `IMachine` was active, by reading the tile in
  `getLightValue`. The light engine may not read block entities in 26.x (it runs off-thread and during chunk load),
  so `TileBuildCraft.updateEntity` copies the light into NeoForge's `AuxiliaryLightManager` when `isActive()` changes
  and relights the block; `BlockBuildCraft.getLightEmission` reads it. Machines no longer need their own
  `updateAllLightTypes` calls for this.
- **Hardness.** `setHardness(5F)` becomes `BlockBuildCraft.defaultProperties(props)` = `strength(5F)`: 1.6.4 set
  resistance to hardness × 5 and divided by 5 for explosions, so explosion resistance is 5. Registration passes the
  block's properties through it and adds the block to `CreativeTabBuildCraft.MACHINES`, which 4.2.2's constructor did.
- **Items.** `ItemBuildCraft(Properties)`: icons become item models, the unlocalised name becomes
  `overrideDescription`, and `getItemDisplayName` is dropped because `Item.getName` already translates the same key.
  `shouldPassSneakingClickToBlock` becomes NeoForge's `doesSneakBypassUse`. `ItemBlockBuildCraft` is a plain
  `BlockItem` (no metadata). The five gears are now `ItemBuildCraft`.
- **TileBuffer / ITileBufferHolder.** Block ids become `BlockState`s: `getBlockID` → `getBlockState`, `0` → air,
  `blockCreated(Direction, BlockState, BlockEntity)`. `blockExists` → `isLoaded`, `isInvalid` → `isRemoved`.
  `makeBuffer` uses `Direction.from3DDataValue(i)`, whose order is `ForgeDirection`'s. The 20-tick re-check through
  `SafeTimeTracker` is unchanged.
- **IMachine.allowAction(IAction)** is left out until #31 brings `IAction` (`SEAM(#31)`). `IPowerReceptor`'s
  `getPowerReceiver(null).update()` in `updateEntity` waits for #35 (`SEAM(#35)`).
- **Network sync.** The reflection wrappers (`TilePacketWrapper`, `@TileNetworkData`) are gone (#20). A tile writes
  the fields 4.2.2 marked `@TileNetworkData` itself in `writePacketPayload(RegistryFriendlyByteBuf)` and reads them in
  `readPacketPayload`; `postPacketHandling()` runs after, as in 4.2.2. Description (chunk load) and update
  (`sendNetworkUpdate`) carry the same bytes, as 4.2.2's `PacketTileUpdate` did for both:
  - update: `TileBuildCraft` is an `ISyncedTile`; `sendNetworkUpdate()` sends a `PacketTileState` with the payload as
    state `TileBuildCraft.NETWORK_STATE` (0) to the players tracking the chunk (4.2.2: within
    `NETWORK_UPDATE_RANGE`). A tile with more states uses ids from 1 and calls super in `getStateInstance` /
    `afterStateUpdated`.
  - description: `getUpdateTag` holds only the payload bytes (`networkData`); `handleUpdateTag` and `onDataPacket`
    read them instead of loading the saved data, as 4.2.2's description carried only `@TileNetworkData` fields. A
    tile that needs saved data on the client (none in 4.2.2) overrides these.
- **SafeTimeTracker** is #22's port, copied byte for byte from its branch (with its test) so the two merge cleanly.
- **Tests.** `TileBuildCraftTest` uses a sample tile on the furnace's block entity type (types cannot be registered
  once the game has loaded) to check the tick, `canUpdate`, re-initialize after removal, save/load with the tag names,
  the network payload and the removal path. The test setup is #20's `unitTest` block, copied unchanged.

## Wrench (#28)

- `core/ItemWrench` keeps 4.2.2's `onItemUseFirst`, now NeoForge's `IItemExtension.onItemUseFirst(ItemStack,
  UseOnContext)`, which runs before the block's own use, as in 1.6.4. It returns `SUCCESS` on both sides (the client
  predicts, the server decides); `SUCCESS` swings the arm, which replaces `player.swingItem()`.
  `shouldPassSneakingClickToBlock` is `doesSneakBypassUse`; `setFull3D` is the `minecraft:item/handheld` model parent.
  It extends `ItemBuildCraft`, as in 4.2.2. Registry name `wrench_item`, key `item.wrenchItem`, after the
  gears in the creative tab (4.2.2 id order). The 4.2.2 stack size (64) is unchanged.
- `api/tools/IToolWrench` (this PR, so #34 skips it): `canWrench(Player, InteractionHand, BlockPos)` and
  `wrenchUsed(Player, InteractionHand, BlockPos)`. The hand is new because a player has two.
- **Rotation.** NeoForge has no `Block.rotateBlock(World, x, y, z, ForgeDirection)`. Our blocks that 4.2.2 let the
  wrench rotate implement `core/IRotatableBlock` with their 4.2.2 `rotateBlock` body. Every other block goes through
  `core/RotationHelper.rotateVanillaBlock`, standing in for Forge's 1.6.4 `RotationHelper`: it turns the block's
  facing property around the clicked face's axis (around the vertical axis for horizontal-only facings), skipping
  states that cannot survive, or cycles an axis property; doors, beds, double chests and extended pistons are left
  alone.
- **`c:tools/wrench` in both directions.** Our wrench is in the tag (`data/c/tags/item/tools/wrench.json`), so other
  mods that check the tag accept it. Our blocks check wrenches through `core/WrenchHelper` (`isWrench`, `canWrench`,
  `wrenchUsed`) wherever 4.2.2 tested `instanceof IToolWrench`; it accepts `IToolWrench` items and any item in the tag.
- Recipe `data/buildcraftreforged/recipe/wrench_item.json` is the 4.2.2 one: iron ingots around a stone gear, with
  exact items, as 4.2.2 did not use the Ore Dictionary here.

## JEI (#32)

- Compile-only against `mezz.jei:jei-26.3-common-api` and `jei-26.3-neoforge-api` (`jei_version` in
  `gradle.properties`) from `https://maven.blamejared.com`. JEI is declared as an optional dependency in
  `neoforge.mods.toml`.
- `platform/jei/BuildCraftJeiPlugin` is an empty `@JeiPlugin`. JEI finds it by the annotation and nothing else refers
  to it, so it is never loaded without JEI. Recipe categories arrive with the features they show (#72, #68, #74).
- `./gradlew runClient -Pwith_jei` (or any run task) adds JEI to the dev runs; without the flag they run without it.

## GameTests (#33)

- GameTests live in the `gametest` source set: part of the mod in dev runs, never in the jar. `BuildcraftGameTests`
  registers test functions (`RegisterEvent` on `Registries.TEST_FUNCTION`) and instances (`RegisterGameTestsEvent`)
  in the `buildcraftreforged` namespace. Run them with `./gradlew runGameTestServer`; there is no CI until release.
- The source set also adds an always-on data pack (`gametest_data`) that puts `minecraft:debug_stick` in
  `c:tools/wrench`, standing in for another mod's wrench. Dev runs see it too; the jar does not.
- **No sockets** (`no_sockets`): Java 25 has no SecurityManager, so `runGameTestServer` starts the JVM with a Flight
  Recorder recording (`build.gradle.kts`) that uses Java 25 method tracing on socket connects and binds
  (`sun.nio.ch.Net`), datagram sends and HTTP/FTP URL connections, plus socket read and write events, all with stack
  traces, from JVM start. After server tick 1000 the test reads a snapshot and fails on any event, logging each stack
  trace. It fails, rather than passes, when the recording is missing. Checked by hand: an HTTP connect added to the
  mod constructor makes it fail and names the line. DNS lookups without a connection are not traced.

## Public api packages (#34)

Ported to `buildcraftreforged.api.*`: `recipes`, `fuels`, `tools`, `filler`, `blueprints`, `inventory`, `transport`.
Interfaces, enums, registries and base classes only; implementations land in their phases. `api/core` (#22),
`api/power` (#35) and `api/gates` (#31) are not touched.

- **Headers.** Files with a header in `reference/` keep it plus a "Modified by" line. Files with no header at 4.2.2
  (half of the api package) get only a short "Modified by" comment naming the 4.2.2 file. The FML `@API` package
  annotations have no NeoForge equivalent; each `package-info.java` keeps the 4.2.2 package and API version in javadoc.
- **`api/core` and `api/gates` types.** `IFillerPattern` and `IBptContext` keep `IBox` and `Position` from
  `buildcraftreforged.api.core` (#22), so the filler, builder and blueprint ports in P4/P6 follow their originals.
  `StackWrapper` (item id + damage) is not used: `IronEngineCoolant` keys solid coolants by `Item`, since damage no
  longer selects a variant after the flattening. `IFillerRegistry.getActions()` needs `IAction`; #31 adds it back.
- **Units.** Fuel `powerPerCycle`, `AssemblyRecipe.energy` and the Refinery `energy` are FE (MJ × 10, plan section 7).
  The types stay as 4.2.2 had them (`float`/`int`); callers pass FE values.
- **Fluid names** in `IronEngineFuel`/`IronEngineCoolant` are registry ids (`buildcraftreforged:oil`) where 4.2.2
  used FluidRegistry names (`oil`). `RefineryRecipes` sorts by the same id string. 1.6.4 `FluidStack.containsFluid`
  is a private helper (same fluid and components, amount at least). `null` still means "no fluid"; an empty stack is
  treated as `null`.
- **Ore Dictionary in `AssemblyRecipe`.** The `(String oreName, Integer count)` pair became `(TagKey<Item>, Integer)`;
  matching uses `ItemStack.is(tag)`. Exact stacks use `ItemStack.isSameItem` (1.6.4 `isItemEqual` ignored NBT too).
  `assemblyRecipes` and `RefineryRecipes` stay the in-memory lookups; the silicon/factory phases fill them from the
  JSON recipe types of plan section 6.
- **Removed numeric-id overloads**: `IronEngineCoolant.addCoolant(int, int, FluidStack)`.
- **Tools.** `IToolWrench` is #28's version (`(Player, InteractionHand, BlockPos)`, see "Wrench" above); this port
  added only `IToolPipette`.
- **Filler.** `IFillerPattern.createPatternIterator(BlockEntity, IBox, Direction)`. `getIcon()` returns the
  sprite `Identifier` instead of a client-only `Icon`, so the interface loads on a dedicated server; `getDisplayName()`
  returns a `Component`.
- **Blueprints (plan section 13).** `BptSlotInfo` holds a `BlockState` (`state`) for `blockId` + `meta`, a
  `CompoundTag cpt`, and gains `pos()`. `IBptContext` loses `mapItemStack`, `mapWorldId` and `storeId` (id
  translation). `BptBlock` is keyed by `Block`; its default `rotateLeft` now rotates the block state clockwise (the
  direction 4.2.2's `BptBlockRotateMeta` tables turned), `isValid` compares the whole state, block entity data goes
  through `saveWithFullMetadata`/`loadWithComponents`, requirements skip a block with no item, and `useItem` leaves
  the container-item swap to the caller because a stack's item is final. `getSignature`/`match` are gone.
  `BlueprintManager.blockBptProps` is a `Map<Block, BptBlock>` with `getBptBlock(block)` creating the default on first
  use (4.2.2 prefilled an id-indexed array). `BlockSignature` and `ItemSignature` are kept unchanged for the 1.6.4
  importer (P9). The 4.2.2 `@Deprecated` marks on these classes are dropped: they announced 5.0's blueprint API, and
  these classes are the live API here.
- **Not ported here:** the 18 `bptblocks` schematics and `BptBlockUtils` (builders phase, P6); `PipeManager` and
  `FacadeManager` (P3).
- **Section 8 markers.** Transport and inventory types carry a "Plan section 8" paragraph saying what is expected to
  survive P3: `PipeType`, `WireColor`, `IPipeConnection`, `ISolidSideTile` and `injectItem` (routing instantly) stay;
  `IPipe.DrawingState`, `IPipeTile.getPipe`, `ISpecialInventory` and `ISelectiveInventory` are not expected to;
  `ISecuredInventory` and `IExtractionHandler` are P3's call. `IPipeTile` no longer extends a fluid handler; pipes
  expose `Capabilities.Fluid.BLOCK`.

## Inventory (#23)

- **`IInventory` → `Container`, `ISidedInventory` → `WorldlyContainer`.** Every class in `core/inventory` keeps its
  name, fields and control flow on the vanilla interfaces: `getSizeInventory` → `getContainerSize`, `getStackInSlot`
  → `getItem`, `decrStackSize` → `removeItem`, `getStackInSlotOnClosing` → `removeItemNoUpdate`,
  `setInventorySlotContents` → `setItem`, `getInventoryStackLimit` → `getMaxStackSize`, `onInventoryChanged` →
  `setChanged`, `isUseableByPlayer` → `stillValid`, `isItemValidForSlot` → `canPlaceItem`; `getAccessibleSlotsFromSide`
  → `getSlotsForFace`, `canInsertItem` → `canPlaceItemThroughFace`, `canExtractItem` → `canTakeItemThroughFace`.
  `openChest`/`closeChest` become `startOpen`/`stopOpen` where 4.2.2 forwarded them; the name methods are gone
  (`SimpleInventory.getInvName` stays as a plain method). `isEmpty` and `clearContent` are new and implemented.
- **Null stacks → `ItemStack.EMPTY`** throughout; `ITransactor.remove` and `InvUtils.moveOneItem` return `EMPTY`
  for "nothing". `Transactor.add` still returns a copy whose count is the amount moved (count 0 reads as empty).
- **Sides.** `ForgeDirection` → `Direction`, `UNKNOWN` → `null`. A null side reaches every slot of a
  `WorldlyContainer` (NeoForge's unsided convention); 4.2.2 passed `UNKNOWN`'s ordinal 6 to
  `getAccessibleSlotsFromSide`. #34's `api/inventory` interfaces (`ISpecialInventory`, ...) take a plain `Direction`;
  `TransactorSpecial` passes null through for `UNKNOWN`.
- **Item capability.** NeoForge 26.x replaced `IItemHandler` with `ResourceHandler<ItemResource>` and transactions.
  `InvUtils.getItemHandler(inv, side)` gives other blocks the 4.2.2 `ISidedInventory` view: a `WorldlyContainer` is
  wrapped per side in NeoForge's `WorldlyContainerWrapper` (slots of `getSlotsForFace`, `canPlaceItemThroughFace`,
  `canTakeItemThroughFace`); a plain `Container` in `VanillaContainerWrapper` (every slot from every side, checks
  `canPlaceItem`), as 4.2.2 treated a plain `IInventory`. Stack limits come from `Container.getMaxStackSize(stack)`.
  - **For machine ports:** a tile that held items in 4.2.2 implements `Container` (or `WorldlyContainer` if it was
    sided), usually by delegating to a `SimpleInventory` field, and its module's `RegisterCapabilitiesEvent` listener
    calls `TileBuildCraft.registerItemHandler(event, type)`. `TileBuildCraft.getItemHandler(side)` can be overridden.
    No module registers one yet.
- **Drops.** `TileBuildCraft.preRemoveSideEffects` now does 4.2.2 `Utils.preDestroyBlock`'s drop:
  `InvUtils.dropItems` (4.2.2's scatter and 10-tick pick-up delay) then `wipeInventory`, for any tile that is a
  `Container`, unless `IDropControlInventory.doDrop()` says no. It no longer calls vanilla's
  `Containers.dropContents`.
- **`TransactorHandler`** (new, no 4.2.2 counterpart): an `ITransactor` over an item `ResourceHandler`, so machines can
  push into or pull from blocks that expose only the capability (most modded blocks now). `getTransactorFor` returns
  it for a `ResourceHandler`. Adds use `ResourceHandlerUtil.insertStacking` (matching stacks first, as
  `TransactorSimple`); `doAdd`/`doRemove` false roll the transaction back. Callers look the handler up with the side
  themselves; the `orientation` argument is unused.
- **`StackHelper`.** `canStacksMerge` is `ItemStack.isSameItemSameComponents` (1.6.4 `isItemEqual` + tag equality;
  damage is a component, so damaged tools still do not merge). `isMatchingItem` compares the item and, with
  `matchNBT`, the component patch minus `DAMAGE`, with 4.2.2's asymmetry (a base without data matches any data).
  `matchDamage` has nothing left to compare: no item has subtypes now, and 4.2.2 ignored damage for items without.
  Wildcards stay as constants (`WILDCARD_VALUE`) but no stack carries one.
- **Ore Dictionary → item tags.** `isCraftingEquivalent(base, comparison, true)` accepts a comparison sharing one of
  base's `c` tags whose path has a slash (`c:ingots/iron`, not `c:ingots`): the counterpart of an Ore Dictionary
  name. `isCraftingEquivalent(oreID, comparison)` takes a nullable `TagKey<Item>` (4.2.2: an int, -1 for none) for
  `TileAdvancedCraftingTable`. `CraftingFilter` uses the first.
- **`StackFilter.FUEL`** is `stack.has(DataComponents.COOKING_FUEL)`, the furnace's own fuel test in 26.x (fuel became
  a data component).
- **`SimpleInventory` save** keeps the `items` list; entries are `ItemStackWithSlot` (`Slot` byte, as 4.2.2). 4.2.2
  also read an int `index` written by older versions; there are no such saves.
- **`api/inventory`** came from #34 already; this port uses it unchanged.
- **Tests.** `TransactorTest` (simple, round-robin, handler transactors; `moveOneItem`), `SidedAccessTest` (sided
  iterator, transactor and capability on a crafting-machine-shaped `WorldlyContainer`; plain inventories from every
  side) and `InventoryTest` (`SimpleInventory` slots and save, mapper, concatenator, copy, `StackHelper`). The unit
  test setup loads no registries, so items have no data components and no `ItemStack` of a real item can be made;
  the tests' `ItemComponents` binds a minimal component map (stack size, damage) to the five items they use. Binding
  vanilla's full set needs bound tags and fails.
