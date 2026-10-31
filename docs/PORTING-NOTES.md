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
| Forge `IFluidHandler`, `FluidStack`, `FluidRegistry` | NeoForge transfer API: `ResourceHandler<FluidResource>` via `Capabilities.Fluid.BLOCK`, `FluidStack`, `Fluid` + `FluidType` + `LiquidBlock` + bucket | Oil and Fuel registered here; mB values unchanged. 26.3 has no `IFluidHandler` (confirmed in #24). |
| `PowerHandler`, `IPowerReceptor`, `IPowerEmitter` | Ported `PowerHandler` holding FE, exposed as NeoForge's `EnergyHandler` (26.x's transaction-based replacement for `IEnergyStorage`) via `Capabilities.Energy.BLOCK` | Section 7. Adapter `api/power/PowerEnergyHandler` (#35). |
| `Packet`, `PacketPayload`, `@TileNetworkData` | `CustomPacketPayload` + `StreamCodec`, one channel | Explicit codecs instead of the reflection-based payload builder. |
| `GuiContainer` / `Container` / `IGuiHandler` | `AbstractContainerScreen` / `AbstractContainerMenu` / `MenuType` + `IMenuProvider` | 31 GUIs. Same textures, same slot positions, same ledgers. Status panel added as a shared ledger (section 10). |
| `ISimpleBlockRenderingHandler`, `TileEntitySpecialRenderer` | JSON models + blockstates for static blocks; `BlockEntityRenderer` for engines, quarry arm, lasers, markers, refinery, tank fluid; pipes per section 8 | Keep the 16× textures. |
| Animated textures | `.png.mcmeta` | Oil, fuel, laser. The engine chamber is one still frame in 4.2.2; its animation is the renderer stacking it behind the piston (#39). |
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
  returns the `[BuildCraft]` fake player; machines pass their owner's player instead, and `breakBlock` asks
  `BreakBlockEvent` first (see "Owner tracking and the owner-profile fake player (#27)"). Drops come from
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
- **Owner** stays the 4.2.2 string (player name, default `[BuildCraft]`). #27 added the owner's UUID next to it
  (`ownerId`), for the fake player.
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
  facts (Isolator, the four self-banned modules of Q16). #55 added the module object beside the type (see "Pipe
  modules" below); #61 calls `setPlugged`.
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
- `PipeModuleType.isSource()` decides `sourceNodes` (#55 kept the type in the node and put the module object beside it)
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
- **Owner (#27).** `IStatusProvider.getStatusOwner()`'s default reads `TileBuildCraft.getOwner()`, the name recorded
  with the owner's profile, so it needed no change. The line is a plain string, at most 64 characters on the wire.
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

## Gates, pipe wire, and pipe triggers and actions (#60)

Ported from 4.2.2 `Gate`, `GateVanilla`, `ItemGate`, `EnergyPulser`, `PipeTriggerProvider`, `ITriggerPipe`,
`transport/triggers/*`, `gui/ContainerGateInterface`, `gui/GuiGateInterface`, the gate and wire half of `Pipe`, and
the gate and wire parts of `TileGenericPipe`, `BlockGenericPipe` and `PipeRendererTESR`. Decided 2026-10-31.

- **Where it lives.** `TilePipe.gateHolder` is a `PipeGateHolder`, the gate and wire half of 4.2.2's `Pipe` object
  (wires, signal strengths, the gate, `redstonePowered`, the wire render state, fitting and stripping). It
  implements `IPipe`, and `TilePipe.getPipe()` returns it, so `ActionManager.getPipeTriggers` and the trigger
  providers keep their 4.2.2 signatures. The shared files only call into it: `TilePipe` (tick, save, payload,
  `destroy`, mask change), `BlockPipe` (neighbour change, use, redstone), `TransportRegistry` (entries).
- **Items.** The two 4.2.2 gate items with seven damage values each are fourteen items, named after the 4.2.2 icons:
  `gate`, `gate_iron_and` … `gate_diamond_or`, `autarchic_gate` … `autarchic_gate_diamond_or`, with the 4.2.2
  keys `item.pipeGate.<damage>` and `item.pipeGateAutarchic.<damage>`. `ItemGate` holds `kind` and `autarchic`
  (4.2.2 `series`). Wires are `ItemPipeWire` (`red_pipe_wire` …), 4.2.2 `ItemBuildCraft`. `Gate.GateKind` keeps the
  4.2.2 ordinals (saved as `Kind`).
- **Programming travels with the gate** (plan section 6, item NBT → data components). Taking a gate off stores its
  triggers, parameters and actions in the `buildcraftreforged:gate_settings` component (`GateSettings`, one entry
  per used position, registry ids); fitting it puts them back. 4.2.2 lost the program. A programmed gate does not
  stack with a blank one.
- **Fitting and GUI.** Use a gate or wire item on a pipe to fit it (`ItemGate.useOn`, `ItemPipeWire.useOn`); sneak-use
  takes it off (the gate only when the gate is clicked). Sneaking with an empty hand strips wires red to yellow, then
  the gate (4.2.2 `stripEquipment`; facades go first once #61 adds them). A gate is "clicked" when the hit lies on the
  pipe centre box, on a face whose side is not connected: 4.2.2 ray-traced the gate boxes there. A click on the gate
  opens the gate menu; `BlockPipe.useItemOn` passes block items, gates and wires to their own use, so placing blocks
  against a gated pipe still works (4.2.2 returned false for `ItemPipe` and signs). #55 decides how a module GUI
  shares the remaining clicks.
- **Signals** are 4.2.2's: a broadcasting gate puts 255 on its pipe's colour, each wired pipe takes its strongest
  neighbour minus one, and a pipe with no stronger neighbour drops to 0 and makes its neighbours look again, one
  step per tick. Two pipes share a colour when both have it and are connected to each other, or one is a Structure
  Pipe (#54). Taking a wire off leaves the pipe's strength for that colour as it was, as in 4.2.2.
- **Redstone.** `BlockPipe.getSignal` and `getDirectSignal` both answer 4.2.2 `isPoweringTo` (15 on every side
  except towards a connected pipe while the gate emits); `shouldRedstoneWireConnectTo` is 4.2.2 `canConnectRedstone`
  (dust connects only to a pipe with a gate). `redstonePowered` is `hasNeighborSignal`, read on neighbour change and
  saved. As in 4.2.2, a gate emitting redstone strongly powers the solid blocks around its pipe, and the pipe then
  reads itself as powered: "Redstone Signal On → Redstone Signal" latches.
- **Pipe triggers and actions** (TRANSPORT.md 12) are registered in `TransportRegistry` under their 4.2.2 field
  names (`trigger_pipe_empty`, `trigger_red_signal_active`, `action_red_signal`, `action_energy_pulser`, …); the
  4.2.2 arrays become `action_pipe_color_<colour>`, `action_pipe_direction_<side>`, `action_power_limiter_m<n>` and the
  lists `TransportRegistry.actionPipeColor` / `actionPipeDirection` / `actionPowerLimiter` (by `EnumColor`,
  `Direction`, `PowerMode` ordinal). `TriggerRedstoneInput` is registered in `CoreRegistry` as in 4.2.2
  (`trigger_redstone_active` / `_inactive`). 4.2.2's `PipePowerIron.PowerMode` is `transport/PowerMode`, in FE/t.
- **Pipe Contents** reads `PipeGateHolder.flow`, a `PipeFlow` (`isFlowing`, `itemContent`, `fluidContent`,
  `requestsEnergy`, `isOverloaded`), `PipeFlow.NONE` until #59 (flow records) and #52 (power phases) set it. Power
  Requested is offered on every power pipe (Q21). Structure Pipes get no Pipe Contents triggers.
- **Module actions.** `PipeGateHolder.target` is an `IPipeGateTarget` (4.2.2 `Pipe.getActions` and
  `actionsActivated` overrides): it adds its actions to the gate's list and receives every action with a trigger and
  its state after each resolution. #55 (Director: Pipe Direction; on a power pipe Power Limiter), #57 (Colour Tag and
  Colour Sorter: Pipe Color; Filtered Colour Tag: Extraction Preset) set it when a module is fitted.
- **Autarchic gates.** `EnergyPulser` sends `min(2^(n-1), 64) × 1.01` MJ, now `EnergyPulser.pulseEnergy(n)` FE (10,
  20, 40 … 646), every 10 ticks to its `IPowerReceptor`. Pipes need no power (TRANSPORT.md 12), so on a pipe the
  receptor is null: the actions are offered and light the gate, nothing more. `GateVanilla.addEnergyPulser` takes the
  gate holder's block entity as the receptor when it is an `IPowerReceptor`; the Basic Extractor (#58) needs a holder
  of its own for that.
- **Gate menu.** `ContainerGateInterface` (menu `buildcraftreforged:gate`) and `GuiGateInterface` keep the 4.2.2
  layouts and `gate_interface_1..4.png`. The `GATE_*` packets are a hidden `GateWidget` (server to client: the offered
  lists when the menu opens, then the selection and the trigger-state bits whenever they change) and a
  `PacketGuiReturn` per changed position (`SelectionReturn`). The server refuses a position outside the gate, a
  trigger or action it did not offer, and a parameter on a gate below gold or a trigger without one; a parameter is
  the server's own carried stack (plan section 19.2). The menu closes when its gate comes off.
- **Rendering** is a `BlockEntityRenderer`, `transport/render/PipeRendererTESR` (4.2.2's `renderGatesWires`), the pipe
  type's only renderer: #59's flow renderer goes into the same class. Wire boxes and gate boxes use the 4.2.2 block
  textures as their own textures (`textures/pipe/texture_<colour>_<dark|lit>.png`, `textures/pipe/gate*_<lit|dark>.png`).
  The server computes the render state (wire set and lit per colour, wire connections per colour and side, gate kind,
  series and light) and sends it in the pipe's payload after the connection mask.
- **Icons.** The 4.2.2 trigger and action icons for pipe contents, colours, extraction presets, directions and
  limiters are GUI sprites under `textures/gui/sprites/triggers/`. Descriptions stay 4.2.2's English strings.
- **GameTests** (`GateTests`): `gate_items_and_fitting`, `gate_logic_kinds` (every kind's AND/OR resolution,
  redstone input and output), `gate_wire_signals` (four colours along five pipes, a pipe missing a colour, an
  unconnected pipe), `gate_pipe_contents` (stubbed flow), `gate_module_actions_and_pulser`, `gate_menu` (lists,
  widget data, checked selections, save and load).

### For #74 (chipsets and gate recipes)

- 4.2.2 `BuildCraftSilicon.loadRecipes`, in MJ (× 10 for FE): Assembly Table — Gate = Redstone Chipset, 20 000;
  Iron AND / OR = Iron Chipset + Red Pipe Wire, 40 000; Gold AND / OR = Golden Chipset + Red + Blue Pipe Wire,
  80 000; Diamond AND / OR = Diamond Chipset + Red + Blue + Green + Yellow Pipe Wire, 160 000; each autarchic gate =
  the gate of the same kind + Pulsating Chipset + Iron Chipset, at half the gate's cost (10 000 … 80 000).
  Crafting table, shapeless — Redstone Chipset + an AND gate gives the OR gate and the other way round, for iron, gold
  and diamond, normal and autarchic.
- `BuildCraftTransport.loadRecipes`: Assembly Table — 8 Red, Blue, Green, Yellow Pipe Wire = the dye (`dyeRed` … ,
  so the `c:dyes/*` tags per the Ore Dictionary rule) + redstone + iron ingot, 500 MJ.
- Items: `TransportRegistry.getGateItem(kind, autarchic)`, `getPipeWire(color)`; a recipe result is a blank gate (no
  `gate_settings`). The AND↔OR swap in 4.2.2 dropped nothing because 4.2.2 gates carried no program; a recipe that
  takes a programmed gate should either keep the component or refuse it (open question for #74).
- JEI: the gate category can list `GateSettings`-free stacks only.

### For #52, #54, #55, #57, #58, #59, #61

- #59 / #52: set `pipe.gateHolder.flow` to the pipe's flow record and power state (`PipeFlow`).
- #55 / #57: set `pipe.gateHolder.target` when a module is fitted, clear it when removed; offer the module's actions
  from `addActions` (4.2.2: `PipeItemsIron` six directions except the open side, `PipeItemsLapis` 16 colours,
  `PipeItemsDaizuli` colours and directions, `PipeItemsEmzuli` four presets, `PipePowerIron` seven limits).
- #54: a Structure Pipe carries wire across any neighbour wired with the same colour (`PipeGateHolder.isWireConnectedTo`).
- #58: the Basic Extractor takes gates and wires "like a pipe end"; it needs a `PipeGateHolder`-like holder with
  itself as the `IPowerReceptor` so the autarchic pulser feeds it.
- #61: facades and plugs hide the gate on their side (`PipeRendererTESR.shouldRenderNormalPipeSide`, `SEAM(#61)`),
  drop on removal and come first in `stripEquipment`.

## Engine base (#36)

Ported: `energy/TileEngine`, `TileEngineWithInventory`, `BlockEngine`, `ItemEngine`, plus `EnergyRegistry.engine(...)`.
Not ported: `TileEngineLegacy` (converts pre-4.0 saves, which cannot reach 26.x) and `BptBlockEngine` (deprecated in
4.2.2; blueprints are P6). Recorded 31 Oct 2026.

- **Units.** The engine's own store stays 4.2.2's `float energy`, now in FE (MJ × 10), and so do `currentOutput`
  (FE/t), `getMaxEnergy`, `maxEnergyReceived`, `maxEnergyExtracted`, `minEnergyReceived` (default 20) and
  `getCurrentOutput`. A float keeps the fractions 4.2.2 had: the Stirling engine's PID output, a fuel's
  `powerPerCycle` (already a float FE in #43) and `doWork`'s 95 % of what came in (`useEnergy(10, max) * 0.95F`, so
  10 FE in is 9.5 FE stored; this settles #35's open rounding question). Only whole FE cross to a receiver: the pulse
  is `(long) extracted` and the engine keeps the rest. The thresholds that counted MJ count FE × 10: the idle drain
  is 10 FE in `updateEntity` and 10 FE in `engineUpdate` (20 FE a tick, 4.2.2's 2 MJ), the `PowerHandler` is
  `configure(min, max, 10, maxEnergy)` with perdition `(10, 100)`. Heat, heat level, stages and piston speeds are
  unitless and unchanged. NBT keeps 4.2.2's `energyF`, `heat` and `progress` floats.
- **Orientation is `BlockEngine.FACING`** (all six directions, default up). `TileEngine.getOrientation()` reads it
  and `setOrientation(Direction)` sets it with `Block.UPDATE_ALL` (4.2.2's `markBlockForUpdate` plus neighbour
  notification) and calls `level.invalidateCapabilities`, because the output side refuses Forge Energy. The block
  entity survives the change. Use these where 4.2.2 used the `orientation` field; it is no longer saved or in the
  payload. Placement (`setPlacedBy`) faces up, then `switchOrientation(true)`; the wrench is `IRotatableBlock` →
  `switchOrientation(false)`; structure rotation turns `FACING` (BuildCraft#1771). The first server tick after
  loading or placing re-checks the orientation (4.2.2 `validate`), and a new neighbour is seen once the
  `TileBuffer` refreshes, within 20 ticks, as in 4.2.2.
- **Delivery.** Unchanged for a neighbour that is an `IPowerReceptor`: its receiver's min/max limit the pulse and
  `receiveEnergy(Type.ENGINE, ...)` takes it. A neighbour that is only a Forge Energy `EnergyHandler` (another mod, or
  a power pipe once #52 exposes one) counts as powered too: the pulse is `extractEnergy(0, maxEnergyExtracted())`,
  inserted in one committed transaction, and only what it accepted leaves the engine. An engine never pushes into an
  `IPowerEmitter` that emits towards it (engines facing each other, BUGFIXES BuildCraft#1827). Every FE handed over
  goes into the engine's `FlowMeter` (`meter.energyOut`).
- **Pipes.** `IPipeConnection`: item and fluid pipes do not connect on the output side; power pipes decide
  themselves (`DEFAULT`).
- **Block.** `BlockEngine(Properties, BlockEntitySupplier<? extends TileEngine>)`; `BlockEngine.engineProperties`
  is 4.2.2's Material.iron and hardness 5: metal, `strength(5F)`, needs a pickaxe, no occlusion, not a redstone
  conductor, no suffocation. Only the back face is sturdy (`getBlockSupportShape`, 4.2.2 `isBlockSolidOnSide`); the
  collision and outline shapes stay a full cube, as 4.2.2's bounds. Sneaking passes the click on; holding a pipe
  `BlockItem` places the pipe (4.2.2 `IItemPipe`); otherwise `TileEngine.onBlockActivated(player, side)` runs. Its
  default opens the status panel (plan section 10), so the redstone engine needs nothing; engines with a GUI override
  it. Redstone is read by `neighborChanged` and `initialize` (`hasNeighborSignal`). Red dust particles while
  `isBurning()`. The render shape is left at the default; #39 decides it with the renderer.
- **Overheat.** Still 4.2.2: `addEnergy` while the stage is `OVERHEAT` (heat level 1, a full engine) explodes at the
  block's corner with `explosionRange()` and removes the block; the stage stays `OVERHEAT` once reached.
  `SEAM(#40)` marks the spot for the timer and `engines.explode`.
- **Sync.** The payload is the stage ordinal and `isPumping`; the client runs the piston animation (`progress`,
  `progressPart`, `getPistonSpeed` from the stage) for #39's renderer to read. `guiData` is a 4-slot `ContainerData`
  standing in for `getGUINetworkData`/`sendGUINetworkData`: energy in two 16-bit halves (whole FE, 4.2.2's tenths of
  an MJ), output in tenths of an FE/t, heat in hundredths. A `ContainerEngine` adds it with `addDataSlots(tile.guiData)`
  on both sides (the client menu uses the client tile's `guiData`).
- **Status panel.** `TileEngine` is an `IStatusProvider`: State `OVERHEATING` in stage `OVERHEAT`, `WORKING` while
  redstone-powered and `isActive()`, else `IDLE`; Power is `Power.produced(meter, energy, getMaxEnergy())`; Heat is
  the stage with `Heat.NO_COUNTDOWN`.
- **Not here.** Comparator output and the four heat triggers (`IOverrideDefaultTriggers.getTriggers`, `SEAM(#41)`),
  the overheat timer (#40), the renderer and textures (`getTextureFile` points at `textures/block/base_*.png`, which
  #39 adds).

### For the engines (#37, #38, #44)

- Register with one line in `EnergyRegistry`, below `ENGINE_TILES`:
  `public static final EngineEntry<TileEngineWood> engineWood = engine("engineWood", "engine_wood", TileEngineWood::new);`
  It registers the block (`engine_wood`, translation key `tile.engineWood`), the `ItemEngine` in the machines tab and
  the block entity type, and gives the type the Forge Energy capability and the item capability (the latter only
  answers for a `TileEngineWithInventory`). The tile's constructor is
  `super(EnergyRegistry.engineWood.tile().get(), pos, state)` (`TileEngineWithInventory` adds the slot count).
- Each engine still adds its blockstate (one variant per `facing`), item model, loot table (drops itself), recipe and
  `minecraft:mineable/pickaxe` entry.
- Override `getMaxEnergy`, `maxEnergyReceived`, `maxEnergyExtracted`, `explosionRange`, `isBurning`,
  `getScaledBurnTime`, `getCurrentOutput`, `getTextureFile`, and `minEnergyReceived` where 4.2.2 did (wood: 0), with
  the values × 10 from "For the engines (#36)" under Power. `burn()` and `engineUpdate()` are the hooks 4.2.2 used;
  fuel engines return `OUT_OF_FUEL` from `getStatusState` when they have none.
- **Tests.** GameTests `engine_heat_and_stages` (heat, stages at each quarter, sticky overheat, server and client
  piston speeds, payload, save tags, GUI data, idle drain), `engine_pushes_into_receptor` (10 FE pulses into an
  `IPowerReceptor`, FE refused on the output side and 95 % kept from the back, status lines),
  `engine_pushes_into_fe_and_engines` (a 25 FE Forge Energy block filled by 10 + 10 + 5, a chain at 9.5 FE per
  pulse, engines facing each other idle) and `engine_orientation` (placement, wrench, solid back, pipe overrides,
  explosion when full). They use the test-only `gametest_engine` (10 FE pulses, 1 000 FE, 500 FE intake, explosion
  range 0 so the blast cannot reach neighbouring tests) and two
  receivers in `EngineTests`. Rendering, particles and the right-click in a client are not tested.

## Assembly table (#72)

Ported: `silicon/BlockLaserTable`, `silicon/TileAssemblyTable`, `silicon/gui/ContainerAssemblyTable`,
`silicon/gui/GuiAssemblyTable`, and `Utils.addToRandomInventoryAround`. New: `silicon/AssemblyTableRecipe` (the JSON
recipe type and its serializer), `silicon/AssemblyRecipeManager` (where the table finds its recipes) and
`silicon/gui/SiliconScreens` (screen registration and the client's recipe copy). Recorded 31 Oct 2026.

- **Block.** `assembly_table_block`, block entity type `assembly_table`, menu `assembly_table_block`. 4.2.2's
  `BlockLaserTable` had two metadata variants; each is now its own block, built as `new BlockLaserTable(properties,
  TileX::new)`, so #73 registers `assembly_workbench_block` the same way. `ItemLaserTable` (names per metadata) is
  gone: each block's item is a plain `ItemBlockBuildCraft` with the 4.2.2 key (`tile.assemblyTableBlock`). Material,
  hardness, sound, pickaxe tag and `noOcclusion` as the laser (#71). Shape 16 × 9 × 16 px (4.2.2 block bounds); JSON
  model `block/assembly_table_block` with 4.2.2's `assemblytable_top/bottom/side` textures, side UVs from the bounds
  (v 7–16) as `RenderBlocks` did. Right-click opens the menu unless the player sneaks. Crafting recipe `ORO/ODO/OGO`
  (obsidian, redstone, diamond, diamond gear) as 4.2.2.
- **Energy is FE** (`long`): the recipe's `energy` is FE (4.2.2 MJ × 10). The table is an `ILaserTarget`:
  `requiresLaserEnergy()` while a recipe is current and less than five times its energy is stored, as 4.2.2. When the
  stored energy reaches the recipe's, the table crafts and the store goes to 0 (4.2.2 discards the excess too).
- **Output** goes to a random neighbouring inventory (`Utils.addToRandomInventoryAround`, which now also reaches other
  mods' blocks through the item capability), then would go to a pipe (`SEAM(#50)`, 4.2.2's `addToRandomPipeAround`),
  and what is left drops above the table. The drop is what is left, not 4.2.2's whole output (docs/BUGFIXES.md,
  silicon). 4.2.2 did not put output into engines (`SEAM(#36)` in `Utils`).
- **Items.** The table is a vanilla `Container` of 12 slots, every side (`TileBuildCraft.registerItemHandler`), and
  drops its contents when broken.
- **Recipe selection and queue** are 4.2.2's: `plannedOutput` in click order, `currentRecipe` the one being powered,
  `setNextCurrentRecipe` after each craft or when the current one can no longer be made. The GUI lists the first 8
  recipes the inventory can make (4.2.2's 8 slots); clicking plans or cancels one.
- **Network.** 4.2.2's three packets are replaced:
  - `SELECTION_ASSEMBLY` (client → server): the screen sends `PacketGuiReturn` with the `SelectionMessage` (select flag
    and output stack, through `ItemStack.STREAM_CODEC`) as extra data; `TileAssemblyTable.readGuiData` applies it after
    `PacketHandler` has checked the menu and distance. The screen applies the click locally first, as 4.2.2 did.
  - `SELECTION_ASSEMBLY_GET`/`SEND`: the planned outputs and the current output are part of the tile's network payload,
    sent to the players tracking the chunk whenever they change (next tick) and with the chunk. Clients therefore also
    see other players' clicks and the current recipe moving on, which 4.2.2 did not send.
  - The energy numbers (required, stored, rate) are 4.2.2's six 16-bit progress bars as vanilla data slots. FE are
    whole numbers and are sent as they are; the rate is FE/t × 100. The ledger shows FE and FE/t.
- **Recipes are keyed by output**, as 4.2.2 did (item id and damage then; item and components now): in saved data
  (`planned`, a list of stacks, and `recipe`, the current one), in the selection message and in the payload. Saved
  data and the payload are looked up again when the level is known (loading now happens before the block entity has
  a level) and whenever the recipe objects change (datapack reload, joining a server). Saved tags: `items`,
  `energyStored` (FE, a long; 4.2.2 saved MJ as a float), `planned`, `recipe`.
- **Not done here:** the 4.2.2 recipes themselves (their items do not exist yet; see the table below), JEI categories
  (#74), the status panel provider (plan section 10, #26), the builders' `BptBlockInventory` for the table (P6).

### Recipe format (`buildcraftreforged:assembly`)

```json
{
  "type": "buildcraftreforged:assembly",
  "energy": 5000,
  "inputs": [
    { "tag": "c:dyes/red", "count": 1 },
    { "item": "minecraft:redstone" },
    { "item": "minecraft:iron_ingot" }
  ],
  "result": { "id": "buildcraftreforged:red_pipe_wire", "count": 8 }
}
```

- `energy` (int, at least 1): FE to craft once. 4.2.2 recipes: MJ × 10.
- `inputs` (1 to 12 entries, the table's slot count): each is an exact item (`item`, 4.2.2's `ItemStack` input,
  components ignored as 4.2.2 ignored NBT) or an item tag (`tag`, 4.2.2's Ore Dictionary name), with `count`
  (default 1). Inputs are matched by total count across the 12 slots, in any slots. Ore names become `c:` tags with a
  slash (`dyeRed` → `c:dyes/red`, plan section 6).
- `result`: an item stack template (`id`, optional `count` and `components`), as vanilla recipe results.
- Files go in `data/<namespace>/recipe/`. The table lists recipes ordered by recipe id, then any added in code to
  `AssemblyRecipe.assemblyRecipes` (the api list, for other mods). A data pack can add, replace or remove recipes; the
  type is sent whole to clients (`OnDatapackSyncEvent.sendRecipes`), whose GUI lists them.
- Recipes load before item components are bound, so `AssemblyTableRecipe` keeps the templates and
  `AssemblyRecipeManager` builds the 4.2.2 `AssemblyRecipe` objects on first use after each load, one list per side.
- The repository has no datagen providers yet; recipes are written by hand like the crafting recipes.

### The 4.2.2 assembly recipes, for the issues that add their items

Every recipe 4.2.2 registered (`BuildCraftSilicon.loadRecipes`, `BuildCraftTransport` post-init, `ItemFacade.addFacade`),
with energy × 10. The registry names are PARITY.md's, some of them placeholders. Each issue adds the JSON files with
its items.

| Output | Inputs | FE | Issue |
|---|---|---|---|
| Redstone Chipset (`redstone_chipset_0`) | redstone | 100000 | #74 |
| Redstone Iron Chipset (`redstone_chipset_1`) | redstone, iron ingot | 200000 | #74 |
| Redstone Golden Chipset (`redstone_chipset_2`) | redstone, gold ingot | 400000 | #74 |
| Redstone Diamond Chipset (`redstone_chipset_3`) | redstone, diamond | 800000 | #74 |
| 2 Pulsating Chipsets (`redstone_chipset_4`) | redstone, ender pearl | 400000 | #74 |
| Gate (`pipe_gate_0`) | Redstone Chipset | 200000 | #74 |
| Iron AND / OR Gate (`pipe_gate_1`, `_2`) | Iron Chipset, red pipe wire | 400000 each | #74 |
| Gold AND / OR Gate (`pipe_gate_3`, `_4`) | Golden Chipset, red and blue pipe wire | 800000 each | #74 |
| Diamond AND / OR Gate (`pipe_gate_5`, `_6`) | Diamond Chipset, red, blue, green and yellow pipe wire | 1600000 each | #74 |
| Autarchic Gate (`pipe_gate_autarchic_0`) | Gate, Pulsating Chipset, Iron Chipset | 100000 | #74 |
| Autarchic Iron AND / OR (`_1`, `_2`) | that gate, Pulsating Chipset, Iron Chipset | 200000 each | #74 |
| Autarchic Gold AND / OR (`_3`, `_4`) | that gate, Pulsating Chipset, Iron Chipset | 400000 each | #74 |
| Autarchic Diamond AND / OR (`_5`, `_6`) | that gate, Pulsating Chipset, Iron Chipset | 800000 each | #74 |
| 8 Red / Blue / Green / Yellow Pipe Wire | 1 of `c:dyes/red` (`blue`, `green`, `yellow`), redstone, iron ingot | 5000 each | #60 |
| 8 Plugs | 1 Cobblestone Structure Pipe | 10000 | #61 |
| 6 Facades of a block | 3 Cobblestone Structure Pipes, the block | 80000 | #61 |

4.2.2 created one facade recipe per facade-able block at start-up (`addFacade`); with data-driven recipes that is
either one JSON per block or a recipe type of its own for #61 to decide.

### Tests

GameTests `assembly_laser_crafting` (a laser under the table powers a planned recipe selected through the GUI return
path: crafts after 400 FE at 40 FE/t, inputs used, output dropped, GUI data slots read back on a client copy),
`assembly_queue_and_tags` (two planned recipes in turn, output into a chest, a tag input takes exactly its count, the
five-times energy cap) and `assembly_selection_save_and_sync` (JSON → 4.2.2 input array, cancelling, a malformed
selection refused, payload to a client copy, save and load, drop on break). Their recipes are test-only
(`gametest_data`: `gametest_assembly_items`, `gametest_assembly_tag`) because no 4.2.2 output item exists yet.
Drawing, clicking in the screen and the client's recipe sync need a client and are not tested.

## Owner tracking and the owner-profile fake player (#27)

New: `core/utils/BuildCraftPlayer`. Changed: `core/TileBuildCraft`, `core/utils/BlockUtil`. Decision in the API mapping
table ("No fake player in 4.2.2", decided 29 Oct 2026).

- **Recorded at placement.** `TileBuildCraft.onBlockPlacedBy` keeps the 4.2.2 `owner` name and adds the placer's UUID,
  saved as `ownerId` (`UUIDUtil` int array). `getOwnerProfile()` returns `new GameProfile(ownerId, owner)`, or null
  when no player placed the block (a dispenser, a structure, a world from before #27). The name is the one at
  placement; a later rename does not change it, and claim mods match on the UUID anyway.
- **The fake player.** `BuildCraftPlayer.get(tile)` (server side, else null) or `get(ServerLevel, profile)` returns
  `FakePlayerFactory.get(level, profile)`. Without a profile it uses `BuildCraftPlayer.DEFAULT`, 4.2.2's
  `[BuildCraft]` with a fixed name-based UUID that belongs to no real player, so claim mods treat ownerless machines
  as strangers. `BlockUtil.getBuildCraftPlayer(level)` returns that default player. `FakePlayerFactory` caches one
  player per level and profile and drops them when the level unloads; do not keep the player in a field, ask for it
  per action.
- **Breaking.** `BuildCraftPlayer.canBreak(player, level, pos)` fires `BreakBlockEvent` through
  `CommonHooks.fireBlockBreak` with `GameType.SURVIVAL`, as a player breaking by hand would. In NeoForge 26.x the
  event is `net.neoforged.neoforge.event.level.block.BreakBlockEvent`; the plan's `BlockEvent.BreakEvent` is its old
  name. A cancelled event means a protected block: nothing is broken. `BlockUtil.breakBlock(..., player)` asks it
  first and now returns whether it broke the block (4.2.2 returned nothing); the overloads without a player use the
  default player. `getItemStackFromBlock(..., player)` takes the loot with that player as breaker but asks no one, so
  call `canBreak` before it.
- **Placing.** `BuildCraftPlayer.placeBlock(player, level, pos, state)` sets the block, fires `EntityPlaceEvent` as
  vanilla does after a placement, and restores the old block through its `BlockSnapshot` when cancelled. Neighbour
  updates of the short-lived block have already happened by then; vanilla's item path avoids that by capturing
  snapshots. `BuildCraftPlayer.useItemOn(player, stack, against, face)` is 4.2.2's `onItemUse` with the BuildCraft
  player: the stack goes in the player's main hand for the call, `ItemStack.useOn` runs NeoForge's own placement
  checks and event, and the hand is emptied again. The stack is used up as the item decides.
- **For #66 (mining well) and #67 (quarry).** 4.2.2 took the drops with `getItemStackFromBlock` and then set the block
  itself. Port as: `FakePlayer owner = BuildCraftPlayer.get(this)`; if `!BuildCraftPlayer.canBreak(owner, level,
  pos)`, stop digging (blocked, status reason for #63) and leave the block; else `getItemStackFromBlock(..., owner)`
  and set the block (pipe or air) as 4.2.2 did. The quarry plan says it "stops in a protected claim": stop, do not skip.
- **For #78 (builder) and #79 (filler).** `FillerPattern.breakBlock` calls `BlockUtil.breakBlock(..., lifespan,
  owner)`; `fillerDestroy`'s `setBlockToAir` must ask `canBreak` first. The filler's `onItemUse` becomes
  `BuildCraftPlayer.useItemOn(owner, stackToPlace, pos.below(), Direction.UP)` (4.2.2 side 1 is up). Schematic
  placement that sets block states directly goes through `placeBlock`; a refused slot is skipped or stops the build as
  the builder port decides.
- **Not covered.** Spawn protection (`MinecraftServer.isUnderSpawnProtection`) is checked by vanilla's player
  interaction code, not by these events, so machines may still break and place near spawn, as in 4.2.2.
- **Tests.** GameTests `owner_recorded_and_saved` (profile and Owner line after placement, save and load, the fake
  player's UUID, the default player for an ownerless block), `owner_break` (a listener standing in for a claim mod
  cancels `BreakBlockEvent` at one position: that block stays and `breakBlock` returns false, the other is broken and
  drops; the listener sees the owner's UUID) and `owner_place` (the same for `placeBlock` and `useItemOn`, with the
  stack not used up when refused). Real claim mods (FTB Chunks and similar) need a client and were not tried.

## Item routing walk and per-segment budgets (#50)

New code from `docs/TRANSPORT.md` 4, 5 and 6, package `buildcraftreforged.transport.route`; the 4.2.2
`PipeTransportItems` was read for behaviour only.

- **Walk.** `PipeWalk.walk(network, start, in, sourcePos, rules, random, maxVisits)` is 4.2 step by step, iterative (no
  Java recursion on long lines). Candidates in side order, then a uniform random permutation from the `RandomSource`
  (the level's; tests pass a seeded one); an accepting endpoint ends the walk, a pipe is entered, a dead end is backed
  out of (Q1) but stays visited. Returns a `Route(path, endpoint, sink)` or null (no destination, or more than
  `maxVisits` pipes, Q20). `PipeWalk.Rules` is where the kinds and modules differ: `accepts(endpoint)` (the simulated
  insert), `canEnter(node)`, `isSink(node)` (default: the Void module) and `allowsOutput(node, side)`.
- **Source exclusion by block.** No endpoint at the source block's position is a destination, on any face, not just
  the face the transfer came from. A chest touching a line twice never receives its own items back. Decided
  2026-10-31.
- **Budgets.** `PipeBudget` is 5.1–5.2: a `long` in the kind's unit, lazily refilled by game time, capped at
  `PipeBudget.cap(refill, burstSeconds, costPerUnit)`, started at 0 when the pipe loads (`TilePipe.onLoad`; a budget
  never started starts at its first read). It is a NeoForge `SnapshotJournal`, so spending is part of a transaction:
  a simulated transfer is an aborted transaction and leaves budgets as they were (6.2). `TilePipe.getBudget()` and
  `TilePipe.getTierStats()` give each pipe's. Not saved.
- **Transfers.** `ResourceRouter<R extends Resource>` is the generic transfer of 5.3 and 6.4, for any
  `ResourceHandler<R>`: walk, then `n = min(offered, simulated insert at the destination, min over the path of
  floor(budget / costPerUnit))`, insert `n` with `ResourceHandlerUtil.insertStacking` (matching stacks first, as 4.2.2
  `Transactor.add`), spend `n × costPerUnit` on every pipe of the path, all in the caller's transaction. It returns
  `TransferResult(moved, reason, route)` with the reasons of 6.3. A Void sink takes `min(offered, budgets)`.
- **No overflow stack.** The 26.x transfer API is transactional, so a transfer is one transaction: the destination
  insert, the budgets and the source extract commit together or not at all, and a handler that takes less than it
  promised just makes the transfer smaller. The `overflow` field of 3.1 and step 5 of 6.4 (Q19) are therefore never
  needed and not implemented; nothing is left between source and destination. Decided 2026-10-31.
- **Items.** `ItemRouting.ROUTER` costs 20 units per item (an item-tick), refill = the tier's `itemsPerSecond`.
  `ItemRouting.push(level, network, node, in, resource, amount, transaction)` is a push from the block beside the pipe;
  `ItemRouting.extract(level, network, node, facing, source)` is one Extractor attempt (8.2, Q13): up to
  `floor(budget / 20)` items from the first slot whose stack has a destination, at most one successful transfer, the
  pull and the insert in one root transaction. NO_CAPACITY stops the scan (a destination exists); NO_DESTINATION tries
  the next slot.
- **Pipe capability (6.2).** Item pipes register `Capabilities.Item.BLOCK` (`TransportRegistry`, through
  `TileBuildCraft.registerItemHandler`); `TilePipe.getItemHandler(side)` returns a `PipeItemHandler`: one virtual,
  always-empty slot; `insert` routes at once and returns what reached a destination; `extract` gives nothing. It refuses
  on a plugged side and on a side facing another item pipe, decided at call time, so no capability invalidation is
  needed when neighbours change. A connection on that side is not required (6.2 says "every side whose neighbour is not
  a pipe of the same kind"). Hoppers and other mods push through it; `TilePipe.injectItem(stack, doAdd, from)` is the
  same push in its own root transaction.
- **Source tick (6.1).** `PipeSources` runs at `LevelTickEvent.Post` on the server: for each valid network with
  `sourceNodes`, one attempt per source starting at `sourceCursor`, then the cursor moves on by one. A source pipe asks
  for its network on its own tick (`TilePipe.updateEntity`), so its network exists by then. Only the item Extractor
  does anything yet, and until #55 saves a facing it pulls from the first side, in side order, that leads to an
  endpoint with an item handler.
- **Config** (`transport` section): `burstSeconds` (0.05–10, 1.0) and `maxRouteVisits` (64–65 536, 4096), read once
  per transfer through `ItemRouting.settings()` (defaults before the config loads). `fluidLockTicks` is #51's.
- **Tests.** Unit (`PipeRoutingTest`, on a stub grid with a test `Resource` and a transactional stub chest): straight
  line and path, 10 000-walk 50 % ± 2 % splits at a junction of two chests and at a chest-versus-pipe junction, dead
  end backed out of, loop, full chest, source never the destination, `maxRouteVisits`, Void sink; every worked
  example of 5.4 tick by tick (wood, iron, gold, quartz, ender, bottleneck, shared trunk, burst), config change and cap
  shrink; partial, NO_CAPACITY with nothing spent, simulation changes nothing, budgets spent only on the path.
  GameTests (`ItemRoutingTests`, padded area): chest → Extractor → chest with two item kinds; a hopper pushing through
  the pipe capability; a junction with a chest, a far chest, a dead end and a full chest (200 items, all in the first
  two, each at least 70); budgets on a wood line and a gold–wood–gold bottleneck, simulated and real pushes and
  `injectItem`; and the 10-tier throughput test, one per tier, which measured exactly `2 × itemsPerSecond` over a
  40-tick window for every tier (tolerance ± 1, plus one tick's worth from 20 items/s up). Every test ends with no
  item entity in its area.

### For #51 (fluids), #52 (power), #55–#57 (modules), #58 (Basic Extractor) and #59 (flow records)

- **#51.** Make a `ResourceRouter<FluidResource>` with `costPerUnit` 1, refill `fluidPerTick`, handlers from the
  endpoint caches (`Capabilities.Fluid.BLOCK`), like `ItemRouting.ROUTER`, and pass `PipeWalk.Rules` with
  `canEnter` for the fluid lock (7.3); `ResourceRouter.push` takes the walk rules from its own lambda today, so add an
  overload or a `Rules` parameter there. The budget object (`TilePipe.getBudget()`) is the same for every kind. Add the
  fluid Extractor at the `SEAM(#51)` in `PipeSources.tickSource` and a fluid capability next to the item one.
- **#52.** Power uses the budget (`PipeBudget`, cost 1 FE) but not the walk (7.4 builds a tree). `PipeBudget.available`
  and `spend(…, transaction)` work the same.
- **#55–#57.** Modules plug into `PipeWalk.Rules`: `allowsOutput` (Director, Colour Sorter), `isSink` (Void already),
  and a candidate filter for Sorter and Colour Sorter (8.5, 8.9) still has to be added to `Rules` and called between
  steps 4 and 6. The Extractor's saved facing replaces the first-handler choice in `PipeSources.tickSource`
  (`SEAM(#55)`); its pull is `ItemRouting.extract`. Filtered Extractor: filter slots before calling the same push
  inside its own transaction, as `extract` does.
- **#58.** The Basic Extractor pulse is `ItemRouting.extract`-shaped: a walk from the pipe on its pipe face with
  `in` = the side facing it; cap `want` by the pulse before the push.
- **#59.** Every successful transfer passes `ResourceRouter.push` where `SEAM(#59)` sits; `TransferResult.route()`
  has the path (node indices), and `route.endpoint()` / `route.sink()` the exit. Record flow there, journaled in the
  same transaction, so simulations record nothing.

## Refinery (#68)

Ported `factory/BlockRefinery`, `TileRefinery`, `gui/GuiRefinery`, `gui/ContainerRefinery`, `render/RenderRefinery`.
New: `factory/RefineryRecipe` (the recipe type), `factory/RefineryRecipeManager` (lookup and client sync),
`factory/gui/FactoryScreens` (client registration). `BptBlockRefinery` waits for the builder (#78). Recorded
2026-10-31.

### Recipe format

4.2.2 registered its one refinery recipe in Java (`RefineryRecipes.addRecipe` in `BuildCraftEnergy.preInit`). Recipes
are JSON files of type `buildcraftreforged:refinery` now, under `data/<namespace>/recipe/`. The shipped one is
`data/buildcraftreforged/recipe/refinery/fuel.json`, 4.2.2's oil to fuel:

```json
{
  "type": "buildcraftreforged:refinery",
  "ingredient1": { "fluid": "buildcraftreforged:oil", "amount": 1 },
  "result": { "fluid": "buildcraftreforged:fuel", "amount": 1 },
  "energy": 120,
  "delay": 1
}
```

| Field | Required | Meaning |
| --- | --- | --- |
| `ingredient1` | yes | `{"fluid": id, "amount": mB}`: the first input (amount at least 1, not `minecraft:empty`) |
| `ingredient2` | no | a second input in the same form; the recipe then needs both, one per input tank |
| `result` | yes | the output fluid and amount per cycle |
| `energy` | yes | FE per cycle (4.2.2 MJ × 10), at least 0 |
| `delay` | no, 1 | ticks between cycles, at least 1 |

- **Rate.** 4.2.2's oil recipe is 1 mB of oil to 1 mB of fuel for 12 MJ every tick: 120 FE per mB here, so a
  refinery makes 20 mB of fuel per second while it gets at least 120 FE/t (plus the 10 FE/t it loses). Power settings
  are 4.2.2's `configure(50, 150, 25, 1000)` and `configurePowerPerdition(1, 1)` × 10: receives up to 1500 FE per
  insert, stores 10000 FE, loses 10 FE per tick.
- **Fluids are an id and an amount**, not a `FluidStack` codec (no components): the stacks are built on first use,
  after fluid data components are bound (see "Fuel and coolant registry (#43)" for why a stack cannot be decoded early).
- **Lookup.** `RefineryRecipeManager.findRefineryRecipe(level, liquid1, liquid2)` replaces 4.2.2's
  `RefineryRecipes.findRefineryRecipe`: first the JSON recipes of the level's side, kept in a `TreeSet` in 4.2.2's
  `Recipe.compareTo` order (two-ingredient recipes before the one-ingredient recipe on the same first fluid; a recipe
  with the same ingredients as one with a smaller recipe id is dropped, as 4.2.2's set dropped later additions), then
  the code-registered `RefineryRecipes` set, which stays for other mods. Each JSON recipe builds a 4.2.2
  `RefineryRecipes.Recipe` (new `RefineryRecipes.createRecipe`, which does not add it to the code set), so matching
  (`Recipe.matches`, loose as in 4.2.2) is unchanged.
- **Client copy.** The GUI shows the result of the chosen filters, so the type is sent to clients on data pack sync
  (`OnDatapackSyncEvent.sendRecipes`) and kept from `RecipesReceivedEvent`, forgotten on logout. Same pattern as the
  assembly table (#72).
- **Data packs** add recipes with new files and override or remove the shipped one at the same path. Hand-written JSON
  like the rest of `data/` (no datagen providers yet).

### Block, tile and GUI

- **Block.** `refinery_block`, block entity `refinery`, menu `refinery`. `Material.iron` + `setHardness(5F)` → metal
  sound and map colour, `strength(5F)`, `requiresCorrectToolForDrops`, `minecraft:mineable/pickaxe`, `noOcclusion`.
  Metadata → `FACING` (horizontal), set on placement to face the placer as 4.2.2's `onBlockPlacedBy`; the wrench turns
  it N → W → S → E as 4.2.2's `rotateBlock`. Recipe `RTR/TGT` (redstone torch, tank, diamond gear) as 4.2.2. Not a
  `BlockBuildCraft` (4.2.2 extended `BlockContainer`), so it does not light up while active.
- **Right-click.** A sneaking wrench resets the three tank filters; any held item except an empty bucket is tried as a
  fluid container that fills the refinery (never drains it); otherwise, or when that fails, the GUI opens.
- **Fluid capability** (`Capabilities.Fluid.BLOCK`, any side): three indices (tank1, tank2, result). Insert reaches
  tank1 and tank2 only, so an insert fills tank1 and then tank2 as 4.2.2's `fill`; extract reaches the result only,
  as 4.2.2's `drain`. The tanks are 4000 mB `SingleUseTank`s: the first fluid a tank takes becomes its filter.
- **Energy capability** through `PowerEnergyHandler` on every side.
- **Ticking.** `updateEntity` now calls `TileBuildCraft.updateEntity` (4.2.2 skipped it), so perdition runs each tick
  instead of only when energy arrives or is used; the energy lost is the same.
- **Filters.** 4.2.2's progress bars 0 and 1 (filter fluid ids) are two `DataSlot`s on the menu: 0 is no filter, a
  fluid is its registry id + 1. 4.2.2 never sent a cleared filter, so the client kept showing it; it does now. The
  GUI's `REFINERY_FILTER_SET` packet is a `PacketGuiReturn` carrying both filters, checked by `PacketData` bounds.
  Clicks as 4.2.2: left with a filled container sets the filter to its fluid, shift-left clears it, right sets it to
  the tank's current fluid. `GuiAdvancedInterface.FluidSlot` now draws the fluid's still sprite tinted with its colour
  (the SEAM(#24) it carried).
- **Status panel** (plan section 10, new): Working; No power while a recipe can run but the store holds less than its
  energy; Blocked with reason "Output blocked" while the result tank has no room for a cycle (`IMachine.isBlocked`,
  which 4.2.2 did not have; the refinery just stopped). Flow counts mB consumed and made and FE used per cycle. The
  GUI adds the status ledger. No red-light overlay: the model is drawn by the tile renderer.
- **Renderer.** `RenderRefinery` keeps 4.2.2's boxes (a 8 × 16 × 8 tank drawn three times, four 8 × 4 × 4 magnets
  picked by animation speed) as baked `ModelPart`s with the same texture offsets on `textures/block/refinery.png`
  (64 × 32), the same translations, scale and FACING angle, and the three fluids through `FluidRenderer.renderFluid`.
  The block shape is invisible; `refinery_back` is the particle texture. 4.2.2's inventory render is the item model
  `block/refinery_block_inventory`: the three tanks and two resting magnets as JSON elements with UVs from the same
  box layout.
- **Tests.** GameTests `refinery_oil_to_fuel` (no fuel without power; exactly 20 mB in 20 powered ticks; 10 mB for
  1300 FE in 10 ticks on stored energy; inputs and outputs through the capability), `refinery_filters_and_blocked`
  (a filter sends oil to tank2, the GUI return with a refused id, the data slots on a client copy, the sneaking wrench,
  a full result tank blocks and draining it resumes, save and load) and `refinery_recipes` (the shipped recipe's
  values, and a two-ingredient water + lava recipe from the GameTest data pack consuming both inputs; placement
  facing).
- **Not checked without a client:** the renderer (geometry, texture orientation, animation, fluid levels, lighting),
  the item model's UVs, the GUI's drawing, fluid-slot sprites and mouse clicks, the right-click → `openMenu` path
  (mock players cannot receive the open-screen packet), and the client recipe copy.

## Engine renderer and textures (#39)

Ported: `energy/render/RenderEngine` (4.2.2's `TileEntitySpecialRenderer` and `IInventoryRenderer` in one class, as
4.2.2) and the engine half of `EnergyProxyClient.registerTileEntities`. Textures copied unchanged from
`reference/.../textures/blocks`: `base_wood`, `base_stone`, `base_iron`, `chamber`, `trunk_blue`, `trunk_green`,
`trunk_yellow`, `trunk_red` (64 × 32, 4.2.2 `ModelBase`'s texture size) and `engineWoodBottom`, `engineStoneBottom`,
`engineIronBottom` as `engine_*_bottom` (16 × 16). Recorded 31 Oct 2026.

- **Geometry.** The four `ModelRenderer` boxes keep their sizes and texture offsets: base `(0, 1)` 16 × 4 × 16,
  moving box `(0, 1)` 16 × 4 × 16, chamber `(1, 1)` 10 × 2 × 10, trunk `(1, 1)` 8 × 12 × 8. They are baked
  `ModelPart`s with no pose; 26.x `ModelPart.Cube` lays out UVs as 1.6.4 `ModelBox` did. The rotation point (8, 8, 8)
  and `angleMap` rotation (Z, then Y, then X, as `ModelRenderer.render`) go on the pose stack per part, not on the
  shared parts, because submitted parts are drawn after `submit` returns.
- **Animation.** `render` is 4.2.2's: `step` rises from 0 to 7.99 px over the first half of `progress` and falls back
  over the second; the moving box is translated `step / 16` along the facing; one chamber slice is drawn every 2 px
  up to `step + 2`, so the bellows grows and shrinks with the piston. The trunk texture follows the stage (blue, green,
  yellow, red; overheat draws red). No partial-tick interpolation, as 4.2.2.
- **What the renderer reads.** `energyStage` and `isPumping` from the tile payload (#36), `progress` from the
  client's own tick (it advances at `getPistonSpeed()` of the synced stage while pumping), the facing from
  `BlockEngine.FACING` and the base texture from `getTextureFile()`. All of it is copied into `RenderEngine.State` in
  `extractRenderState`.
- **Render types.** 4.2.2 drew with lighting and face culling on and blending off: `RenderTypes.entitySolid` on each
  texture, the block's packed light, no overlay, plus the breaking overlay (`submitCrumblingOverlay`), which 4.2.2 did
  not have for engines.
- **Chamber texture.** Plan section 6 listed the engine chamber under animated textures. In 4.2.2 `chamber.png` is a
  single still frame; the moving part is the stacked slices above. No `.png.mcmeta` was added; the plan row is corrected.
- **Block model.** The block keeps `RenderShape.MODEL` with an element-less model, as vanilla's chest:
  `block/engine` (parent `minecraft:block/block`, for the item display transforms) and `block/engine_wood`,
  `engine_stone`, `engine_iron`, which only name the 4.2.2 `BlockEngine.getIcon` texture as `particle` (breaking and
  landing particles). Everything visible is the block entity renderer, as 4.2.2's `RenderingEntityBlocks` drew nothing
  for an engine in the world. This replaces #36's note that each engine adds one blockstate variant per facing: one
  `""` variant is enough.
- **Item.** 4.2.2 put one `RenderEngine(TEXTURE)` per engine metadata into `RenderingEntityBlocks.blockByEntityRenders`.
  Now the item model is a `minecraft:special` model of type `buildcraftreforged:engine` with the base texture as its
  `texture` field (`RenderEngine.ItemRenderer`, registered in `EnergyProxyClient`). It draws 4.2.2's
  `inventoryRender`: blue trunk, facing up, progress 0.25.
- **Registration.** `EnergyProxyClient.registerRenderers` binds `RenderEngine` to every type in
  `EnergyRegistry.engineTiles()`, so an engine registered with `EnergyRegistry.engine(...)` gets the renderer without
  further code.

### Engine textures, for #37, #38 and #44

Each engine plugs in one row. The trunk and chamber textures are shared.

| Engine | `getTextureFile()` | Item model `texture` | Block model (particle) |
|---|---|---|---|
| Redstone (`engine_wood`, #37) | `TileEngine.WOOD_TEXTURE` | `buildcraftreforged:textures/block/base_wood.png` | `block/engine_wood` (`engine_wood_bottom`) |
| Stirling (`engine_stone`, #38) | `TileEngine.STONE_TEXTURE` | `buildcraftreforged:textures/block/base_stone.png` | `block/engine_stone` (`engine_stone_bottom`) |
| Combustion (`engine_iron`, #44) | `TileEngine.IRON_TEXTURE` | `buildcraftreforged:textures/block/base_iron.png` | `block/engine_iron` (`engine_iron_bottom`) |

The textures and block models are in place. Each engine adds two files:

- `blockstates/engine_wood.json`: `{"variants": {"": {"model": "buildcraftreforged:block/engine_wood"}}}`
- `items/engine_wood.json`:
  `{"model": {"type": "minecraft:special", "base": "buildcraftreforged:block/engine_wood", "model": {"type": "buildcraftreforged:engine", "texture": "buildcraftreforged:textures/block/base_wood.png"}}}`

### Tests and what is not checked

- GameTest `engine_render_state`: a running engine's payload gives the client the stage (yellow at 60 %), the pumping
  flag and the matching piston speed; overheat syncs and stops the client piston; the facing reaches the client tile
  through the block state; every texture the renderer, the item model and the block models name is in the jar at
  its 4.2.2 size.
- **Not checked without a client:** the drawing itself (box orientation for all six facings, the UVs, face culling
  and winding, the chamber stacking, light), the item render in the GUI, hand and ground, and the particle texture.
  Tick the PARITY "renders" columns when an engine has been looked at in game.

## Pipe modules: framework, Extractor, Filtered Extractor and Director (#55)

New code from `docs/TRANSPORT.md` 8.1–8.4, package `buildcraftreforged.transport.module`; the 4.2.2 `PipeItemsWood`,
`PipeLogicWood`, `PipeItemsEmerald`, `GuiEmeraldPipe`, `ContainerEmeraldPipe`, `PipeItemsIron`, `PipeLogicIron` and
`PipePowerIron` were read for behaviour only.

- **Type and object.** `PipeModuleType` (the enum of #48) stays the type the connection rule and the graph read
  (`isSource`, bans, Isolator); it also has `accepts(PipeType)`, the module/kind matrix of 8.1 (Q8). `PipeModule` is the
  object a pipe holds: `type`, settings and hooks. `PipeModule.create(type)` makes one with default settings; types with
  no behaviour yet (#56, #57) are a plain `PipeModule`, so `TilePipe.setModuleType` works for every type.
- **Hooks** (all no-ops by default; the pipe calls them on the server):
  `onInstalled(pipe)`, `onRemoved(pipe)`; `onNeighbourChange(pipe)`, after every connection recompute (neighbour block
  change, capability invalidation, load, plug or module change); `allowsOutput(pipe, side)`, read live by the walk;
  `tickSource(level, network, node, pipe)`, one attempt per tick for source types, called by `PipeSources`;
  `onWrench(pipe, player)`, a wrench without sneaking; `gateTarget(pipe)`, the `IPipeGateTarget` of #60 that the pipe
  puts in `gateHolder.target` when the module is fitted or loaded; `getMenuProvider(pipe)`, the GUI opened with an
  empty hand (a click on a gate opens the gate first);
  `save(ValueOutput)` / `load(ValueInput)`; `toItem(registries)` / `PipeModule.fromItem(type, stack, registries)`.
- **Pipe.** `TilePipe.getModule()`, `setModule(module)` (null removes; calls `onRemoved` / `onInstalled`, recomputes
  connections, rebuilds the network, syncs), `setModuleType(type)` (a new default module), `removeModule()` (returns the
  item with its settings). Saved as `module` (the type, as before) and `module_settings` (the module's own values).
  Only the type reaches the client, in the update payload after the mask; the client holds a default module of that
  type. Breaking the pipe drops the module item with its settings (`TilePipe.destroy`).
- **Fitting (Q18).** `ItemPipeModule.useOn` fits its module to a pipe of an accepted kind with no module and uses up
  one item. `BlockPipe.useItemOn` on a pipe with a module: a wrench calls `onWrench`; a sneaking wrench takes the module
  off and puts its item in the player's inventory (dropped at the player when full); a block item places as usual; any
  other item falls through to `useWithoutItem`, which opens the module's GUI. The item keeps the settings in the
  `buildcraftreforged:module_settings` data component (`CustomData`), written by `save`, so a module carries its filters,
  mode and facing from pipe to pipe.
- **Walk hook.** `PipeWorld.Pipe.allowsOutput(side)` (default true; `TilePipe` asks its module) is what
  `PipeWalk.Rules.allowsOutput` now returns by default, so every router (items now, fluids in #51) honours the
  Director. A facing change needs no network rebuild.
- **Source hook.** `PipeSources.tickSource` hands each source node to its pipe's module. The Extractor finds its source
  handler from the network endpoint on its facing; `ItemRouting.extract` gained an overload with a
  `Predicate<ItemResource>` for filtered pulls, and `ItemRouting.handler(endpoint)` is public.
- **Extractor.** Facing saved as `facing` (-1 none, else `get3DDataValue`). Valid facing: a connected side whose
  neighbour is not a pipe and that `PipeManager` (ported to `api/transport`, the 4.2.2 exclusion list of
  `IExtractionHandler`s) allows. `ModuleFacing.next` is the 4.2.2 search `facing+1 … facing+6`; no facing starts at
  `UP`, like 4.2.2's metadata 6. On install and on every connection recompute an invalid facing switches; the wrench
  switches. When no side is valid the facing becomes none (4.2.2 kept the stale metadata; decided 2026-10-31, so an
  excluded or vanished block is never pulled from). Fluid extraction is `SEAM(#51)` in `ModuleExtractor.tickSource`.
- **Filtered Extractor.** Extends the Extractor. Nine ghost filters (`SimpleInventory`, stack size 1), the cursor
  (`FilterCursor`, level-free) and the mode (`BLOCKING` default, `NONBLOCKING`), saved as `filters`, `currentFilter` and
  `state` (the 4.2.2 keys). One attempt: the current filter's item via the filtered `extract` ("same item" =
  `ItemResource.is(item)`, components ignored); success moves the cursor; non-blocking tries each following filter
  once; `NO_CAPACITY` stops the attempt without moving the cursor (4.2.2 did nothing without energy). GUI:
  `ContainerFilteredExtractor` (menu type `buildcraftreforged:filtered_extractor`, the 4.2.2 slot layout) and
  `transport/client/GuiFilteredExtractor` on the 4.2.2 `filter_2.png`; the mode reaches the client through a data
  slot and comes back with `PacketGuiReturn` through a handler on the menu (the pipe's position, valid while the
  module is still on the pipe), not through `TilePipe`, so gates (#60) can give the pipe its own GUI.
- **Director.** Facing saved as `facing`; `allowsOutput(side) = side == facing`, none = nothing leaves. Valid facing: a
  connected side whose neighbour is not a pipe with an Extractor or Filtered Extractor. Installed, it starts at `UP`
  and moves to the first valid side (4.2.2 `onBlockPlaced`); a Director fitted before its pipe's connections settle
  gets its first valid side on the next recompute. Wrench: `switchPosition`. Gate: it offers one Pipe Direction
  action per connected side and applies the first active one through `setFacing(pipe, side)`, the 4.2.2 setter
  (refused for an invalid side). Redstone (Q11): `onNeighbourChange` compares
  `hasNeighborSignal` with the last value (not saved, read again on load) and switches on every change.
- **Power Limiter (Q7).** A Director on a power pipe directs nothing (`allowsOutput` is true on every side) and keeps
  a `PowerMode` (#60's enum, 20 to 1 280 FE/t), saved as `powerMode` (its ordinal), default `M2` as a fresh 4.2.2 iron
  kinesis pipe. The wrench steps to the next limit and shows `chat.pipe.power.limiter.mode`; 4.2.2 stepped back on a
  sneaking wrench, which now removes the module. The gate offers the seven Power Limiter actions and applies the first
  active one. `getPowerLimit()` is `SEAM(#52)`: power routing caps the pipe with it.
- **Items.** `module_extractor`, `module_filtered_extractor`, `module_director` (keys
  `item.moduleExtractor` …, no 4.2.2 keys), in the machines tab, textures copied from the 4.2.2 wood, emerald and
  iron item pipe textures as stand-ins. No recipe yet: modules are made in the Assembly Table (PLAN 8.2); the recipes are
  #74 (`docs/PARITY.md`).
- **Tests.** Unit (`ModuleLogicTest`): the facing search order and wrap, no facing starting at `UP`, the filter
  cursor in both modes (round robin, waiting, skipping, all filters empty, stop on no capacity, range), the kind
  matrix; `PipeRoutingTest`: a Director sends everything out of its facing, read live; a Director facing a full chest
  backs up to the previous junction; no facing is a dead end. GameTests (`ModuleTests`, padded area): fitting with the
  item, facing, wood-rate extraction from a chest and the wrench reversing it; the exclusion list; sneak-wrench removal
  with settings, refitting, saving with the pipe, no Filtered Extractor on a fluid pipe, the drop on breaking; filtered
  round robin; blocking versus non-blocking and empty filters; the menu, its phantom slots and the GUI return; the
  Director's facing, wrench, Pipe Direction actions and redstone toggle with chests; the Power Limiter's wrench steps
  and actions.

### For #56 (Sorter, Pickup, Void), #57 (colours, Isolator), #60 (gates), #51 and #52

- **A new module:** subclass `PipeModule`, add a case to `PipeModule.create`, register its item with
  `TransportRegistry.module(type, "moduleX")` (a line next to the three here) and add the lang key and item model.
  Settings go in `save`/`load`; they travel with the item automatically. A GUI is a `MenuProvider` from
  `getMenuProvider`, with a menu that returns its settings like `ContainerFilteredExtractor`.
- **Candidate filter (Sorter, Colour Sorter) and colour on entry:** not added yet. Add them to `PipeWalk.Rules`
  between steps 4 and 6 (and to `PipeWorld.Pipe` / `PipeModule` like `allowsOutput`); they need the resource, which
  only the router's rules know, so `ResourceRouter.push` will have to pass it.
- **Pickup:** a source type already; `tickSource` is called once per tick when the pipe's network exists (source
  pipes ask for theirs on their own tick).
- **Void:** the walk's `isSink` still reads `node.module == VOID` (the type), which needs no module object.
- **Gate actions:** a module with actions returns an `IPipeGateTarget` from `gateTarget(pipe)` (Pipe Color and
  Extraction Preset for #57), as the Director does.
- **#51:** fluid Extractor at `SEAM(#51)` in `ModuleExtractor.tickSource` (facing and validity already handle fluid
  pipes through `PipeManager.canExtractFluids`); the Director works once the fluid router uses the default
  `allowsOutput`.
- **#52:** read `ModuleDirector.getPowerLimit()` on a power pipe whose module is a Director.

## Power (kinesis) routing (#52)

New code from `docs/TRANSPORT.md` 7.4, package `buildcraftreforged.transport.route`; the 4.2.2 `PipeTransportPower`,
`PipePowerWood` and `PipePowerIron` were read for behaviour only. Recorded 2026-10-31.

- **Intake (6.2, Q6).** Power pipes register `Capabilities.Energy.BLOCK` (`TransportRegistry`, `TilePipe::getEnergyHandler`)
  and return a `PipeEnergyHandler` per side: amount 0 always, capacity = the pipe's refill (FE/t) so callers that look
  at it see a live handler, `extract` 0. `insert(amount, transaction)` routes at once and returns what the receivers
  took. Like the item handler it refuses on a plugged side and on a side facing another power pipe, decided at call
  time. Every power pipe takes pushes from any adjacent block: engines (#36 already push into any `EnergyHandler`),
  other mods' generators, a test source. Pipes store nothing, so nothing is lost in them; the only losses are the
  receivers' own (`PowerHandler` perdition) and whatever no receiver wanted, which stays in the pusher.
- **Phases.** `PowerRouter.push(network, start, sourcePos, amount, now, burstSeconds, transaction)` is 7.4: a BFS tree
  from the pushed pipe (one parent each, side order); request phase children first, `request(pipe) = min(budget, sum
  of children's requests + receivers' simulated insert of Integer.MAX_VALUE)`; send phase root first,
  `floor(y × request / sum)` per outlet and the remainder 1 FE at a time in side order; deliver with real inserts; sum
  what was taken up the tree; spend each pipe's `PipeBudget` (cost 1 FE) by what passed it. All in the caller's
  transaction, so a simulated push is an aborted transaction (6.2) and a real one is atomic with the pusher's extract.
  `PowerRouting.push(level, network, start, in, amount, transaction)` is the level-side call; `PowerRouting.ROUTER`
  reads `TilePipe.getBudget()`, `getPowerRefill()` and `getPowerState()`.
- **Receivers.** Each block is one receiver, through the first of its faces the BFS meets; a machine touching two
  pipes is not asked twice (that would double its share). The pushing block is never a receiver, on any face (#1827,
  as #50's source exclusion by block). Endpoints whose capability went away read as null and ask for nothing.
- **Results.** `PowerRouter.Result(moved, reason, tree)` with the reasons of 6.3: `NO_DESTINATION` when no receiver
  asked for anything, `NO_CAPACITY` when receivers asked but the budgets gave nothing, else `MOVED` / `PARTIAL`.
- **Re-entry.** A receiver that pushes back into the same network while a push runs (another mod's cable, for
  instance) gets 0; the router keeps the set of networks it is routing.
- **Overload and requests.** `PowerPipeState` (per pipe, journaled, not saved) records FE passed per tick, the 4.2.2
  overload counter (each closed tick at ≥ 95 % of the refill +1, else −1, 0–60, overloaded at 60; idle ticks are
  closed lazily) and the last request phase's answer (Q21: demand behind the pipe > what it passed).
  `PowerRouting.isOverloaded(level, pipe)` and `PowerRouting.requestsEnergy(level, pipe)`; the latter answers from the
  last phase if it is under `REQUEST_TICKS` (20) old, else asks the receivers behind the pipe directly
  (`PowerRouter.demand`, any demand at all), so a gate that switches engines off on "no request" switches them on again
  when a machine needs power.
- **Tests.** Unit (`PowerRoutingTest`, stub grid): 30/30/40 and 4/3/3 splits, split by requests across branches,
  budget caps per node, a capped branch leaving the rest to the other, the 5.5 diamond example tick by tick, source
  exclusion on one face and on two, a block on two pipes counted once, a loop, no receiver / full receiver /
  empty budget reasons, simulation changes nothing, the tier-1 burst then 80 FE/t, redstone pulses through wood,
  overload rising to 60 and falling, request recording and `demand`. GameTests (`PowerRoutingTests`, padded area,
  test blocks `gametest_fe_source`, `gametest_fe_sink`, `gametest_power_machine`): the test engine through five wood
  pipes into a `PowerHandler` machine (machine got exactly what the engine pushed, in 10 FE pulses, pipes at 0 FE every
  tick); a 150 FE/t source into a junction with sinks taking 100 and 200 per insert (split 1:2, everything delivered);
  the caps test for all ten tiers (100 000 FE/t pushed, ticks 21–120 deliver exactly `100 × powerPerTick`); and the
  limiter (a 160 FE/t limit on a gold line passes 160 FE/t, a limit above the tier changes nothing, the limited pipe
  is overloaded and requests energy, the gold pipe beside it is not overloaded).

### For #55 (Director as Power Limiter), #59 (flow records) and #60 (gates)

- **#55.** `TilePipe.setPowerLimit(int fePerTick)` (0 = none) lowers the pipe's power refill to
  `min(tier FE/t, limit)` (`getPowerRefill()`); the budget cap follows (`refill × 20 × burstSeconds`). The pipe does
  not save it: the Director module on a power pipe owns the mode (#60's `PowerMode`, `maxPower` already in FE/t), and
  calls `setPowerLimit(mode.maxPower)` when it is fitted, loaded, switched by wrench or the Power Limiter action, and
  `setPowerLimit(0)` when it is removed. Nothing else is needed from routing.
- **#59.** Every real push ends at `SEAM(#59)` in `PowerRouter.run`, inside the push's transaction.
  `Result.tree()` has the BFS `order`, `parent[node]`, `entrySide(node)` (the pipe's side towards its parent, null at
  the root; the root's entry side is the `in` side of the push), `passed[node]` in FE, and per receiver the endpoint
  index and FE `delivered`. The record's rate is FE/t; `overload` is `PowerRouting.isOverloaded`. Width uses
  `getPowerRefill()` or the tier's `powerPerTick` (9.4 says the tier's).
- **#60** (merged first, so wired here: its `SEAM(#52)`). A power pipe's `gateHolder.flow` is a `PowerPipeFlow`, set in
  the `TilePipe` constructor: `isFlowing` = power passed in the last 20 ticks (`PowerPipeState.lastFlowTick`, the 9.2
  window), `requestsEnergy` = `PowerRouting.requestsEnergy`, `isOverloaded` = `PowerRouting.isOverloaded`. Item and
  fluid pipes keep `PipeFlow.NONE` until #59. #59 may replace `isFlowing` with its record's `rate > 0`.

## Blocked output (#63)

New code for plan section 8.3 and `docs/TRANSPORT.md` 6.3, in `buildcraftreforged.core.status`. Decided 2026-10-31.

- **`MachineOutput`** is a machine's item output buffer and its Blocked state. The machine keeps one
  (`new MachineOutput(flowMeter)` counts `itemsOut`, or `new MachineOutput()`), `add(stack)`s what it makes, and calls
  `push(level, worldPosition)` every server tick. A push offers every pending stack to the item capability of each
  neighbour (`Capabilities.Item.BLOCK`, on the neighbour's side facing the machine), in a random side order, each insert
  in its own root transaction with `ResourceHandlerUtil.insertStacking`. Item pipes, chests, other mods' inventories
  and other machines all take part the same way, so 4.2.2's two calls (`addToRandomInventoryAround`, then
  `addToRandomPipeAround`) are one. Engines (`IPowerEmitter` block entities) are skipped, as 4.2.2 skipped
  `TileEngine`.
- **State.** After a push: blocked when something is pending and the push moved nothing; cleared by the first push that
  moves anything, or when nothing is pending. `hasPending()` is what a machine checks before it makes more ("stops"):
  a quarry mines the next block only when its output is empty. Whatever is not taken stays in the buffer;
  nothing is ever spilled (the deliberate change from 4.2.2, which dropped such items).
- **Wiring a machine (three one-liners).** `isBlocked()` → `output.isBlocked()` (the status panel State line, the red
  light and the Machine trigger "blocked" all read it through `IMachine.isBlocked()` and the default
  `IStatusProvider.getStatusState()`); `getStatusReason()` → `output.getReason()`; `saveAdditional` / `loadAdditional`
  → `output.save(output)` / `output.load(input)` (list `pendingOutput`; the blocked state is not saved and is
  recomputed by the next push). When the block is broken, call `output.dropAll(level, pos)` from
  `preRemoveSideEffects`, as an inventory's contents drop. The block adds `StatusPanel.BLOCKED` and the multipart red
  light as in "Status panel (#26)". A machine whose output is not items (a full tank) can report the same state with
  `setBlocked(StatusReason.OUTPUT_FULL)` / `setBlocked(StatusReason.NONE)`; the refinery keeps its 4.2.2 flag.
- **Reason line.** `OUTPUT_FULL` ("Output blocked") when something next to the machine could take items but is full
  or saturated; `NO_DESTINATION` ("No destination") when nothing around takes items at all. A neighbour that refuses
  says which through `IOutputRefusal.whyRefused(resource)`; a plain inventory without it counts as full. Item pipes
  implement it (`PipeItemHandler`): a simulated push that finds a destination but no budget, or a walk that finds no
  destination while the network still has an item endpoint other than the pushing block (a full chest the walk
  backed out of), is "Output blocked"; a pipe line that reaches no item handler is "No destination". This refines
  TRANSPORT.md 6.3, where a walk's `NO_DESTINATION` alone would have shown "No destination" for a pipe to a full
  chest, against the section 14 test that expects "Output blocked".
- **For #67 (quarry) and the other P4 machines.** Replace 4.2.2's `mineStack` sequence (`addToRandomInventoryAround`,
  `addToRandomPipeAround`, `dropItem`) with `output.add(stack)`; push every tick; skip the next work step while
  `output.hasPending()`. The mining well, auto workbench and assembly table can do the same.
- **Tests.** GameTests `blocked_output_pipe_to_full_chest` (TRANSPORT.md 14: machine, two gold pipes, a full chest:
  red light, State Blocked, Reason "Output blocked", Machine trigger blocked and Work Done false, one item pending and
  no more made; the pending item survives a save and load; emptying a slot clears all three within three ticks and
  cobblestone arrives), `blocked_output_full_chest` (a full chest beside the machine) and
  `blocked_output_no_destination` (nothing around, then a pipe leading nowhere: "No destination"; a chest at the end
  unblocks it). They use the test-only machine `gametest_output_block` (`OutputTestBlocks`, dev runs only), which
  makes one cobblestone whenever its output is empty. Every test ends with no item entity in its area. The red light's
  look needs a client and is not checked.

## Fluid routing (#51)

New code from `docs/TRANSPORT.md` 7.2 and 7.3, package `buildcraftreforged.transport.route`; the 4.2.2
`PipeTransportFluids`, `PipeFluidsWood` and `PipeFluidsVoid` were read for behaviour only. Recorded 2026-10-31.

- **Router.** `FluidRouting.ROUTER` is a `ResourceRouter<FluidResource>` with cost 1 (1 mB per budget unit), refill
  the tier's `fluidPerTick`, handlers from the endpoint caches (`Capabilities.Fluid.BLOCK`). Same walk, budgets,
  transaction and reasons as items (#50): one transfer goes whole to one destination, picked by the random walk (Q2),
  so a junction alternates between its outlets from transfer to transfer rather than splitting each tick.
  `ResourceRouter.push` has a new overload with a `Predicate<PipeNode> canEnter` (the walk's `Rules.canEnter`); the old
  one passes "any pipe".
- **Lock (7.3).** `FluidPipeState` (per pipe, journaled, not saved): the fluid that last passed and when, the entry and
  exit sides of that transfer and the mB passed this tick. The lock is that last flow: a pipe refuses another fluid
  while `now - lastFlowTick < transport.fluidLockTicks` (new config key, 1–1200, default 20; `FluidRouting.lockTicks()`).
  `FluidRouting.route` walks with `canEnter = state.canPass(fluid)`, so a locked start pipe gives `NO_DESTINATION`, a
  locked pipe further on is walked around (or backed out of), and a Void pipe locked to another fluid is not entered
  (the lock is checked before the sink test). After a real transfer every pipe of the path records it, in the same
  transaction: a simulation neither locks nor records. Pipes on a branch the walk backed out of are not locked. Every
  transfer renews the lock, so a fluid flowing every tick holds its pipes for as long as it flows.
- **Intake.** Fluid pipes register `Capabilities.Fluid.BLOCK` (`TransportRegistry`, `TilePipe::getFluidHandler`):
  `PipeFluidHandler`, one always-empty tank of capacity `fluidPerTick`; `insert` routes at once (`FluidRouting.push`)
  and returns what reached a destination; `extract` gives nothing (4.2.2 `drain` returned null). It refuses on a
  plugged side and on a side facing another fluid pipe, decided at call time, as the item and energy handlers.
- **Extractor (8.2).** `ModuleExtractor.tickSource` on a fluid pipe calls `FluidRouting.extract(level, network, node,
  facing, source)`: offers `min(simulated drain, budget of the source pipe)` from the first slot of the facing handler
  whose fluid has a destination (Q13 applied to fluids; a slot that drains nothing, such as a refinery input, is
  skipped), at most one successful transfer a tick, drain and fill in one root transaction. The facing and the
  `woodenPipe.liquid.exclusion` list are #55's (`PipeManager.canExtractFluids`). The Director and the Void module work
  on fluid pipes through the walk's defaults.
- **Empty tanks are destinations.** A tank an Extractor has emptied takes any fluid routed past it, including another
  fluid from the same network; the Extractor then pulls that out again. This is the same rule as items (only the
  source block of a transfer is excluded), recorded here because the mixing GameTest runs into it.
- **Pipe Contents (#60).** A fluid pipe's `gateHolder.flow` is a `FluidPipeFlow`: `isFlowing` = fluid passed in the
  last 20 ticks, `fluidContent` = that fluid. The 4.2.2 Contains Fluids trigger with a filled container as parameter
  works through it.
- **Tests.** Unit: `FluidPipeStateTest` (no lock on a new pipe; water refuses lava up to the last locked tick and accepts
  it after; renewal; an aborted transaction neither locks nor records; mB per tick and the side bits) and two
  `PipeRoutingTest` cases (a refused pipe is walked around; a refused start pipe gives `NO_DESTINATION`). GameTests
  (`FluidRoutingTests`, padded area, test blocks `gametest_fluid_source`, an endless source of one fluid, and
  `gametest_fluid_sink`, which takes any fluid without limit): tank to tank (4 000 mB through an Extractor and gold
  pipes, pipes hold nothing and give nothing back, Pipe Contents reads water); splitting at a junction with two tanks
  (100 transfers, each tank at least a quarter, nothing lost); pushes into a wood line (cap 400 mB, simulated push
  changes nothing, refill after 20 ticks) and a Void module deleting lava; mixing refusal (water and lava share a
  trunk: the trunk stays locked to water and the lava tank untouched until all the water is through, lava moves
  exactly `fluidLockTicks` after the last water); and the caps test for all ten tiers (ticks 21–120 deliver
  `100 × fluidPerTick` mB, ± one tick).
- **Not checked without a client:** nothing in #51 draws anything; fluid in pipes becomes visible with #59.

### For #59 (flow records) and #57 (Isolator)

- **#59.** Every real fluid transfer ends at `SEAM(#59)` in `FluidRouting.record`, inside the transfer's transaction,
  after each path pipe's `FluidPipeState.recordPassed`. Per pipe: `lastFluid()` (the record's content),
  `entrySide()` / `exitSide()` (0–5 in side order, `FluidPipeState.CENTRE` = 6 for a transfer that starts inside the
  pipe or ends at a Void sink: the 9.1 `sides` byte is `entry | exit << 3`), `passed(now)` (mB this tick) and
  `lastFlowTick()`. The rate is mB/t; width uses the tier's `fluidPerTick`. #59 may replace `FluidPipeFlow.isFlowing`
  with its record's `rate > 0`.
- **#57.** The Isolator needs nothing from fluid routing: it is a connection rule (#48), so an Isolator never has a
  tank endpoint.

## Redstone and Stirling engines (#37, #38)

Ported: `energy/TileEngineWood`, `TileEngineStone`, `gui/ContainerEngine`, `gui/GuiEngine`, `gui/GuiStoneEngine`.
Registered as `engine_wood` and `engine_stone` through `EnergyRegistry.engine(...)`, with blockstates and item models
from the #39 texture table, loot tables, `minecraft:mineable/pickaxe` and the 4.2.2 recipes (`"plankWood"` →
`#minecraft:planks`, `"cobblestone"` → `#c:cobblestones`, the rest exact items). Recorded 2026-10-31.

- **Redstone engine units.** Every MJ value × 10: `OUTPUT` 0.5 FE/t (4.2.2 0.05 MJ/t, shown only), 10 FE added every
  16 ticks while powered (4.2.2 `addEnergy(1)`), 1 000 FE store, 500 FE intake, 10 FE pulses (4.2.2 1.01 MJ, see
  "For the engines (#36)" under Power). Its own `computeEnergyStage` (energy, never `OVERHEAT`) and `getPistonSpeed`
  (0.08 × heat level on the server) are kept, so a cold engine pulses slowly and a warm one about every 16 ticks:
  at its working temperature it delivers about 0.625 FE/t.
- **Redstone engine and pipes.** 4.2.2's `TileEngineWood.overridePipeConnection` returned `DISCONNECT` for every pipe,
  yet its pulse still reached a wooden kinesis pipe beside it, which looked unconnected. Plan section 7 has the
  redstone engine power any accepting block, and #52's power pipes take pushes from any neighbour, so power pipes now
  get `DEFAULT` (they connect); item and fluid pipes still get `DISCONNECT` on every side.
- **Stirling engine units.** `MAX_OUTPUT` is 10 FE/t (4.2.2 1 MJ/t); `MIN_OUTPUT`, `eLimit` follow from it and
  `TARGET_OUTPUT` (0.375 of the store), `kp`, `ki` are unitless, so the PID aims at 3 750 FE of a 10 000 FE store and
  outputs between 10 / 3 and 10 FE/t, 10 × 4.2.2 at every point. Upstream's own RF port (7.1) ended with the same
  constants. Intake 2 000 FE (minimum the `TileEngine` default of 20), pulses up to 1 000 FE.
- **Burn times.** 1.6.4 `TileEntityFurnace.getItemBurnTime` is now the item's `minecraft:cooking_fuel` component,
  resolved as the vanilla furnace does (`ResolvableInt.getFromItem` with a `CONTAINER_PROCESS` loot context built like
  `BaseContainerBlockEntity.getLootContext`), so data packs that change furnace fuel change the engine too. The vanilla
  values match 1.6.4 for coal (1 600), blaze rods (2 400), sticks (100), coal blocks (16 000) and lava buckets
  (20 000). `Utils.consumeItem` leaves the crafting remainder, so a lava bucket leaves an empty bucket, as 4.2.2.
- **GUI data.** `TileEngine.guiData` now has `getGUINetworkDataCount()` slots (default 4); the Stirling engine adds
  `burnTime` and `totalBurnTime` as ids 4 and 5 (4.2.2 15 and 16), read back as unsigned 16-bit values (burn times
  up to 65 535 ticks, 4.2.2 sent shorts too).
- **Menu.** `ContainerEngine(MenuType, containerId, Inventory, TileEngineWithInventory)` keeps 4.2.2's slots (fuel at
  80, 41 for the Stirling engine, 52, 41 otherwise; inventory from 8, 84; hotbar at 142), adds `tile.guiData` and a
  `StatusWidget`. One `MenuType` per engine, registered in `EnergyRegistry` (`engineStoneMenu`, id `engine_stone`) with
  a client constructor that reads the block position (`ContainerEngine.clientTile`). `TileEngineStone` is the
  `MenuProvider` and opens it from `onBlockActivated` with `player.openMenu(this, worldPosition)`. The screen is paired
  in `energy/gui/EnergyScreens`.
- **Screens.** `GuiEngine` (abstract, `GuiBuildCraft<ContainerEngine>`) keeps 4.2.2's `EngineLedger` (orange, 94 high)
  with FE/t, FE and °C instead of MJ/t and MJ, its icon the `icons/energy` GUI sprite; the status ledger is added after
  it. `GuiStoneEngine` draws `textures/gui/steam_engine_gui.png` (copied unchanged) and the 4.2.2 flame.
- **Status panel.** The Stirling engine reports `OUT_OF_FUEL` when it is powered with nothing burning and nothing
  burnable in its slot. The redstone engine opens the status panel on use (#36's default).
- **Not here.** The inventory triggers 4.2.2's `TileEngineStone.getTriggers` added (`SEAM(#41)`), the overheat timer
  (#40) and comparator output.
- **Tests.** GameTests `engine_wood_redstone_output` (nothing without a signal; with one, 10 FE per 16 ticks all in the
  receptor or the engine, 10 FE pulses, about 0.625 FE/t from a warm engine; pipe overrides), `engine_stone_burns_fuel`
  (coal taken only when powered, 1 600 ticks, 10 FE per tick burnt below the target, lava bucket, other fuels,
  OUT_OF_FUEL, save tags, GUI values), `engine_stone_output_control` (PID limits), `engine_stone_menu` (slots,
  shift-click, status widget, GUI texture, both recipes) and `engine_delivery_through_kinesis_pipe` (both engines
  through three wood power pipes into a `PowerHandler` machine, pipes empty every tick). Not checked without a client:
  the GUI drawing, flame and ledgers, the engines in the world and in the hand.

### For the combustion engine (#44)

- One line in `EnergyRegistry` below `engineStone`
  (`engine("engineIron", "engine_iron", TileEngineIron::new)`) and a menu beside `engineStoneMenu`:
  `REGISTERS.menus.register("engine_iron", () -> IMenuTypeExtension.create((id, inv, buf) -> new ContainerEngine(
  EnergyRegistry.engineIronMenu.get(), id, inv, ContainerEngine.clientTile(inv, buf.readBlockPos(), EnergyRegistry.engineIron))))`.
  Pair it with `GuiCombustionEngine::new` in `EnergyScreens`.
- `ContainerEngine` already puts slot 0 at 52, 41 for any engine that is not a `TileEngineStone`. `GuiCombustionEngine`
  extends `GuiEngine` (ledgers come with it); the fluid gauges and tanks are #24's.
- Extra GUI values go after id 3: override `getGUINetworkDataCount()` and extend `sendGUINetworkData(int)` /
  `getGUINetworkData(int, int)` as `TileEngineStone` does (4.2.2's iron ids 15 to 19 become 4 onwards).
- Implement `MenuProvider` and override `onBlockActivated` like `TileEngineStone`, and `getStatusState` for
  `OUT_OF_FUEL`.
- Copy `combustion_engine_gui.png` from `reference/buildcraft_resources/assets/buildcraft/textures/gui`. Add the two
  #39 files, loot table, pickaxe tag and recipe (`Item.ingotIron` exact, `ironGearItem`, glass, piston).
