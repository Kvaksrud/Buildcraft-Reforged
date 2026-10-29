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
