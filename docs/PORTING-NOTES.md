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
| `IInventory`, `ISidedInventory` | `IItemHandler` via `Capabilities.Item.BLOCK` | Internal `SimpleInventory` port keeps slot semantics; expose handlers per side exactly as 4.2.2 `ISidedInventory` did. |
| Forge `IFluidHandler`, `FluidStack`, `FluidRegistry` | NeoForge transfer API: `ResourceHandler<FluidResource>` via `Capabilities.Fluid.BLOCK`, `FluidStack`, `Fluid` + `FluidType` + `LiquidBlock` + bucket | Oil and Fuel registered here; mB values unchanged. 26.3 has no `IFluidHandler` (confirmed in #24). |

| `IInventory`, `ISidedInventory` | Vanilla `Container`, `WorldlyContainer`; exposed as `ResourceHandler<ItemResource>` via `Capabilities.Item.BLOCK` (26.x has no `IItemHandler`) | Internal `SimpleInventory` port keeps slot semantics; handlers per side exactly as 4.2.2 `ISidedInventory` did. See Inventory (#23). |
| Forge `IFluidHandler`, `FluidStack`, `FluidRegistry` | NeoForge `IFluidHandler` capability, `FluidStack`, `Fluid` + `FluidType` + `LiquidBlock` + bucket | Oil and Fuel registered here; mB values unchanged. |
| `PowerHandler`, `IPowerReceptor`, `IPowerEmitter` | Ported `PowerHandler` holding FE, exposed as NeoForge's `EnergyHandler` (26.x's transaction-based replacement for `IEnergyStorage`) via `Capabilities.Energy.BLOCK` | Section 7. Adapter `api/power/PowerEnergyHandler` (#35). |
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
  | `PacketStatusWatch` (`status_watch`, #26) | to server | new | container id, watching |

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

- `core/gui/BuildCraftContainer` (the networking half; #25 ported the rest) is the base of every Buildcraft menu, as in
  4.2.2. Constructor `(MenuType, containerId, inventorySize)`. It keeps the widget list, `sendWidgetDataToClient`,
  `handleWidgetClientData` and the crafters list; crafters are the players from `PlayerContainerEvent.Open`/`Close`,
  which replace `addCraftingToCrafters`, and `broadcastChanges` calls `Widget.updateWidget(player)` for each.
- `core/gui/widgets/Widget` likewise had only geometry, `initWidget`, `updateWidget` and `handleClientPacketData`;
  #25 added drawing, mouse handling and tool tips.
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

## Fluid tanks (#24)

- **26.3 has no `IFluidHandler` or `FluidTank`.** NeoForge's fluid capability is the transfer API:
  `Capabilities.Fluid.BLOCK` gives a `ResourceHandler<FluidResource>` with indexed slots and transactions. The API
  mapping row now says so (here and in `docs/PLAN.md` section 6).
- **`core/fluids/Tank`** carries Forge 1.6.4 `FluidTank`'s fields (`fluid`, `capacity`, `tile`) and its `fill(FluidStack,
  boolean)` / `drain(int, boolean)` logic, so the 4.2.2 machine code calls them unchanged. An empty tank holds
  `FluidStack.EMPTY` where 1.6.4 held null; `drain` returns `EMPTY` for nothing. It is also a one-slot
  `ResourceHandler<FluidResource>`: `insert`/`extract` simulate with `fill`/`drain(..., false)`, then journal and
  apply. Rollback goes through NeoForge's `SnapshotJournal` (`Tank` extends it; `SingleUseTank` adds its
  `acceptedFluid` to the snapshot). A change marks the tile changed (1.6.4 posted a `FluidEvent` instead), on the
  root commit when inside a transaction.
- **`TankManager<T extends Tank>`** is still a `ForwardingList<T>` and implements `ResourceHandler<FluidResource>`
  with one index per tank, so a machine exposes it directly:
  `event.registerBlockEntity(Capabilities.Fluid.BLOCK, TYPE, (tile, side) -> tile.tankManager)`. The whole-handler
  `insert`/`extract` keep 4.2.2's order: the first tank that takes (or gives) any fluid is the only one used. The 4.2.2
  `fill(Direction, ...)`, `drain(Direction, ...)`, `canFill`, `canDrain` stay as plain methods for the machines'
  own code; `getTankInfo` is gone with `FluidTankInfo` (read the handler's slots instead, as #31's
  `TriggerFluidContainer` should).
- **`RestrictedTank` / `SingleUseTank`** also answer `isValid` from their accepted fluids.
- **Tag names** are 4.2.2's: a compound per tank named after it (`tank`, `tank1`, `tankFuel`, ...), holding Forge
  1.6.4 `FluidTank`/`FluidStack` tags `FluidName`, `Amount`, `Tag` (now the components patch) or `Empty`, plus
  `acceptedFluid` for `SingleUseTank`. `FluidName` is the registry id (`minecraft:water`); a name without a namespace
  (1.6.4's `water`, `oil`) is looked up in `minecraft`, then `buildcraftreforged` (`FluidUtils.getFluid`).
- **Sync.** `TankManager.writeData`/`readData` take a `RegistryFriendlyByteBuf`: per tank, the
  `FluidStack.OPTIONAL_STREAM_CODEC`. Call them from the tile's `writePacketPayload`/`readPacketPayload` (#21), as
  4.2.2's `TileTank` did from its payload. 4.2.2 also sent the fluid colour from the server; fluid colours are client
  data now, so `readData` fills `Tank.colorRenderCache` from `FluidRenderer.getColorForFluidStack` (white until the
  client has fluid models). Never call `readData` on a dedicated server.
- **`FluidUtils`.** `handleRightClick(handler, player, hand, fill, drain)`: the side argument is gone (the handler is
  already sided), the hand is new; containers go through `ItemAccess.forPlayerInteraction(...).oneByOne()` and the
  item fluid capability, which also covers creative mode, so buckets and any modded container work.
  `getFluidBlockId` → `getFluidBlock(Fluid, moving)`, the source `BlockState` (one `LiquidBlock` per fluid; its flow
  is scheduled on placement). `pushFluidToConsumers(Tank, flowCap, TileBuffer[])` looks up each neighbour's
  `Capabilities.Fluid.BLOCK` and moves with `ResourceHandlerUtil.move`.
- **`BCFluid` is a `FluidType`**: 4.2.2's only addition to Forge's `Fluid` was the name key `fluid.<name>`, and names
  live on `FluidType` now. #44 builds oil and fuel on it.
- **`core/render/FluidRenderer`** (client only): sprites come from the fluid's `FluidModel` in the block atlas
  (the missing model stands in for "missingno"); `setColorForFluidStack` → `getColorForFluidStack`, returning RGB from
  the model's `FluidTintSource`; the display lists → `renderFluid(stack, stage, flowing, pose, buffer, color, light,
  overlay)`, which writes the same 0.01–0.99 cuboid, `stage / DISPLAY_STAGES` high, into a `VertexConsumer` for a
  `BlockEntityRenderer`. Nothing renders with it yet (#64 tank, refinery); it compiles but is not checked in game.
- **Tooltip.** `Tank.getToolTip`/`refreshTooltip` wait for `core/gui/tooltips` (`SEAM(#25)`).
- **Tests.** `TankManagerTest` (JUnit) covers fill/drain order, the indexed handler, commit/abort/nested
  transactions, `SingleUseTank` and `RestrictedTank`, save/load with the tag names, the namespace-less name, and the
  sync round trip. JUnit loads no data packs, so it binds empty default components on the fluids first. The
  GameTests `tank_fill_and_drain` and `tank_bucket_and_save` use a `test_tank` block and block entity registered only
  in the `gametest` source set, exposed through `Capabilities.Fluid.BLOCK`.

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

## Triggers and actions (#31)

- **Registries replace numeric ids.** `api/gates/ActionManager` holds two NeoForge registries,
  `buildcraftreforged:trigger` (`TRIGGER_REGISTRY`) and `buildcraftreforged:action` (`ACTION_REGISTRY`), synced to
  clients. `CoreRegistry.register` adds them in `NewRegistryEvent`. Every `ModuleRegisters` has `triggers` and
  `actions` registers, so energy (engine heat, Engine Overheating) and transport (pipe triggers and actions) add their
  own in their registry class. Entries are named after the 4.2.2 `BuildCraft*` field with `RegistryNames.of`
  (`triggerMachineActive` → `trigger_machine_active`, `actionOn` → `action_on`) and held as
  `DeferredHolder<ITrigger, BCTrigger>`; call `.get()` where 4.2.2 used the field.
- **Legacy ids are gone**: `getLegacyId`, the `int legacyId` constructor arguments, `getTriggerFromLegacyId` and the
  `DefaultProps.TRIGGER_*`/`ACTION_*` constants. The 4.2.2 unique tags stay (`getUniqueTag`, the
  `ActionManager.triggers`/`actions` maps, filled by the `BCTrigger`/`BCAction` constructors as in 4.2.2, with the
  `buildcraft.filteredBuffer.*` alias), so the P9 importer can map 4.2.2 gate saves. New saves and packets use
  `ActionManager.TRIGGER_CODEC`/`ACTION_CODEC` and the `*_STREAM_CODEC`s (registry ids).
- **Parameters.** `TriggerParameter` holds `ItemStack.EMPTY` instead of null and keeps one item. `writeToNBT`/
  `readFromNBT` take `ValueOutput`/`ValueInput` under the 4.2.2 key `stack`; the numeric `itemID` branch is gone.
  `TriggerParameter.CODEC`/`STREAM_CODEC` serialise it for data components. 4.2.2 never kept triggers in item NBT
  (gates saved them in the pipe's tile data and lost them when removed), so no component is registered here: #60 puts
  the gate's trigger, parameter and action lists in a component on the gate item if it keeps them, built from these
  codecs. What 1.6.4 kept in the parameter stack's NBT is now its data components, compared with item and components.
- **Signatures.** `isTriggerActive(@Nullable Direction side, BlockEntity tile, @Nullable ITriggerParameter)`; `side`
  is the tile's side facing the gate (4.2.2 passed `o.getOpposite()`), null for 4.2.2's `UNKNOWN`. Providers take
  `(Block, @Nullable BlockEntity)`.
- **Inventory and fluid triggers read capabilities.** 4.2.2's `instanceof IInventory` / `IFluidHandler` became
  `Capabilities.Item.BLOCK` / `Capabilities.Fluid.BLOCK` lookups on the tile's side (`BCTrigger.getCapability`), so
  vanilla and other mods' blocks count too. The slot walk is 4.2.2's `InventoryIterator` walk over the handler's
  indices: `canPutStackInSlot` → `isValid`, `canStacksMerge` → same item and components, `getMaxStackSize` of the slot
  → `getCapacityAsInt(slot, resource)`. Tanks are handler indices; `fill(simulate)` is an insert of 1 mB in a
  transaction that is never committed; `FluidContainerRegistry.getFluidForFilledItem` → `FluidUtil.getFirstStackContained`.
  `ISpecialInventory` is still checked first, as in 4.2.2. #23 may swap the walk for its `InventoryIterator` port; the
  semantics must stay.
- **Machine trigger, blocked state (plan 8.3).** `TriggerMachine`'s boolean became `State { Active, Inactive, Blocked }`
  (tags `buildcraft.work.scheduled`, `.done`, and the new `.blocked`; registry `trigger_machine_blocked`).
  `IMachine.isBlocked()` is a default method returning false; a machine whose output is refused overrides it. While
  blocked, "Work Done" is false: the machine still has work. `DefaultTriggerProvider` offers all three on an `IMachine`.
- **`IMachine.allowAction(IAction)`** is back (resolves #21's `SEAM(#31)`); every machine implements it as in 4.2.2.
- **Icons** are GUI sprites: `getIcon()` returns an `Identifier` in the GUI atlas, `buildcraftreforged:triggers/<4.2.2
  icon name>`, from `ActionTriggerIconProvider` (same index constants, plus `Trigger_Machine_Blocked` = 36). The
  4.2.2 PNGs it registered are copied to `textures/gui/sprites/triggers/`. `registerIcons` and `IIconProvider` are
  gone. The blocked icon has no art yet (plan section 8.6 art list).
- **Descriptions** stay the 4.2.2 English strings from `getDescription()`; #60 may switch the gate GUI to
  translation keys.
- **Empty parameter.** `TriggerInventoryLevel` treats a parameter without a stack as no parameter (false); with null
  stacks gone this is the 4.2.2 null check, which would otherwise have thrown.
- **`IFillerRegistry.getActions()`** is back (left out by #34).
- **Tests.** GameTests `trigger_registries`, `trigger_machine`, `trigger_inventory`, `trigger_fluid` use a test-only
  block (`gametest_trigger_block`, registered by the gametest source set, not in the jar): an `IMachine` with two item
  slots and a 4000 mB tank as capabilities. The inventory test also reads a vanilla chest.

## Markers and path markers (#29, #30)

- **Package and names.** The markers stay in `builders` as in 4.2.2: `BlockMarker`, `TileMarker`, `BlockPathMarker`,
  `TilePathMarker`, `EventHandlerBuilders`, registered in `BuildersRegistry` as `marker_block` and
  `path_marker_block` (block entity types `marker` and `path_marker`). `Box`, `BlockIndex`, `DefaultAreaProvider`,
  `EntityBlock` and `core/render/RenderEntityBlock` are in `core`.
- **`EntityBlock` is render data, not an entity.** 4.2.2 spawned an `EntityBlock` for every laser
  (`Utils.createLaser`/`createLaserBox`) and an `EntityPowerLaser` between linked path markers. Here
  `Utils.createLaser` returns an unspawned `EntityBlock` (position, size, texture `Identifier`, brightness 210) that
  the calling block entity keeps, and its `BlockEntityRenderer` draws it through `RenderEntityBlock.doRenderBlock`
  inside `submitCustomGeometry`. `setDead()` only marks it. `Box.getLasers()` and `TileMarker.getLasers()`/
  `getSignals()` hand the arrays to a renderer. Later ports that made `EntityBlock`s (filler, builder, quarry arm)
  should do the same.
- **`builders/render/RenderMarker`** (new) is the renderer for both marker types: the red box and blue signal lasers,
  and for path markers the link laser with `RenderLaser`'s 16x1x1 model box and `textures/entity/laser_1.png`, drawn
  once by the marker at the lower position. It renders off screen with a render box of the marker inflated by 64.
  `core/render/RenderLaser` itself is not ported yet.
- **`RenderingMarkers` becomes JSON models.** `models/block/template_marker.json` has the 1.6.4 marker shape (two
  crossed full-texture planes and the 2x2 cap at 9/16); the blockstates rotate it per `FACING` as vanilla's end rod.
- **Metadata → `BlockMarker.FACING`** (the clicked face). `canPlaceBlockOnSide`/`BuildersProxy.canPlaceTorch`
  ("renderAsNormalBlock") is `canSurvive`: the block behind must be `isSolidRender()`. The 4.2.2 neighbour check and
  drop stay in `neighborChanged`/`onPlace`.
- **Material.circuits** becomes no collision, instant break, light 7 (`setLightValue(0.5F)`), `PushReaction.POPPED`.
  **Not waterloggable**: 1.6.4 water washed a marker off like a torch, and a block without collision does the same.
- **Path marker texture.** `getBlockTexture`'s active icon is `BlockPathMarker.ACTIVE`, which the server keeps equal
  to `tryingToConnect` (4.2.2 re-rendered on the client when it changed). The cap always uses the active texture.
- **Sync.** `TileMarker` writes `origin` (vectO, vect[3], min/max) then `showSignals`; `TilePathMarker` adds
  `tryingToConnect` and the two link positions, which replace the tracked laser entity. The client rebuilds its
  lasers in `postPacketHandling` as 4.2.2 did.
- **Removal versus unload.** 4.2.2's `invalidate` → `destroy` ran on block removal only; 26.x `setRemoved` also runs on
  chunk unload, so the unlinking lives in `destroy()` (from `preRemoveSideEffects`) and `onChunkUnloaded` only drops a
  path marker from the available list.
- **Save.** Tag names are 4.2.2's (`vectO`, `vect0..2` with `i/j/k`; `x0..z1`). 26.x does not tick block entities in
  border chunks, so a marker loaded but not yet initialised writes back what it loaded instead of dropping the box or
  link. `initVectO` is cleared after linking, so a second `initialize` (after `clearRemoved`) does not relink.
- **Textures** are the 4.2.2 files renamed: `block/marker_block`, `block/path_marker_block(_active)`,
  `block/red_laser`, `block/blue_laser`, `block/stripes_laser`, `entity/laser_1`.
- **Recipe**: lapis lazuli over a redstone torch, as 4.2.2. The path marker recipe was commented out in 4.2.2, so the
  path marker stays creative-only.

## GUI base classes and ledgers (#25)

- **Ported:** `core/gui/BuildCraftContainer` (now complete), `GuiBuildCraft` with `LedgerManager` and `Ledger`,
  `GuiAdvancedInterface`, `GuiTools`, `core/GuiIds`; and what they need that no other issue owns: `tooltips/*`,
  `slots/` (`IPhantomSlot`, `SlotBase`, `SlotPhantom`, `SlotUntouchable`, `SlotLimited`, `SlotOutput`), `widgets/`
  (`Widget` completed, `ButtonWidget`, `IndicatorWidget`, `IIndicatorController`, `IndicatorController`) and
  `buttons/` (`GuiBetterButton`, `IButtonTextureSet`, `ButtonTextureSet`, `StandardButtonTextureSets`). Textures:
  `textures/gui/ledger.png`, `textures/gui/buttons.png`, and the ledger icon `icons/energy` as a GUI sprite.
- **Not ported yet:** `SlotValidated` and `SlotWorkbench` (Railcraft licence header on `SlotValidated`; `SlotWorkbench`
  needs crafting), `FluidGaugeWidget` (#24's fluid rendering), the other `buttons/` classes (`GuiButtonSmall`,
  `GuiToggleButton*`, `GuiMultiButton`, `MultiButtonController`, `IMultiButtonState`, `LockButtonState`). The first GUI
  that uses one ports it here.
- **Screens are generic in their menu:** `GuiBuildCraft<T extends BuildCraftContainer>`, because `MenuScreens` pairs a
  menu type with a screen of that menu. `container` (typed `T`), `tile` and `texture` keep their 4.2.2 names. The
  constructor is `(menu, inventory, texture, playerInventory, title[, xSize, ySize])`: 26.x fixes the size in the
  constructor (`imageWidth`/`imageHeight` are final), where 4.2.2 GUIs assigned `xSize`/`ySize` afterwards. `inventory`
  is what 4.2.2 passed as the `IInventory` (usually the block entity) and is handed to `initLedgers`.
- **Drawing keeps the 1.6.4 calls.** 26.x renders through a `GuiGraphicsExtractor` per frame instead of GL state.
  `GuiBuildCraft` stores it in `graphics` at the start of each pass, and offers the 1.6.4 calls on top:
  `bindTexture`, `setColor` (`GL11.glColor4f`), `drawTexturedModalRect` (256 by 256 textures),
  `drawGradientRect`, `drawString`/`drawStringWithShadow` (`fontRenderer.drawString...`; colours without alpha are
  drawn opaque, as 1.6.4 did, which 26.x does not do by itself). Ported GUIs keep their drawing code and override
  `drawGuiContainerBackgroundLayer(float, int, int)` and `drawGuiContainerForegroundLayer(int, int)` as before. The
  background runs from `extractBackground`; the foreground runs after the slots, translated to the GUI corner, as in
  1.6.4. Vanilla's title and "Inventory" labels are not drawn: 4.2.2 GUIs draw their own in the foreground.
  `xSize`/`ySize` → `imageWidth`/`imageHeight`, `guiLeft`/`guiTop` → `leftPos`/`topPos`, `fontRenderer` → `font`,
  `mc` → `minecraft`, `zLevel = 300` → `graphics.nextStratum()`.
- **Mouse buttons.** 26.x numbers them 1 left, 2 middle, 3 right; widgets, ledgers and `slotClicked` keep the 1.6.4
  numbers (0 left, 1 right, 2 middle). `GuiBuildCraft.toLegacyButton` converts at the edge. On the menu side the
  1.6.4 click `modifier` is the `ContainerInput`, whose ids are the 1.6.4 numbers (0 pickup, 1 shift, 3 middle).
- **Phantom slots.** `clicked` sends every click on an `IPhantomSlot` to the 4.2.2 handler, except vanilla's
  drag-split (`QUICK_CRAFT`), and `canDragTo` keeps drags out of phantom slots (1.6.4 had no drag-split into them).
  26.x only sends a middle click in creative; `GuiBuildCraft.mouseClicked` sends it for phantom slots in survival too,
  as 1.6.4 did, so middle click still clears a filter slot.
- **Buttons.** `GuiBetterButton` is an `AbstractButton` with the 4.2.2 `id`; pressing it calls the open screen's
  `GuiBuildCraft.actionPerformed(AbstractButton)`, as 1.6.4's `GuiScreen` did. Add buttons in `init()` with
  `addRenderableWidget`; `GuiTools.newButtonRow*` take `this::addRenderableWidget` where 4.2.2 passed `buttonList`.
- **Ledgers** are unchanged, including `SessionVars` remembering the open ledger across GUIs and the animation
  stepping once per frame (frame-rate dependent, as in 1.6.4). `drawIcon` takes a GUI sprite id
  (`textures/gui/sprites/<path>.png`): 4.2.2's item-atlas icons (`buildcraft:icons/energy`) are GUI sprites now
  (`buildcraftreforged:icons/energy`).
- **`GuiAdvancedInterface`**: `AdvancedSlot.getIcon` returns a `TextureAtlasSprite` (which knows its atlas, so
  `getTexture` is gone); hover text is a deferred tooltip. `FluidSlot` draws nothing until #24 ports `FluidRenderer`
  (`SEAM(#24)`); 4.2.2 marked it unfinished too.
- **Slots take a vanilla `Container`** where 4.2.2 took `IInventory`, as `Utils` does (#22), until #23 settles the
  inventory layer. `StackHelper.canStacksMerge` is a private copy in `BuildCraftContainer` until #23 ports
  `StackHelper`.
- **Opening a menu.** Register the type with `IMenuTypeExtension.create(Container::new)` and a client constructor
  `(int containerId, Inventory, RegistryFriendlyByteBuf)` that reads the block position and finds the block entity; open
  it from the block's `useWithoutItem` with `player.openMenu(tile, pos)` (the tile is the `MenuProvider`); pair it with
  its screen in `RegisterMenuScreensEvent`. This replaces `IGuiHandler` and `openGui(..., GuiIds.X, ...)`.
- **Sample** (GameTest source set, dev runs only, never in the jar): block `buildcraftreforged:sample_gui`
  (`/give @s buildcraftreforged:sample_gui`) opens a menu laid out like 4.2.2's Stirling engine GUI (texture copied as
  `sample_gui.png`): a phantom slot, a counter synced by `CounterWidget` over `PacketGuiWidget` and drawn as the
  flame gauge with a tool tip, a ledger showing the counter and step, and a `GuiBetterButton` that changes the step
  through `PacketGuiReturn`. It is the pattern to copy for machine GUIs.
- **No `@OnlyIn`.** NeoForge 26.3 no longer strips `@OnlyIn` members and shows a load warning for any mod using it.
  Client-only classes (`GuiBuildCraft`, `GuiAdvancedInterface`, `GuiTools`, `GuiBetterButton`, every screen) carry a
  `/* Client only. */` comment instead, and only client code (screen constructors, `RegisterMenuScreensEvent`) may
  touch them. `Widget.draw` takes a `GuiBuildCraft` but only screens call it, so the server never loads the class.
- **Tests.** `BuildCraftContainerTest` (JUnit: drag rule, widget data, button numbering). Item stacks need a loaded
  world, so the phantom-slot and shift-click rules are the `gui_menu_slots` GameTest, and `gui_sample_menu` opens the
  sample block's menu, checks the widget value on a client copy of the menu and runs a GUI return through
  `PacketHandler.checkMenuAccess`. A mock player has no mod channel, so NeoForge refuses the open-screen packet: the
  test opens the tile's menu on the server side itself, and the right-click → `openMenu` path is checked in a client
  only. Drawing and mouse input need a client and are not tested.

## Power (#35)

Ported to `buildcraftreforged.api.power`: `PowerHandler` (with `Type`, `PerditionCalculator`, `PowerReceiver`),
`IPowerReceptor`, `IPowerEmitter`, `ILaserTarget`; new `PowerEnergyHandler`. Plan section 7.

### Scaling: MJ × 10 = FE, stored as `long`

Every energy value the 4.2.2 power api handled in `float` MJ is a `long` in FE, 10 FE per MJ:

| 4.2.2 (MJ, `float`) | Here (FE, `long`) |
|---|---|
| `configure(min, max, activation, maxStored)` | same four values × 10 (`TileRefinery` `configure(50, 150, 25, 1000)` → `configure(500, 1500, 250, 10000)`) |
| `energyStored`, `receiveEnergy`, `useEnergy`, `addEnergy`, `setEnergy`, `powerRequest` | FE, `long` |
| `PerditionCalculator.DEFAULT_POWERLOSS` 1 MJ/t | 10 FE/t |
| `PerditionCalculator.MIN_POWERLOSS` 0.01 MJ/t | 0.1 FE/t |
| `configurePowerPerdition(loss, regularity)` | `loss` × 10, `regularity` unchanged: engines' `(1, 100)` → `(10, 100)` = 0.1 FE/t |
| `new PerditionCalculator(x)` | `x` × 10 (laser 0.5 → 5F, quarry `2 * miningMultiplier` → `20 * miningMultiplier`) |
| `ILaserTarget.receiveLaserEnergy(float)` | `receiveLaserEnergy(long)` |
| NBT `powerProvider` / `storedEnergy` (`float`) | the same tag names; `storedEnergy` is a `long` |

- **Fractions.** Receive limits, storage and pulses are whole FE: no 4.2.2 caller used a fraction finer than 0.1 MJ
  except `PipeItemsWood`'s `64.1f` (→ 641 FE) and `TileEngineWood.maxEnergyExtracted` = `1 + MIN_POWERLOSS` =
  1.01 MJ (→ 10 FE, see "For the engines" below). The perdition rate stays a `float` in FE/t because 0.1 FE/t is common
  (engines, pump, the minimum): `PowerHandler` keeps the fraction already lost in thousandths of an FE
  (`perditionDebt`) and reports the stored energy rounded up, so 0.1 FE/t loses exactly 1 FE every 10 ticks and
  10 FE per 100 ticks, as 4.2.2 lost 1 MJ per 100 ticks. The fraction is not saved (under 1 FE) and is cleared when
  the store empties. `PerditionCalculator.applyPerdition` takes and returns `double` FE so a custom calculator sees
  that fraction; the 4.2.2 rule that a calculator which loses nothing is replaced by `DEFAULT_PERDITION` is kept.
- **Tax.** `getTaxPercent()` is still a fraction; the tax is rounded down to whole FE. Nothing in 4.2.2 overrode it.
- **Unit tests.** `PowerHandler` has a package-private constructor taking a game-time supplier, so
  `PowerHandlerTest` drives perdition, `doWork` and source fading without a level. `SafeTimeTracker`'s
  `markTimeIfDelay(long, long)` is public for that.

### Forge Energy

- **No `IEnergyStorage` in 26.x.** `Capabilities.Energy.BLOCK` is `EnergyHandler`: `int insert/extract` inside a
  `Transaction`, amounts as `long`. `PowerEnergyHandler` is the adapter: one per `(IPowerReceptor, side)`, looking
  the side's `PowerReceiver` up on every call.
- **Registering.** In a module's `RegisterCapabilitiesEvent` listener:
  `PowerEnergyHandler.registerEnergyHandler(event, TYPE)` for a block entity type whose tile implements
  `IPowerReceptor`. A side whose `getPowerReceiver(side)` is null has no capability.
- **Inserting** (`canReceive()`): allowed on a non-null side with a receiver, unless the receptor is an
  `IPowerEmitter` with `canEmitPowerFrom(side)` true. That is how an engine's output side refuses FE (plan section 7)
  without a new interface. It works as 4.2.2's `receiveEnergy` from a power pipe (`Type.PIPE`): no minimum, the tax
  taken, at most `maxEnergyReceived` per call (4.2.2's pipes capped themselves through `powerRequest`), limited by free
  space. The null side reports amount and capacity but never accepts.
- **Transactions.** An insert changes only the stored energy, through a `SnapshotJournal` inside `PowerHandler`
  (shared by every side's adapter), so an aborted transaction leaves nothing behind. On the root commit the sides that
  inserted are marked as power sources (`updateSources`) and `applyWork` runs, as `receiveEnergy` did. Perdition is
  not applied inside a transaction; the next `update()` does it.
- **Extracting** always returns 0: 4.2.2 machines never handed power back and engines push on their pulse.
- **Ticking.** `TileBuildCraft.updateEntity` calls `getPowerReceiver(null).update()` for an `IPowerReceptor`, as
  4.2.2 did (`SEAM(#35)` removed), skipping a null receiver.
- **`ForgeDirection` → `Direction`**, `UNKNOWN` → `null`; `powerSources` is still indexed by ordinal.

### For the engines (#36)

- An engine tile implements `IPowerReceptor` and `IPowerEmitter` (`canEmitPowerFrom(side)` = `side == orientation`),
  returns `level` from `getWorld()`, and registers `PowerEnergyHandler.registerEnergyHandler`. Call
  `level.invalidateCapabilities(worldPosition)` when the orientation changes so cached lookups re-ask.
- Engine constants × 10: `configure(minEnergyReceived(), maxEnergyReceived(), 10, getMaxEnergy())`,
  `configurePowerPerdition(10, 100)`; `maxEnergyReceived`/`maxEnergyExtracted`/`getMaxEnergy` are wood
  500/10/1000 FE, stone 2000/1000/10000, iron 20000/5000/100000 (`minEnergyReceived`: wood 0,
  stone and iron the `TileEngine` default of 20). `TileEngineWood.maxEnergyExtracted` 1.01 MJ becomes 10 FE:
  the redstone engine gains 10 FE every 16 ticks, and the extra 0.01 MJ only showed when more than 1 MJ was
  waiting; 10 FE keeps plan section 7's 10 FE per pulse.
- `sendPower` to a BuildCraft receptor is unchanged: `receiveEnergy(Type.ENGINE, extracted, side)` with the min/max and
  eat-excess rules pinned in `PowerHandlerTest`. A neighbour that is only an FE `EnergyHandler` (another mod) gets the
  pulse through `insert` in a transaction; that path, and what `getPowerToExtract` uses as min/max for it, is the
  engine port's call.
- The doWork conversion `useEnergy(10, maxEnergyReceived(), true) * 0.95F` needs a rounding rule in FE.

## Pipe block, block entity, connections and waterlogging (#48)

New code from `docs/TRANSPORT.md` 3.1–3.3 and 9.4; the 4.2.2 transport classes were read for behaviour only.

- **Table.** `TransportRegistry.PIPE_DEFINITIONS` lists `PipeDefinition(kind, material)` rows; each row registers one
  block and one `ItemBlockBuildCraft`. #48 registers wood, stone and gold for item, fluid and power (9 pipes, the
  materials with 4.2.2 textures); #53 extends the material list to all ten. `PipeMaterial` holds the tier and the
  config id (`wood` … `ender`). One block class per kind (`BlockPipeItems`, `BlockPipeFluids`, `BlockPipePower`)
  under the abstract `BlockPipe`; one block entity type, `pipe` (`TilePipe`), valid for every pipe block.
- **Names.** The 4.2.2 pipe class name is the unlocalised name: `PipeItemsGold` → registry name `pipe_items_gold`,
  translation key `item.PipeItemsGold` for both block and item (the key is in the 4.2.2 language file). Copper,
  netherite and ender have no 4.2.2 key or texture yet (#53, PLAN 8.6).
- **Block state** is `waterlogged` only (`SimpleWaterloggedBlock`). Connections live in `TilePipe.connectionMask`,
  bit `d` = `Direction.get3DDataValue()`. The mask is not saved; it is recomputed on the server tick after the pipe
  loads or is placed, after `neighborChanged`, after a neighbour capability cache (`BlockCapabilityCache`, one per side,
  for the pipe kind's capability) invalidates, and after a plug or module changes on the pipe or a neighbouring pipe.
  Pipes tick on the server only. A pipe whose neighbour pipe disagrees about their shared side tells it to look again,
  which covers chunk loads and same-tick placement. The mask is synced as the network payload (one byte); the client
  refreshes model data and re-meshes the section.
- **Connection rule** (`PipeConnections.canConnect`, level-free and unit-tested): plug → override (`IPipeConnection`
  on the neighbour block entity, ignored by the Isolator) → pipe to pipe (no far plug, same kind, no module ban) →
  pipe to block (not structure, not Isolator, kind capability on the facing side). Items: `Capabilities.Item.BLOCK`
  with `size() > 0`. Fluids: `Capabilities.Fluid.BLOCK` with `size() > 0`. Power: any `Capabilities.Energy.BLOCK`
  handler except `EmptyEnergyHandler`, because NeoForge 26.3's `EnergyHandler` has no `canReceive`/`canExtract`.
- **Modules and plugs** exist only as data so far: `TilePipe.setModuleType(PipeModuleType)` and
  `setPlugged(Direction, boolean)`, saved as `module` and `plugs`. `PipeModuleType` knows only the connection-rule
  facts (Isolator, the four self-banned modules of Q16). #55 replaces the type with the module object; #61 calls
  `setPlugged`.
- **Hardness.** Registered with 0.25 (`DefaultProps.PIPES_DURABILITY`); `BlockPipe.getDestroyProgress` reads
  `pipes.durability` at break time, so the configured value applies without a restart.
- **Shape.** `PipeShapes`: the 4.2.2 centre (4–12 px) plus an arm per connected side, 64 shapes built once, read from
  the block entity in `getShape`.
- **Model.** Blockstate files use the custom block-state model type `buildcraftreforged:pipe` with one `texture`.
  `PipeBlockStateModel.Unbaked` bakes two north-facing templates (`block/pipe_centre_face`, `block/pipe_arm`) six
  ways each into twelve parts and stores the part list of every mask (64 lists); rendering looks the list up by the
  mask in the block entity's model data (`TilePipe.CONNECTIONS`). The geometry key is the model plus the mask.
  The item model is a straight vertical pipe (`block/pipe_inventory`). Textures are the 4.2.2 ones: `pipeItemsWood_standard`,
  `pipeItemsStone`, `pipeItemsGold` and the fluid and power equivalents, under `textures/block/pipe_<kind>_<material>.png`.
- **Not here:** routing and the pipe capabilities of TRANSPORT.md 6.2 (#50–#52; `injectItem` returns 0 with a SEAM),
  the network graph (#49; SEAMs where the mask changes, on load and on removal), `Utils.checkPipesConnections` and
  `addToRandomPipeAround` (still waiting in `Utils`), facades (`isSolidOnSide` is false), the Structure Pipe (#54).
- **GameTests** (`PipeTests`): chest and pipe bits and breaking the chest; each kind against chest, cauldron and an
  energy handler; plugs, the Isolator and module bans; waterlogged pipes keep their connections through bucketing.
  The GameTest source set gives lodestones an energy handler as a stand-in endpoint.

## Fuel and coolant registry (#43)

4.2.2 registered combustion engine fuels and coolants in code (`BuildCraftEnergy.preInit`). They are data now:
three NeoForge data maps declared in `api/fuels/FuelDataMaps` and registered by `EnergyRegistry`. Decided 2026-10-31.

| Data map | File | Key | Value |
| --- | --- | --- | --- |
| `buildcraftreforged:combustion_fuels` | `data/buildcraftreforged/data_maps/fluid/combustion_fuels.json` | fluid | `{"power_per_cycle": FE, "total_burning_time": ticks}` |
| `buildcraftreforged:combustion_coolants` | `data/buildcraftreforged/data_maps/fluid/combustion_coolants.json` | fluid | `{"degrees_cooling_per_mb": float}` |
| `buildcraftreforged:solid_coolants` | `data/buildcraftreforged/data_maps/item/solid_coolants.json` | item | `{"fluid": fluid, "amount": mB}` |

- **Values.** Oil 30 FE per cycle for 5000 ticks, fuel 60 FE for 25000 ticks (4.2.2: 3 and 6 MJ, × 10). Water cools
  0.0023 heat per mB (heat units are unchanged); an ice block counts as 2000 mB of water. Lava stays out, as it was
  commented out in 4.2.2. All values are synced to clients (not mandatory).
- **Oil and fuel before #42.** Their entries carry a `neoforge:registered` condition on the fluid, so they load once
  #42 registers `buildcraftreforged:oil` and `buildcraftreforged:fuel` and are skipped quietly until then. Key the
  source fluid, not the flowing one; tanks hold source fluids. #42/#44 need no code change here.
- **Lookups (the API for #44).** Unchanged 4.2.2 entry points: `IronEngineFuel.getFuelForFluid(Fluid)` returns a
  `Fuel` (`liquid`, `powerPerCycle` in FE, `totalBurningTime`) or `null`; `IronEngineCoolant.getCoolant(FluidStack)`,
  `getCoolant(ItemStack)`, `getFluidCoolant(ItemStack)` and `isCoolant(Fluid)`. Each reads the data map first and
  falls back to the 4.2.2 code-registered maps (`fuels`, `liquidCoolants`, `solidCoolants`, filled by `addFuel` and
  `addCoolant`), which stay for other mods. Data wins when both name a fluid. `getFuelForFluid` builds a new `Fuel`
  for data entries on each call, so callers may cache it per fuel change; `getFluidCoolant` builds a new stack for data
  entries. Solid coolants are a fluid and an amount, not a `FluidStack`: a `FluidStack` cannot be decoded while data
  maps load, because fluid components are bound after them.
- **Config multipliers.** 4.2.2 multiplied the oil and fuel burning times by `fuel.oil.combustion` and
  `fuel.fuel.combustion` when it registered them. The new `IronEngineFuel.burningTimeMultipliers` (fluid id →
  `DoubleSupplier`) applies them at lookup to data-map fuels, `(int) (time * multiplier)` as in 4.2.2, so a config
  change applies without a reload. `EnergyRegistry` puts the two config values there.
- **Data packs** add a fuel or coolant with a file at the same path (entries merge; `"replace": true` overrides an
  entry, `"remove"` drops one). Tags work as keys, as in every data map.
- **Tests.** `FuelRegistryTest` (JUnit) checks the shipped files against the 4.2.2 values and the code-registered
  fallback; no data packs load there, so the data maps are empty. The GameTest `fuels` reads the loaded data maps:
  water, ice, and lava from the GameTest data pack behind the same condition (plus an entry for a missing fluid that
  the condition skips), data over code, the multiplier, and oil and fuel at 30/5000 and 60/25000 once #42 has
  registered them.

## Pipe network graph and cache (#49)

New code from `docs/TRANSPORT.md` 3.4, package `buildcraftreforged.transport.graph`. No routing, budgets or source
ticking yet (#50–#52).

- **Classes.** `PipeNetworkManager` builds and caches networks; it reads the level only through `PipeWorld` (a
  position → pipe lookup, "is this a loaded non-pipe block", and an endpoint-cache factory), so the whole graph is
  unit-tested on a stub (`PipeNetworkManagerTest`). `LevelPipeWorld` is the real `PipeWorld`; `PipeNetworks` keeps one
  manager per `ServerLevel` (created on first `get`, dropped on `LevelEvent.Unload`). `TilePipe` implements
  `PipeWorld.Pipe` (`getPipeType`, `getTier`, `getConnectionMask`, `getModuleType`).
- **Network.** `PipeNetwork`: `kind`, `id` (per manager, for logs), `nodes[]`, `endpoints[]`, `sourceNodes[]` (nodes with
  `PipeModuleType.isSource()`: Extractor, Filtered Extractor, Pickup; sorted by `BlockPos.asLong`), mutable
  `sourceCursor`, `isValid()`, `indexOf(pos)` / `node(pos)`. Node 0 is the pipe the build started from, then BFS order
  with neighbours in side order, so the same world always builds the same arrays.
- **Node.** `PipeNode`: `pos`, `tier`, `module` (the `PipeModuleType` at build time; #55 swaps in the module object),
  `connectionMask`, `pipe` (the `PipeWorld.Pipe`, i.e. the `TilePipe`, for budgets and records), and `next(side)`: a node
  index (`PipeNode.isNode`), `PipeNode.NONE` (-1), or an endpoint (`PipeNode.isEndpoint`, `PipeNode.endpointIndex`).
  A side leads to another node only when both masks have the bit; a connected side towards an unloaded position or
  towards a pipe block without a live block entity is `NONE`, never an endpoint.
- **Endpoint.** `PipeNetwork.Endpoint(pos, face, node, side, cache)`: the block, its face towards the pipe (the
  capability context), the node index and the node's side towards it, and a `BlockCapabilityCache` for the kind's
  capability (`Capabilities.Item.BLOCK`, `Fluid.BLOCK`, `Energy.BLOCK`). The cache's listener invalidates the network;
  its validity check is `network.isValid()`, so caches of dropped networks unregister themselves. The cache is read
  once when it is created, because NeoForge only notifies caches that were read since their last invalidation.
- **Dirty means unmapped** (a simplification of the 3.4 text, 31 Oct 2026). The manager keeps `byPos` for valid
  networks only; `invalidate(pos)` drops the networks at `pos` and at its six neighbours and unmaps all their nodes,
  so there is no separate dirty set that could grow with positions that are no longer pipes. `isDirty(pos)` is "the
  next `get` builds". Dropping the neighbours' networks too is what makes merging work when only the new pipe has
  reported a change; it also drops an adjacent network of another kind, which only costs a rebuild.
- **Invalidation sources** (`PipeNetworks.invalidate(level, pos)`, a no-op on the client and when the level has no
  manager yet): `TilePipe.onLoad`, `setRemoved` (block break and chunk unload), a connection-mask change in
  `updateConnections`, `onConfigurationChanged` (plug or module change, even when the mask stays the same), and the
  endpoint caches. Chest contents, tank levels and energy levels never invalidate. Masks settle on the server tick
  after a block change (#48), so a network asked for in the same tick as a placement can still be the old shape; it
  is invalidated again when the mask changes.
- **Lookups never load chunks or create block entities**: `LevelPipeWorld.getPipe` uses
  `LevelChunk.getBlockEntity(pos, CHECK)` behind `isLoaded`. `IMMEDIATE` would replace a removed pipe's block entity
  with a fresh one during a build.
- **Structure pipes** have no network: `get` returns null for them (TRANSPORT 11).

### For #50 (item walk), #51 (fluids) and #52 (power)

- Get the network with `PipeNetworks.get(serverLevel, pos)` (null when the position holds no loaded pipe, or a structure
  pipe). It is cheap when cached. Do not keep a network across ticks without checking `isValid()`; get it again.
- Walk with `network.nodes[i].next(side)`; `in` sides and paths are node indices. Destinations are
  `network.endpoints[PipeNode.endpointIndex(next)]`; read the handler live with `endpoint.cache().getCapability()`
  (cast to the kind's handler type; null when the block lost it, which the next tick's rebuild removes). Never call
  `getCapability` from an invalidation listener.
- Per-pipe state (budgets, fluid locks, flow records, overflow) stays on the block entity: `(TilePipe) node.pipe`.
- The source tick (TRANSPORT 6.1) is not here: `PipeNetworkManager.networks()` lists the valid networks built so far,
  in build order, but a network is only built when something asks for it. The tick has to make sure every source pipe's
  network exists (for example source pipes ask for theirs when they load or when their module is fitted) and then
  walk `networks()` with `sourceNodes` / `sourceCursor`, at `LevelTickEvent.Post` on the server.
- `PipeModuleType.isSource()` decides `sourceNodes`; when #55 replaces the type with module objects, keep that method
  (or its equivalent) and rebuild the network on a module change (`onConfigurationChanged` already invalidates).

## Tier pipes: registration, recipes, textures and the tier table (#53)

New code from `docs/TRANSPORT.md` 2.1, 2.2 and Q17 (decided 29 Oct 2026); the 4.2.2 pipe registration
(`BuildCraftTransport.buildPipe`) was read for the recipe shapes only.

- **Registration.** `TransportRegistry.PIPE_DEFINITIONS` now lists every material, so the table is the 30 tier pipes:
  item, fluid and power × wood, stone, iron, copper, gold, quartz, diamond, emerald, netherite, ender, in that creative
  tab order. Names follow the #48 rule (`pipe_power_netherite`, key `item.PipePowerNetherite`). The twelve keys 4.2.2
  never had are added to `en_us.json` in the 4.2.2 wording ("Copper Transport Pipe", "Quartz Fluid Pipe",
  "Emerald Kinesis Pipe"). The 4.2.2 `tip.*` lines describe the old per-material behaviour and are left unused.
- **Pipe Sealant** (`pipe_waterproof`, key `item.pipeWaterproof`) is registered here because the fluid-pipe recipe
  needs it: one green dye, shapeless, gives one, as 4.2.2 `loadRecipes`. Texture: the 4.2.2 `items/pipeWaterproof.png`.
- **Recipes** (Q17), hand-written JSON like the rest of `data/` (the project has no datagen providers yet):
  item pipe = `material, glass, material` in a row, 8 pipes; fluid pipe = item pipe + Pipe Sealant, shapeless, 1;
  power pipe = item pipe + redstone, shapeless, 1; and the 4.2.2 uncrafting of a fluid or power pipe back into its item
  pipe (`pipe_items_<material>_from_pipe_<kind>_<material>`). Materials: `#minecraft:planks` and `#c:stones`, where
  4.2.2 used the Ore Dictionary (`plankWood`, `stone`; `c:stones` also takes andesite, diorite, granite, tuff and
  deepslate), and exact items elsewhere: iron, copper, gold and netherite ingots, block of quartz, diamond, emerald,
  ender pearl, `minecraft:glass`.
- **Tier table.** `transport.tiers.<material>.itemsPerSecond`, `fluidPerTick` and `powerPerTick` in the common config
  (`PipeTierConfig`, defined inside #19's `transport` section). Defaults are `PipeTierStats.defaults`: 1 item/s,
  20 mB/t and 80 FE/t at tier 1, doubling per tier. Each value is a plain validated `define`, not `defineInRange`,
  because NeoForge clamps an out-of-range number to the nearest bound while TRANSPORT.md 2.2 wants the tier default;
  a missing, non-numeric or below-1 value is replaced by the default and logged by the config spec (one warning per
  key). `BlockPipe.getTierStats()` returns the caps in use (defaults until the config is loaded); routing (#50–#52)
  applies them. `transport.burstSeconds`, `fluidLockTicks` and `maxRouteVisits` belong to those issues.
- **Textures.** Every 4.2.2 pipe texture is one material colour plus a kind overlay: diagonal dots for items, dark
  green (`#223505`) sealant brackets for fluids, red L-bars for power. The 4.2.2 files are used where the material and
  kind existed: items wood, stone, iron, gold, quartz, diamond (`pipeItemsDiamond_center`, the Sorter's side colours
  left out), emerald; fluids wood, stone, iron, gold, emerald; power wood, stone, gold, quartz, diamond. The other 13 are
  new, made by recolouring the 4.2.2 gold texture of the same kind: copper `#C8683A`, netherite `#3F3537`, ender
  `#22796A` in all three kinds; quartz and diamond fluid pipes and the emerald power pipe in the 4.2.2 colours of
  those materials (quartz takes its pixels from `pipeItemsQuartz`); and the iron power pipe, because 4.2.2 only had
  the Power Limiter's mode textures (`pipePowerIronM2`…`M128`), which would be wrong on a plain pipe.
- **Tests.** Unit (`PipeTiersTest`): the default ladder against the table; config with all tiers, a missing tier and
  invalid values (zero, a string) run through the spec's correction; 30 registered blocks and items with the right
  kind, tier and caps; every pipe has a blockstate, item definition, item model, texture, loot table and recipe.
  GameTests (`TierPipeTests`): every pipe and the sealant crafts from its recipe in the server's recipe manager with
  the right count, uncrafting included; every pipe places and reports the loaded config's caps.

## Status panel (#26)

New code for plan section 10 (no 4.2.2 original), in `buildcraftreforged.core.status` plus `core/gui/StatusLedger`.
Decided 31 Oct 2026.

- **Provider.** A block entity that works implements `IStatusProvider` (porting checklist step 4). One method per
  line, each with a default, each read on the server only while someone has the panel open:

  | Line | Method | Default | Type |
  |---|---|---|---|
  | State | `getStatusState()` | from `IMachine`: `isBlocked()` → `BLOCKED`, `isActive()` → `WORKING`, else `IDLE` | `MachineStatus` |
  | Reason | `getStatusReason()` | `NONE` (line hidden) | `StatusReason` |
  | Flow | `getStatusFlow()` | null (line hidden) | the tile's `FlowMeter` |
  | Power | `getStatusPower()` | null | `StatusReport.Power` (`drawn`/`produced` helpers) |
  | Heat | `getStatusHeat()` | null | `StatusReport.Heat(HeatStage, overheatSeconds)` |
  | Owner | `getStatusOwner()` | `TileBuildCraft.getOwner()` | `String` |

  `MachineStatus` and `StatusReason` carry the plan's values; their ordinals are the wire format, so append new values
  at the end and add a `gui.status.state.*` / `gui.status.reason.*` key to `en_us.json`.
- **`FlowMeter`** (`new FlowMeter(() -> level == null ? 0 : level.getGameTime())`) counts `itemsIn/itemsOut` (items),
  `fluidIn/fluidOut` (mB) and `energyIn/energyOut` (FE) into per-tick buckets and sums the last 20 ticks; it needs no
  tick call and is not saved. The panel shows items/s, mB/t (mB per second / 20) and FE/t (`Power.drawn(meter, stored,
  capacity)` uses `energyIn`, `Power.produced` uses `energyOut`).
- **Sync only while open.** `StatusWidget` is an invisible widget. A machine menu adds it
  (`addWidget(new StatusWidget(tile))`, on both sides, in the same widget order) and the screen adds the ledger in
  `initLedgers`: `ledgerManager.add(new StatusLedger<>(this, StatusWidget.find(container)))` (or a field, as the sample
  does). Opening the ledger, or `SessionVars` reopening it in the next GUI, sends the server-bound `PacketStatusWatch`
  (`status_watch`: container id, watching) and closing it sends false; closing the menu ends everything. The server
  checks that the id is the sender's open `BuildCraftContainer`, that it has a `StatusWidget` and that `stillValid`
  holds, and drops the packet otherwise (logged as for `gui_return`). While watched, `broadcastChanges` sends a
  `StatusReport` over `PacketGuiWidget` at most every `StatusWidget.SEND_INTERVAL` (10) ticks and only when it changed;
  the first goes at once. `StatusReport.STREAM_CODEC` writes absent lines as absent, so the client hides them.
- **Blocks without a GUI.** `useWithoutItem` calls `StatusPanel.open(player, tile)` (no-op on the client; return
  `InteractionResult.SUCCESS`). It opens the `status` menu (`CoreRegistry.statusMenu`, `ContainerStatus`: no slots,
  watched from the moment it opens) with `GuiStatus`, which is the ledger alone, centred and always open, headed with
  the block's name.
- **Red light.** A block that can be blocked adds `StatusPanel.BLOCKED` (`blocked`) to its state definition (default
  false). `TileBuildCraft.updateEntity` calls `StatusPanel.updateBlockedLight` every server tick for an
  `IStatusProvider`, which sets the property to `getStatusState() == BLOCKED` when it differs (so keep
  `getStatusState` cheap). The model shows it with a multipart case applying `buildcraftreforged:block/status_light`, a
  small emissive red lamp on top of the block (see the `sample_status` blockstate). Blocks drawn by a
  `BlockEntityRenderer` (engines) can read the same property.
- **For #27 (owner).** Only `IStatusProvider.getStatusOwner()`'s default reads `TileBuildCraft.getOwner()`; change the
  default when the owner becomes a game profile. The line is a plain string, at most 64 characters on the wire.
- **For #36 (engines).** Implement `getStatusState` (`WORKING`, `IDLE`, `OVERHEATING`, `OUT_OF_FUEL`), `getStatusPower`
  with `Power.produced(meter, stored, capacity)` and `meter.energyOut(fe)` where the engine sends power, and
  `getStatusHeat` with `HeatStage.values()[energyStage.ordinal()]` (named and ordered as 4.2.2's
  `TileEngine.EnergyStage`) and the overheat timer's seconds left, or `Heat.NO_COUNTDOWN`. The engine GUIs add the
  ledger next to `EngineLedger`; engines are not blocked, so they need no `BLOCKED` property.
- **For #63 (blocked output).** Return `BLOCKED` from `getStatusState` (the default does, through
  `IMachine.isBlocked()`), `OUTPUT_FULL` or `NO_DESTINATION` from `getStatusReason`, and add `BLOCKED` to the block
  with the multipart light. The Machine trigger already reads `isBlocked()`.
- **Samples** (GameTest source set): `sample_gui` now fills all six lines (made up from its counter) and has the status
  ledger; `sample_status` is a GUI-less block that opens the panel on right-click and cycles Working, Blocked (red light)
  and Idle every five seconds.
- **Tests.** JUnit `FlowMeterTest` (window, forgetting, reuse, rates) and `StatusReportTest` (codec with all and no
  lines, bad ordinal, owner cut, `IMachine` defaults, watch packet). GameTests `status_sync_while_open` (no data before
  the watch packet, refused packets, all six lines read by a client copy of the menu, interval, nothing after close)
  and `status_panel_without_gui` (watched on open, `stillValid`, red light on and off without replacing the block
  entity). Drawing, the ledger's open/close clicks and the light's look need a client and are not tested.

## Laser and laser rendering (#71)

Ported: `silicon/BlockLaser`, `silicon/TileLaser`, `core/EntityLaser`, `core/EntityEnergyLaser`,
`core/EntityPowerLaser`, `core/render/RenderLaser`, `core/render/RenderEnergyLaser`. New: `silicon/render/RenderLaserTile`
(the block entity renderer) and `silicon/render/SiliconRenderers` (4.2.2 `SiliconProxyClient.registerRenderers`).
`SiliconRenderBlock` became the JSON model `block/laser_block`. Recorded 31 Oct 2026.

- **Block.** `laser_block`, block entity type `laser`. Metadata → `BlockLaser.FACING`, the clicked face, as 4.2.2's
  `onBlockPlaced`; the blockstate rotates the up-facing model as the markers do. `Material.iron` + `setHardness(10F)`
  → metal sound and map colour, `strength(10F)`, `requiresCorrectToolForDrops` (1.6.4 iron needed a pickaxe) and the
  `minecraft:mineable/pickaxe` tag. Not an opaque cube (`noOcclusion`); the shape stays a full cube, as 4.2.2's bounds.
  Recipe `ORR/DDR/ORR` (obsidian, redstone, diamond) as 4.2.2.
- **Model.** `block/laser_block` has `SiliconRenderBlock`'s two boxes for the up facing (4 px base, 6 x 9 x 6 px
  emitter) with `laser_top` on up faces, `laser_bottom` on down faces and `laser_side` on the rest, UVs from the box
  bounds as `RenderBlocks` did. The `uvRotate*` tweaks per facing are not reproduced; the blockstate rotation decides
  the side texture's orientation.
- **Power, MJ × 10.** `configure(250, 1500, 250, 10000)`, perdition `PerditionCalculator(5F)` (0.5 MJ/t), at most
  `getMaxPowerSent()` = 40 FE per tick to the target, `Type.MACHINE`. FE comes in on every side through
  `PowerEnergyHandler.registerEnergyHandler` (`SiliconRegistry.registerCapabilities`). Saved under 4.2.2's
  `powerProvider` tag.
- **Target search** is 4.2.2's `findTable`: an 11 x 11 x 11 box around the laser cut at the laser's own layer on the
  side away from the facing (the laser's own plane is included), every 100 ticks while it has no valid target, a
  random target among those whose `requiresLaserEnergy()` is true. New: positions in unloaded chunks are skipped,
  because 26.x `getBlockEntity` would load the chunk where 1.6.4's did not.
- **The beam is render data, not an entity**, as `EntityBlock` (#29). `TileLaser` keeps its `EntityEnergyLaser`
  (head, tail, visible, power average over 100 ticks) and writes it after an `active` flag in its network payload;
  the client tile rebuilds it in `readPacketPayload`, and `RenderLaserTile` draws it through `RenderEnergyLaser`.
  Positions are sent as doubles, not 4.2.2's data watcher ints (× 8192), which overflow beyond 262144 blocks from the
  origin (the GameTest structures sit near x 14 000 000 and caught it); the power average is a `long`. Updates go every 3 ticks as 4.2.2's
  `sendNetworkUpdate` throttle; turning the beam off, or the target becoming valid or invalid, is sent at once (4.2.2
  killed the entity, which reached clients by itself). `EntityLaser.onUpdate` keeps only the length and angle maths;
  the bounding box and NBT (which 4.2.2 called useless) are gone.
- **Texture by power.** `EntityEnergyLaser.getTexture()` picks `textures/entity/laser_1..4.png` at ≤ 10, ≤ 20, ≤ 30 and
  above FE/t (4.2.2: 1, 2, 3 MJ/t). `RenderEnergyLaser` keeps the 40 boxes whose texture offset steps back one pixel
  every 10 frames, so the stripes move along the beam. Light is the laser's brightness, 210, used as packed light as
  `RenderMarker` does.
- **Active light.** `isActive()` is `isValidTable()` on the server, as 4.2.2, and the synced `active` flag on the
  client, so `TileBuildCraft`'s auxiliary light (+8) agrees on both sides.
- **Gates.** `IActionReceptor` with `action_on`/`action_off`: Off removes the beam and stops delivery until On.
- **Not done here:** the status panel provider (plan section 10, #26). `RenderMarker` keeps its private copy of
  `RenderLaser`'s box; it can switch to `RenderLaser` with an `EntityPowerLaser` when someone next touches it.

### Laser target API (for #72, #73 and other mods)

`buildcraftreforged.api.power.ILaserTarget`, implemented by the target's block entity:

| Method | Contract |
|---|---|
| `boolean requiresLaserEnergy()` | True while the target has work that needs energy. Lasers only pick targets that return true, and drop a target (beam off) as soon as it returns false. |
| `void receiveLaserEnergy(long energy)` | Called once per tick by each laser feeding the target, server side, with the FE it took from its store: up to 40 FE. The target keeps it; there is no return value and nothing is given back. |
| `boolean isInvalidTarget()` | True once the target is gone; return `isRemoved()`. |
| `int getXCoord()`, `getYCoord()`, `getZCoord()` | The target's block position; the beam ends at x + 0.475, y + 9/16, z + 0.475 (jittered by ±0.1 in x and z). |

- Energy is FE as `long` (4.2.2's float MJ × 10). An assembly table that needs 4.2.2's `energy` MJ for a recipe
  compares against `energy × 10` FE.
- Lasers search 5 blocks each way, on the facing side of the laser, so a target is fed by every laser that faces it
  within range. A laser keeps its target until `requiresLaserEnergy()` or `isInvalidTarget()` says otherwise, then
  searches again within 100 ticks.
- No capability is involved: the laser finds targets by `level.getBlockEntity(pos) instanceof ILaserTarget`.
- `LaserTests` (GameTests `laser_feeds_target`, `laser_target_search`) has the smallest target, a test-only block
  `gametest_laser_target` that wants 400 FE; they check delivery at 40 FE/t, the beam's head, tail, length and angle,
  the payload round trip, the gate actions, the facing and range limits and an unpowered laser.

## Tank (#64)

- **Blocks and items.** `factory/BlockTank` (a `BaseEntityBlock` with `BlockBuildCraft`'s ticker, as the markers) and
  `factory/TileTank`, registered in `FactoryRegistry` as `tank_block` (block entity `tank`). `Material.glass` and
  `setHardness(0.5F)` become glass sound, the hat instrument, strength 0.5, no occlusion, no spawns, not a redstone
  conductor, no suffocation. Recipe as 4.2.2 (eight glass); the block drops itself and its fluid is lost, as in 4.2.2.
  `FactoryRegistry` now carries the MMPL header and registers its capabilities from a `RegisterCapabilitiesEvent`
  listener added in `register(modBus)`.
- **Textures.** 4.2.2 `getBlockTexture` drew the sides with `tank_stacked_side` when the block below was a tank. Models
  cannot look at neighbours, so the block state has `joined_below`, set on placement and by `updateShape`, and picks
  `tank_block` or `tank_block_stacked` (both on `block/template_tank`, a 2..14 × 0..16 × 2..14 cuboid; UVs follow the
  element, as 1.6.4's did the block bounds). `shouldSideBeRendered` (no top or bottom face between two tanks) is
  `skipRendering` with `cullface` on the top and bottom faces.
- **Fluid capability.** `TileTank` is itself a one-slot `ResourceHandler<FluidResource>` for the whole stack:
  `Capabilities.Fluid.BLOCK` → the tile. The slot is 4.2.2 `getTankInfo`'s composite tank (bottom tank's fluid plus
  every tank above with the same fluid; capacity up to the first tank with another fluid), which `getTankInfo` now
  returns as a detached `Tank`. `insert` is 4.2.2 `fill` (refused if the bottom holds another fluid, then bottom tank
  first and up), `extract` is `drain(FluidStack)` (bottom tank only). Each `Tank` journals itself, so one transaction
  over several tanks rolls back as one. The 4.2.2 `fill`/`drain`/`canFill`/`canDrain` stay as plain methods. Fixes
  from BUGFIXES: `canFill`/`canDrain` answer from the bottom tank (upstream aa24a99d9), and `drain(FluidStack)`
  compares with the bottom tank (upstream 08edcf175).
- **Stacking.** `moveFluidBelow` each server tick, as 4.2.2. `hasUpdate` is also set by `setChanged`, which every tank
  change (and every committed transaction) calls, then the 4.2.2 throttle sends the tile state every
  `2 * updateFactor` ticks.
- **Buckets.** `onBlockActivated` → `useItemOn` over `FluidUtils.handleRightClick(tile, player, hand, true, true)`
  (#24), so any container with the item fluid capability works, creative mode keeps the container, and an emptied
  container goes to the inventory (see BUGFIXES, BuildCraft#2353). As in 4.2.2 a filled container always uses the
  click, so a full tank never lets a bucket place its fluid; an empty container on an empty tank passes the click on.
- **Light.** 4.2.2 relit the client from `getFluidLightLevel` (`FluidType.getLightLevel(stack)` now). The tile writes
  it into the chunk's auxiliary light manager on both sides and `BlockTank.getLightEmission` reads it, as
  `TileBuildCraft` does for active machines.
- **Comparator** (new, plan section 6): BuildCraft 7.1's formula, composite amount × 15 / composite capacity, the same
  for every tank in the stack. Each tank checks it every server tick and calls `updateNeighbourForOutputSignal` when it
  changes, so a comparator beside any tank follows changes anywhere in the stack.
- **Renderer** (`factory/render/RenderTank`, registered in `FactoryRenderers`): the render state copies the fluid,
  `colorRenderCache` and capacity; `submit` applies 4.2.2's translate/scale and submits `FluidRenderer.renderFluid` at
  stage `amount / capacity × (DISPLAY_STAGES - 1)` in `RenderTypes.entityTranslucent` on the block atlas, with the
  block's light. 4.2.2 enabled face culling; the render type here draws both sides, because `FluidRenderer`'s winding
  is not checked in game yet.
- **Not ported:** `BptBlockTank` (deprecated in 4.2.2; blueprints are P6).
- **GameTests** (`TankTests`): `tank_stacking` (states, bottom/top lookup, fill from the top tank up through the stack,
  composite view, another fluid refused, comparator value, breaking the middle tank), `tank_transfer` (aborted
  transaction across two tanks, fluid falling into the lower tank, a comparator block following it, draining through the
  upper tank), `tank_buckets` (fill, empty, empty-on-empty passes, a stack of two buckets returns the empty one, a full
  tank keeps the bucket, creative keeps the bucket, lava lights the tank).
- **Not checked without a client:** the two models and textures, face culling between stacked tanks, the render layer
  (picked from the textures' transparency), the fluid renderer (geometry, colour, light), the item model, the light
  update on the client after a sync.

## Oil and fuel (#42)

Ported `energy/BlockBuildcraftFluid`, `ItemBucketBuildcraft`, `render/EntityDropParticleFX` and the oil and fuel part
of `BuildCraftEnergy.preInit`. Decisions recorded 31 Oct 2026.

- **Registry** (`EnergyRegistry`). Per fluid: a `FluidType` (`BCFluid`, registry name `oil`/`fuel`, 4.2.2 field
  `buildcraftFluidOil`/`buildcraftFluidFuel`), a source `Fluid` (`oil`/`fuel`, field `fluidOil`/`fluidFuel`), a
  flowing `Fluid` (`flowing_oil`/`flowing_fuel`, fields `fluidOilFlowing`/`fluidFuelFlowing`, new), a
  `BlockBuildcraftFluid` (`block_oil`/`block_fuel`, keys `tile.blockOil`/`tile.blockFuel`) and an
  `ItemBucketBuildcraft` (`bucket_oil`/`bucket_fuel`, keys `item.bucketOil`/`item.bucketFuel`, Machines tab). A
  `FluidStack` always holds the source fluid; compare with `Fluid.isSame` when a flowing fluid can turn up.
  `FluidUtils.getFluid("oil")` still resolves the 4.2.2 name to `buildcraftreforged:oil`.
- **Flow** (`BuildcraftFlowingFluid`, new; Forge's `BlockFluidClassic` split into `LiquidBlock` and `FlowingFluid`).
  7 blocks (quantaPerBlock 8, drop-off 1, slope search 4); tick delay viscosity / 200 as Forge 1.6.4's
  `BlockFluidBase` computed it: oil 1500 → 7 ticks, fuel 1000 → 5. No new sources. Falling fluid spreads the vanilla
  way (7 blocks from where it lands; Forge gave it one less), accepted as a platform difference. Density, viscosity
  and temperature are on the `FluidType` as 4.2.2 set them.
- **Other liquids.** 4.2.2 made both fluids `Material.water` and overrode `canDisplace`: oil and fuel never flow into
  another liquid (on water they spread sideways over it), nothing flows into them, and lava hardens against them as
  against water (obsidian from a source, cobblestone from flowing lava; a `FluidInteractionRegistry` entry). NeoForge
  26.3 checks fluid interactions in `LiquidBlock.neighborChanged` only, not in `onPlace`, so lava hardens when oil or
  fuel arrives next to it or on its next neighbour update, not the instant lava is poured beside existing oil.
- **Tag.** `minecraft:washed_away_by_fluids` must list every `LiquidBlock`: `FlowingFluid` only spreads into blocks
  in that tag (water and lava are in it), including its own flowing blocks. Without it oil does not flow.
- **`Material.water` properties** on the `FluidType`: swim, drown, extinguish, boats, farmland hydration, no fall
  damage. Bucket sounds are vanilla's (1.6.4 buckets were silent). The block copies vanilla water's block properties.
- **Fire** (`BlockBuildcraftFluid`). Fire spread 300 when flammable; flammability oil 0, fuel 5; oil is a fire source
  (burns forever) as in 4.2.2. Oil's flammability follows `burnOil` through a supplier, so the config is read when
  fire asks, not at registration. In the Nether, a neighbour change turns a flammable fluid block into fire (no
  explosion) when `fluids.netherIgnition` is on (PLAN.md section 19.2).
- **Buckets.** `BucketHandler` and `FluidContainerRegistry` are gone: vanilla `BucketPickup` on the `LiquidBlock`
  fills an empty bucket with `Fluid.getBucket()`. NeoForge gives `Capabilities.Fluid.ITEM` only to the exact
  `BucketItem` class, so `EnergyRegistry` registers `BucketResourceHandler` for both buckets; tanks and
  `FluidUtils.handleRightClick` then take and give 1000 mB.
- **Client** (`EnergyProxyClient`, not checked in a client). `RegisterFluidModelsEvent` gives each fluid pair the 4.2.2
  `<fluid>_still`/`<fluid>_flow` textures (with their 4.2.2 `.mcmeta` animations) and no overlay or tint;
  `RenderingOil` and `textureHook` have no counterpart. No `IClientFluidTypeExtensions`: the view from inside oil uses
  NeoForge's defaults, not 1.6.4's water fog. The drip particle is the `drop` particle type
  (`ColorParticleOption`, oil black, fuel 0.7/0.7/0) with vanilla's `drip_hang`/`drip_fall`/`drip_land` sprites
  for 1.6.4's particle indices 113/112/114.
- **GameTests** (`OilFuelTests`): bucket placement, pickup and a tank both ways; flow distance, levels and tick
  rates in walled channels, and no new source between two; oil over water, water over oil, lava against oil and fuel;
  fuel turning into fire in the Nether (the test runs in the Nether) and staying put in the overworld. They use
  padding around the 1x1x1 structure and build from y = 2, above the barrier case GameTest puts around it.

### For the combustion engine (#44)

- 4.2.2 `IronEngineFuel.addFuel("oil", 3, 5000 * fuel.oil.combustion)` and `("fuel", 6, 25000 * fuel.fuel.combustion)`:
  key them on `EnergyRegistry.fluidOil`/`fluidFuel` (or the names, which `FluidUtils.getFluid` resolves), MJ × 10
  for the power per tick. The multipliers are `BuildCraftConfiguration.fuelOilMultiplier`/`fuelFuelMultiplier`.
  The fuel registry itself is #43.
- The engine's fuel tank accepts the source fluids; buckets of oil and fuel go in through `FluidUtils.handleRightClick`
  with no extra code.

### For oil fields (#45)

- Place `EnergyRegistry.blockOil.get().defaultBlockState()` (a source). Worldgen that places sources where they could
  flow must schedule a fluid tick (`scheduleTick(pos, fluid, fluid.getTickDelay(level))`) as vanilla lake features
  do, or the oil stays still until a neighbour changes.
- `BlockSpring.EnumSpring.OIL.liquidBlock` is `blockOil`; `canGen` follows `BuildCraftConfiguration.spawnOilSprings`.
- In oceans: oil does not sink into or displace water and water does not flow into oil, so a well capped at the sea
  floor stays a column of oil under the water.

### For the refinery (#68)

- 4.2.2 `RefineryRecipes.addRecipe(new FluidStack(fluidOil, 1), new FluidStack(fluidFuel, 1), 12, 1)` was registered
  in `BuildCraftEnergy.preInit`; #68 owns it now (oil in, fuel out, `energy` 12 MJ, so 120 FE, `delay` 1).
- `FluidRenderer` draws the tank contents from the fluid models registered here.
