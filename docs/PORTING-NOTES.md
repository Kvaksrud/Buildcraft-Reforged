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
