# Buildcraft Reforged — port plan

BuildCraft's Minecraft 1.6.4 content and machines, rebuilt for NeoForge on the Minecraft 26.x line, with Forge Energy (RF/FE) as the power unit, a redesigned rate-based transport system, overheat alarms, a status panel on every machine, and automatic CurseForge publishing on release.

Written 29 Oct 2026 from a clone of https://github.com/BuildCraft/BuildCraft. Every number and class name taken from the baseline was read from that history, not from memory. Section 14 is the decision log.

---

## 1. Decisions in one screen

| Topic | Decision |
|---|---|
| Baseline | BuildCraft **4.2.2**, tag `4.2.2`, released 4 Jan 2014. The last release built for Minecraft 1.6.4 (Forge 9.11.1.949). 5.0.0 already targets 1.7.2. |
| Scope | All 4.2.2 machines, engines, gates, builders and content, behaving as in 4.2.2 plus bug fixes. Transport (pipes) is redesigned (section 8). A short list of deliberate additions (section 3). Nothing else from later BuildCraft versions. |
| Deliberate deviations | (1) Platform: NeoForge 26.x. (2) Energy: Forge Energy at 1 MJ = 10 FE. (3) Transport: rate-based pipes with tiers and modules, instant routing, animated flow. (4) Engines: overheat timer with alarm output, explosions configurable. (5) Status panel on every working block. (6) Fake player with owner profile for block-breaking machines. (7) Integrations: recipe viewer, wrench tags, comparator output, waterlogging. |
| Name, ids | Display name "Buildcraft Reforged", mod id `buildcraftreforged`, Java package `buildcraftreforged`. |
| Licence | MMPL 1.0.1, unchanged, because the licence requires it. Full source published. Original copyright headers kept, with a "modified by" line added. No naming or "unofficial" wording required by the licence; none added. |
| Target version | Newest NeoForge 26.x at kick-off (26.3 as of writing). Version-specific code isolated so following 26.4 and later is routine. |
| Build | Gradle 9.8, ModDevGradle 2.0.148, Java 25, single jar, one Gradle project with packages mirroring the original modules. |
| Publishing | GitHub Actions. Tag `v*` → build → upload to CurseForge and a GitHub release via the mod-publish-plugin. No Modrinth for now. |
| Versioning | Development series **0.x**, first release **0.1.0**. Every 0.x upload is marked beta on CurseForge. 1.0.0 is reserved for the moment every row in `docs/PARITY.md` is ticked and the tier ladder has survived a playtest. Minor bump per phase landed (0.2 after P2, and so on), patch bump for fixes. |
| Structure | A fresh repository, not a fork. The 4.2.2 sources are vendored read-only under `reference/` for diffing, never compiled. |
| Oil fields | Placed feature inside vanilla desert and ocean biomes. No new biomes, no third-party dependency. |
| Blueprints | 4.2.2 semantics and class names, block-state storage, new versioned file format. 1.6.4 files not loadable in 0.1.0; a one-way importer ships in a later 0.x release (section 13.5). |

---

## 2. What the baseline contains

Tag 4.2.2: 497 Java files, 400 PNG textures, one language file (`en_US.properties`), Ant build. Modules by package (file counts): api 78, builders 43, core 168, energy 32, factory 38, silicon 15, transport 117.

### Blocks and machines (27 block classes, 33 tile classes)

| Module | Content |
|---|---|
| core | Wrench, Marker, Path Marker, Spring (water/oil; **water spring dropped**, see section 3), fluid blocks |
| energy | Redstone engine (wood), Stirling engine (stone), Combustion engine (iron), oil and fuel fluids, oil wells and oil biomes (Desert Oil Field, Ocean Oil Field), buckets |
| factory | Quarry (+ frame block), Mining Well (+ plain pipe), Pump, Refinery, Tank, Auto Workbench, Hopper, Flood Gate |
| silicon | Laser, Assembly Table, Advanced Crafting Table, Redstone Chipsets, Gates (and autarchic gates), Pipe Wire, Facades, Plugs |
| builders | Architect Table, Builder, Filler (+ patterns), Blueprint Library, Blueprint and Template items, `BptBlock` schematics for vanilla blocks |
| transport | 30 pipes (below), pipe triggers/actions, Filtered Buffer |

### Pipes in 4.2.2 (30), superseded by section 8

- Items (14): Wood, Cobblestone, Stone, Sandstone, Quartz, Iron, Gold, Diamond, Emerald, Obsidian, Void, Lapis, Daizuli, Emzuli
- Fluids (8): Wood, Cobblestone, Stone, Sandstone, Iron, Gold, Emerald, Void
- Power (7): Wood, Cobblestone, Stone, Quartz, Iron, Gold, Diamond
- Structure (1): Cobblestone

### Gate triggers and actions (8 triggers, 11 actions)

Triggers: Engine Heat, Fluid Container, Inventory, Inventory Level, Machine, Pipe Contents, Pipe Signal, Redstone Input.
Actions: Energy Pulser, Extraction Preset, Filler pattern, Machine Control, Pipe Color, Pipe Direction, Power Limiter, Redstone Output, Signal Output, Single Energy Pulse.

### Energy constants (MJ, from 4.2.2 source)

| Thing | Value |
|---|---|
| Redstone engine | output 0.05 MJ/t (1 MJ per piston pulse), stores 100 MJ, powers any receptor it faces |
| Stirling engine | output up to 1 MJ/t, stores 1 000 MJ |
| Combustion engine | stores 10 000 MJ, accepts up to 2 000 MJ |
| Power pipe capacity, MJ/t (4.2.2, superseded) | Cobblestone 8, Stone 16, Wood 32, Quartz 64, Iron 128, Gold 256, Diamond 1024 |
| Perdition (idle power loss) | default 1 MJ/t, minimum 0.01 MJ/t |
| Quarry | receive 50–100 MJ/t × miningMultiplier |
| Mining Well | receive 100 MJ/t, stores 1 000 MJ |
| Pump | receive 1–15 MJ/t, 10 MJ per operation, stores 100 MJ |
| Refinery | receive 50–150 MJ/t, 25 MJ per operation, stores 1 000 MJ |
| Laser | receive 25–150 MJ/t, stores 1 000 MJ |
| Builder | 25 MJ in every slot of the configuration |
| Filler | receive 30 MJ/t minimum, usage-based maximum |

Facts about 4.2.2 that settle later questions: engines already chain (each engine is both a power receiver and emitter); the Auto Workbench already handles non-stackable items; diamond gates already offer four AND/OR slots; the quarry already chunk-loads; there is no fake player and no protection check anywhere.

A generated table `docs/ENERGY-TABLE.md` will list every constant with its FE value (× 10).

---

## 3. Scope rules

### Kept as 4.2.2 (plus bug fixes)

Every machine, engine, gate, trigger, action, GUI, recipe, fluid, worldgen feature and config option of 4.2.2, except where sections 7 to 10 say otherwise. That includes things later versions removed or nerfed:

- Redstone engines power machines, not just pipes. Later versions (6.1.5 onward) restricted them to pipes. We keep 4.2.2.
- Power loss in machines (perdition) as in 4.2.2.
- Block ids and metadata are gone by platform necessity. The config's `block`/`item` id sections are dropped.

### Deliberate additions (decided 29 Oct 2026, section 14)

| Addition | Where specified |
|---|---|
| Rate-based transport with ten material tiers, pipe modules, instant routing, animated flow | Section 8 |
| Overheat timer, warning sound, alarm trigger and comparator output, explosions configurable | Section 9 |
| Status panel on every working block | Section 10 |
| Fake player carrying the owner's profile for quarry, mining well, builder, filler | Section 6 |
| Recipe viewer plugin, wrench tag compatibility, comparator output, waterlogged pipes and frames | Section 6 |
| Server-bound packets validated against the player's open menu or reach; tile sync is client-bound only | Section 19.2 |
| Blueprint library under the game or world directory, keyed by UUID, names sanitised | Sections 13.2, 19.2 |
| Nether oil and fuel ignite with fire instead of exploding (`fluids.netherIgnition`) | Section 19.2 |
| Quarry chunk loading behind `quarry.loadsChunks`, default off; breaking a quarry removes its frame | Section 19.3 |
| Oil wells capped at ground level, no geyser column above the surface | Section 19.3 |
| Dropped-item lifespan default raised to vanilla 6000 ticks | Section 19.3 |
| No network access of any kind: version checker and remote changelog removed | Section 19.1 |

### Dropped from 4.2.2

- **Water springs.** 4.2.2 generates a spring block under the surface that keeps a water source above it and occasionally pops water through. Removed entirely: no water spring block, no worldgen for it, no `worldgen.spring` config entry. The oil spring stays, because oil spouts in oil fields are built on it (section 6, oil placed feature). Decided 29 Oct 2026.

### Out

Everything else first released after 4.2.2. Section 11 lists it. The big ones: gate expansions and the Integration Table (5.0), robots, zone planner, construction markers, energy converter (6.x), lenses, quartz gates (6.3), the 7.0 "Essence" textures, paintbrush, stripes pipes, lists, the 8.0 rewrite.

### Bug fix policy

A change qualifies as a bug fix when all of these hold:

1. The bug exists in 4.2.2 (reproduce it in a reference 1.6.4 instance, or the upstream issue is against 4.x).
2. The fix changes no balance number, recipe, texture, GUI layout or feature set, beyond the additions above.
3. The fix is documented in `docs/BUGFIXES.md` with the upstream commit hash, the issue number, and the decision.

Harvest sources, in order of relevance (counts are commits whose message mentions fix/close/crash/NPE/dupe):

| Source | Candidates | Note |
|---|---|---|
| 4.2.2 → 5.0.0 | 30 of 98 commits | Same code, mostly 1.7.2 porting; pick e.g. "Fix Engine dupe", "NPE check in Iron Engine", "InventoryIterator supports double chests", "Fix Gate Rendering", "TurtleQuarryFix" |
| 5.0.x branch after 5.0.3 | 54 commits | e.g. flood gate + finite liquids (#1563), tile ClassCastException (#1584), packet leaks (#1855), oil spouts above ocean |
| 6.0.x branch | 253 of 678 | e.g. pump crash, tile marker crash, drain dupe, residual liquid in pumps and refineries (#1790, #1791), "do not send power where power is received" (#1827), unbreakable block handling (#1789), fake player (#1786) |
| 6.1.x–7.1.x, module dirs only | 411 | Lower yield; many touch rewritten code. Scan for tanks, quarry, builders: tank liquid dupe (#2353), quarry not dropping inventory contents (#2340), filler world corruption (#2331), fluid handlers without tanks (6.3) |
| GitHub issues | — | Search closed issues labelled for 4.x or 1.6.4 |

Accepted in advance as fixes (they change behaviour only in edge cases): engines facing each other no longer exchange power (#1827), engine orientation on placement (#1771), unbreakable block handling (#1789), pump and refinery residual liquid, tank fill/drain flags, fluid pipes connecting to tank-less fluid handlers.

Triage happens per ported class: when porting `TileX`, run `git log --oneline 4.2.2..origin/7.1.x -- common/buildcraft/<module>/TileX.java` in the reference clone and review each hit.

---

## 4. Target platform

As of 29 Oct 2026:

| Item | Value |
|---|---|
| Minecraft | 26.3 (released 15 Sep 2026); 26.2 on 16 Jun 2026; 26.1 on 24 Mar 2026 |
| NeoForge | 26.3.0.99 at kick-off (29 Oct 2026). The beta label was dropped from 26.3.0.76 on |
| Java | 25 |
| Gradle | 9.8.2 (wrapper) |
| ModDevGradle | 2.0.148 at kick-off |
| Version scheme | NeoForge `26.3.0.N`: first three parts are the Minecraft version |

Context: the NeoForge audience in 2026 still sits overwhelmingly on Minecraft 1.21.1 (roughly two thirds of the ~20 700 tracked 1.21.1 modpacks are NeoForge; the 26.1 line is a small but growing slice). The 26.x choice stands. A thin `platform` package keeps a 1.21.1 backport possible later; nothing in this plan targets it.

---

## 5. Repository layout

```
buildcraft-reforged/
├── build.gradle.kts            ModDevGradle, mod-publish-plugin
├── settings.gradle.kts
├── gradle.properties           versions: mc, neoforge, mod, curseforge project id
├── LICENSE                     MMPL 1.0.1 (verbatim from 4.2.2 buildcraft_resources/LICENSE.txt)
├── NOTICE.md                   attribution: SpaceToad, CovertJaguar, Krapht, SirSengir and the BuildCraft team; link to upstream; what changed
├── CHANGELOG.md                keep-a-changelog style; the release workflow reads the top section
├── README.md
├── .github/workflows/
│   ├── build.yml               PR + push: build, run GameTests (added near release, see section 14)
│   └── release.yml             tag v*: build, publishMods (CurseForge + GitHub)
├── docs/
│   ├── PLAN.md                 this file
│   ├── BUGFIXES.md             every harvested fix, with upstream hash and decision
│   ├── PARITY.md               checklist, one line per block/item/pipe/module/gate/trigger/action/GUI
│   ├── ENERGY-TABLE.md         generated MJ → FE table
│   ├── TRANSPORT.md            the rate model, ladders and routing rules (section 8, kept current)
│   └── PORTING-NOTES.md        API mapping decisions (section 6), kept current
├── reference/                  git submodule or vendored copy of BuildCraft at tag 4.2.2, read-only
└── src/
    ├── main/java/buildcraftreforged/
    │   ├── BuildcraftReforged.java     mod entry, registries, config
    │   ├── platform/                   anything that touches NeoForge version-specific API (save I/O, networking, rendering hooks)
    │   ├── api/                        ported buildcraft.api.* (power, transport, gates, blueprints, fuels, recipes, tools, filler, inventory, core)
    │   ├── core/                       wrench, markers, triggers base, network, GUI base, status panel, fake player, utils
    │   ├── energy/                     engines, overheat alarm, oil/fuel fluids, worldgen
    │   ├── factory/                    quarry, mining well, pump, refinery, tank, auto workbench, hopper, flood gate
    │   ├── silicon/                    lasers, tables, chipsets, gates, facades, plugs, pipe wire, pipe modules
    │   ├── builders/                   architect, builder, filler, library, blueprints, schematics
    │   └── transport/                  pipe network, routing, tier pipes, flow rendering, pipe triggers/actions
    ├── main/resources/
    │   ├── META-INF/neoforge.mods.toml
    │   └── assets/buildcraftreforged/  textures (the 400 PNGs plus new pipe/module art), models, blockstates, lang/en_us.json, sounds
    ├── generated/resources/           datagen output: recipes, loot tables, tags, worldgen JSON, models
    ├── datagen/java/                  data providers
    └── test/java/ or gametest/        unit tests for energy and routing maths; NeoForge GameTests
```

Packages mirror the 4.2.2 modules so every ported class can be diffed against `reference/common/buildcraft/<same path>`. The transport package is the exception: it is new code, with `docs/TRANSPORT.md` as its reference instead.

---

## 6. Rewrite method: transliterate, do not redesign (except transport)

Rule: port class by class, keeping names, field names, constants and control flow. Replace only the platform API underneath. A reviewer should be able to open the 4.2.2 class next to the new one and follow along. Transport is designed fresh against section 8.

### API mapping

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

### Per-class porting checklist

1. Copy the 4.2.2 class into the new package, keep header + add "Modified by" line.
2. Replace platform imports per the mapping. No renaming of fields or methods beyond the mapping.
3. Run the upstream log for that file (section 3) and apply qualifying fixes; record them.
4. Add the status panel data provider (section 10) if the block works.
5. Tick the class in `docs/PARITY.md` with "compiles", "renders", "behaves" columns.

---

## 7. Energy: Forge Energy, Mekanism-style

Mekanism stores its own unit internally and exposes `IEnergyStorage` with a conversion rate. Buildcraft Reforged does the same with one simplification: the internal unit is FE itself, stored as `long`, and every 4.2.2 MJ value is multiplied by 10. Fractions disappear (0.05 MJ/t becomes 10 FE per pulse, which is how the redstone engine already delivered power: once per piston cycle, not per tick).

| Rule | Detail |
|---|---|
| Conversion | 1 MJ = 10 FE, the rate BuildCraft itself used from 6.1.5 and the default in 8.x. Not configurable. |
| Storage | Every engine and machine has a ported `PowerHandler` holding FE, with the same min/max receive, max stored, activation energy and perdition behaviour (1 MJ/t → 10 FE/t loss while idle). |
| Delivery | Engines push on each piston pulse, exactly as 4.2.2. Engines expose `IEnergyStorage` with `canReceive() == false` on their output side. |
| Receiving | Machines expose `IEnergyStorage` with `canReceive() == true` on the sides 4.2.2 accepted power. Any FE source from another mod can power a BuildCraft machine; inherent to adopting FE. |
| Kinesis pipes | Per-tick request/send over the pipe network with the tier caps of section 8. Power pipes do not store energy; they carry FE/t. |
| Redstone engine | 10 FE per pulse, stores 1 000 FE, powers any accepting block. The 6.1.5 pipe-only restriction is **not** applied. Also drives the Basic Extractor (section 8). |
| Display | GUIs, tooltips and the status panel show FE and FE/t. |
| Triggers | Engine heat and machine triggers unchanged; thresholds × 10. New trigger: Engine Overheating (section 9). |

Unit tests pin the ported `PowerHandler` against values recorded from the 4.2.2 code paths (pulse sizes, perdition).

---

## 8. Transport: rate-based pipes

Decided 29 Oct 2026. This replaces the 4.2.2 per-item travelling simulation. Nothing moves through pipes tick by tick on the server; items, fluids and power are routed instantly along a path whose segments have throughput caps, and the client animates flow from a tiny per-segment record. Cheap on both sides, no over-engineering.

### 8.1 Pipe kinds and tiers

Three pipe kinds (item, fluid, power), ten materials, one ladder, doubling per tier. A gold pipe is always tier 5 whatever it carries. One recipe pattern per kind (glass between two material items, as 4.2.2), one texture family per material.

| Tier | Material | Items/s | Fluid mB/t | Power FE/t |
|---|---|---|---|---|
| 1 | Wood | 1 | 20 | 80 |
| 2 | Stone | 2 | 40 | 160 |
| 3 | Iron | 4 | 80 | 320 |
| 4 | Copper | 8 | 160 | 640 |
| 5 | Gold | 16 | 320 | 1 280 |
| 6 | Quartz | 32 | 640 | 2 560 |
| 7 | Diamond | 64 | 1 280 | 5 120 |
| 8 | Emerald | 128 | 2 560 | 10 240 |
| 9 | Netherite | 256 | 5 120 | 20 480 |
| 10 | Ender (ender pearls) | 512 (8 stacks) | 10 240 | 40 960 |

All values live in a config table (`transport.tiers`) so they can be rebalanced without code. Items/s is enforced with a per-pipe fractional budget per tick (1 item/s = 0.05 items per tick accumulated).

Two blocks outside the ladder: the **Structure Pipe** (cobblestone, carries nothing, holds gates, wires and facades, as 4.2.2) and the **Basic Extractor** (below).

### 8.2 Modules

Every 4.2.2 pipe function becomes a module crafted in the Assembly Table, the machine that makes gates. One module per pipe; gates, wires, facades and plugs remain separate pluggables as before. A pipe without a module is a plain transport pipe. Each module keeps the 4.2.2 GUI of the pipe it replaces and takes the old ingredient in its recipe.

| Module | Replaces 4.2.2 pipe | Behaviour |
|---|---|---|
| Extractor | Wooden pipe | Pulls from the adjacent inventory or tank at the pipe's tier rate. **No engine and no power needed**; modules never need a redstone engine. Decided: fixed rate, no power. |
| Filtered Extractor | Emerald pipe | Extractor with the 9-slot filter and round-robin/ordered modes of 4.2.2. |
| Director | Iron pipe | Forces the exit side; wrench or gate action rotates it. |
| Sorter | Diamond pipe | Six-colour filter GUI, sorts by side; unmatched items follow the 4.2.2 default rule. |
| Pickup | Obsidian pipe | Picks up item entities in front of it; suction range as 4.2.2 (power-scaled in 4.2.2, here tier-scaled). |
| Void | Void pipe | Destroys what enters. Also for fluids. |
| Colour Tag | Lapis pipe | Paints passing items with the pipe's colour. |
| Colour Sorter | Daizuli pipe | Routes by item colour, one colour per side. |
| Filtered Colour Tag | Emzuli pipe | Four filtered colour slots with gate-selectable presets. |
| Isolator | Sandstone pipe | Connects only to pipes, never to machines or inventories. |

**Basic Extractor.** The starter way to pull items, kept beside the Extractor module so the classic redstone-engine setup still exists. It is its own block, not a pipe: a stepped nozzle that sits against an inventory (chest, machine, tank) with its wide plate on the inventory face and narrowing in three steps toward the pipe, which connects on the far side. A redstone engine (or any engine) attaches to one of its free sides and drives it with pulses, exactly as a redstone engine on a wooden pipe did in 4.2.2: one item per redstone engine pulse, more per pulse from stronger engines, capped by the line's tier. Without an engine it does nothing.

Animation: while it is pulsing, the diagonal stripe texture on the three steps scrolls from the inventory toward the pipe, and the icon of the item being pulled slides out of the inventory face, through the nozzle and into the pipe over the pulse duration, so it visibly sucks items out. Idle, the stripes stand still. Client side only, driven by the engine pulse the client already receives for the piston animation. Sketch from the design session:

```
 chest        extractor
┌────────┐   ┌──┐
│        │   │//├─┐
│        │   │//│/├─┐   <── pipe goes here
│        │   │//│/├─┘
│        │   │//├─┘
└────────┘   └──┘
```

Crafting: the 4.2.2 wooden pipe recipe (planks, glass, planks), so it costs what the old wooden pipe cost. It also takes gates and pipe wire like a pipe end, so autarchic gates and the Extraction Preset action keep working on it.

### 8.3 Routing

- The pipe network is a graph rebuilt on block change and cached. Each connected component knows its pipes, modules and attached inventories, tanks and energy handlers.
- When an Extractor module, a Basic Extractor, or a machine output has a stack to send, the server walks the graph from the source applying the **4.2.2 junction rules at each pipe**: Director forces the exit, Sorter and Colour Sorter filter by side, Isolator excludes machines, Void is a sink, plain junctions pick randomly among valid branches that do not lead back. The first inventory on that walk that accepts the stack (simulated insert) is the destination. Result: the same destination 4.2.2 would have reached, reached instantly.
- Every segment on the path spends budget from its per-tick cap. If any segment is saturated for the amount, the transfer is reduced to what fits, or refused if nothing fits.
- **No destination or no capacity:** the item stays in the source. Extractors simply do not pull. A machine whose output is refused stops, shows a red light on its model, and reports "Output blocked" in the status panel and as the Machine trigger state "blocked". Nothing is ever spilled on the ground. Void modules still delete on purpose.
- Fluids use the same walk with fluid handlers as destinations and mB/t budgets; one fluid type per network segment at a time, as 4.2.2 enforced. Power uses the 4.2.2 request/send phases over the graph with FE/t caps.

### 8.4 Flow visuals

Per pipe segment the server keeps one small record: last item (or fluid) that passed, direction, rate in items/s, mB/t or FE/t. It is sent only when it changes materially or at most twice a second. The client animates icons sliding at that rate for items, a fluid column whose thickness and animation speed follow mB/t, and a kinesis glow whose thickness follows FE/t. Nothing on the client simulates individual items. Rendering is a dynamic pipe model per connection mask (cached) plus a light block-entity renderer only on segments with a non-zero rate.

### 8.5 Gates, triggers and actions on pipes

Unchanged from 4.2.2 where they still make sense: Pipe Contents (now "flow present"), Pipe Signal, Redstone Input, Extraction Preset, Pipe Color, Pipe Direction, Power Limiter, Energy Pulser, Single Energy Pulse, Signal Output, Redstone Output. Pipe Wire and all gate kinds (autarchic, iron, gold, diamond, AND/OR up to four slots) as 4.2.2.

### 8.6 New assets

Pipe textures for copper, netherite and ender in three kinds, module items and overlays, the red light overlay for blocked machines, the flow animation frames, the overheat whine sound. New art is the main cost of this section and must be in the style of the 4.2.2 textures.

---

## 9. Engines: overheat timer and alarm

Decided 29 Oct 2026.

- `engines.explode` (default **true**): when an engine finishes overheating it explodes with the 4.2.2 explosion range. When false it shuts down and stays off until hit with a wrench (the 6.2 behaviour).
- `engines.overheatTimerSeconds` (default **300**): when an engine enters its red heat stage, a timer starts. While it runs the engine keeps working at red-stage output, plays a whirring sound that rises in pitch as the timer runs out, and reports "Overheating, N s" in the status panel. Cooling below the red stage resets the timer.
- Alarm outputs: a new gate trigger **Engine Overheating** on adjacent pipes (true while the timer runs), and a comparator signal from the engine block that scales with heat (0 to 15, with 15 reached when the timer starts). Both work with plain 4.2.2 gate wiring and with vanilla redstone, and neither conflicts with the redstone input that drives the engine.
- Heat maths, stages, cooling with water and ice, fuel values and piston speeds stay as 4.2.2.

---

## 10. Status panel

Decided 29 Oct 2026. Every block that works (engines, all factory machines, lasers and tables, builders, extractors and pipes with modules) opens a shared status ledger on right-click, next to its normal GUI or alone for blocks without one. One layout everywhere, data from the existing block entity:

| Line | Content |
|---|---|
| State | Working, Idle, Blocked, No power, Overheating, Out of fuel, No work area |
| Reason | When blocked: what is blocked (output full, no destination, protected block, missing materials) |
| Flow | Items/s and mB/t in and out over the last second |
| Power | FE/t drawn or produced, FE stored of maximum |
| Heat | Engines only: heat stage and overheat countdown |
| Owner | Player who placed the block (used by the fake player) |

Blocked machines also show a red light overlay on the model. Data is sent only while the panel is open.

---

## 11. What is new after the baseline, and what was decided

Listed per version from changelogs and the class diff. Decisions from 29 Oct 2026 are marked.

### 5.0.0 (Feb 2014, MC 1.7.2)
- Gate expansions (Pulsar, Timer, Redstone Fader) and the Integration Table. **Out.** 4.2.2 already has autarchic gates and the Energy Pulser action.
- Pipe Wire as a proper item class, Cylinder filler pattern, Fluid Container Level trigger. Out.

### 6.0.x (Mar–Jul 2014)
- Robots (first version), Urbanist, Heat kinesis pipe. Out.
- Builder and blueprint rewrite with `Schematic*` classes. Out (we port the 4.2.2 schematics onto block states, section 13).
- Crash and dupe fixes. **In**, per the bug fix policy. Fake player **in** (section 6).

### 6.1.x (Aug–Nov 2014)
- Robots with boards, docking stations, zone planner, construction markers, creative engine, energy converter, gears, map location. Out.
- 6.1.5: MJ → RF (we use FE, section 7); redstone engines restricted to pipes (**not** applied).

### 6.2.0 (Nov 2014)
- Clay, diamond fluid, quartz fluid, emerald and sandstone kinesis pipes. Superseded by the tier ladder (section 8).
- Charging Table, Requester, Gate Copier, List item. Out.
- Overheating instead of explosions. **Replaced** by section 9 (both, configurable, with timer and alarm).
- Networking rewrite into one subsystem. **In** as implementation guidance.
- Hardness-based quarry energy, block-break animation. Out.

### 6.3.0 (Jan 2015)
- Lenses, filters, quartz gates, hollow facade items, dye-able colour pipes. Out.
- Fluid pipes connecting to tank-less fluid handlers, tank dupe (#2353), quarry inventory drops (#2340), filler corruption (#2331). **In** if reproducible in 4.2.2.
- Tile lookup and blueprint loading optimisations. In as implementation guidance.

### 6.4.0, 7.0.x, 7.1.x
- Robot energy, filler parameters, modularisation, Essence textures, paintbrush, Light Sensor, stripes, lists, power adapters, chutes. Out.
- Round-robin diamond pipes. Out; the Sorter module keeps 4.2.2 rules.
- Pipe optimisations and smoother item movement. Superseded by section 8.
- Comparator support (7.1). **In** (section 6).
- Auto Workbench buffer. Out; 4.2.2 already handles non-stackables.

### 7.99.x / 8.0.x (2016 onward)
- Ground-up rewrite; per-material kinesis limits, limiter pipes, per-tick engine output, RF pipes beside MJ. Behaviour reference only. Its kinesis tiers informed, but do not equal, the ladder in section 8.

---

## 12. Licence and attribution

The 4.2.2 tree ships `buildcraft_resources/LICENSE.txt`: Minecraft Mod Public License 1.0.1. Relevant terms, paraphrased:

- Anyone may decompile, modify and redistribute the mod, partially or in full, including in a modified form (sections 4 and 6).
- Modified binaries must come with a way to get the complete modified sources at no cost.
- All distributions, including modified ones, must remain under the MMPL.
- Dependencies must be under comparable terms, except Minecraft itself and the mod loader. NeoForge is the loader, so Forge Energy is fine. JEI is an optional runtime integration, not a dependency the mod needs to work.
- Textures and other assets sit under the same file in the repo, so they are covered too. New art and new code we write are released under the same MMPL as part of the whole.

So releasing Buildcraft Reforged built on this code is allowed, with attribution, source publication and the MMPL kept. The licence says nothing about the project name: it neither grants nor restricts use of "BuildCraft", and it has no requirement to label a derivative as unofficial. The name stays as chosen. Telling the current maintainers (the BuildCraft GitHub organisation) about the project is an optional courtesy. This is a reading of the licence text, not legal advice.

---

## 13. Blueprints: block states and the 1.6.4 importer

### 13.1 What a blueprint is

A saved copy of a structure that a machine can rebuild. Four Land Marks outline a box; an Architect Table scans it onto a Blueprint item; a Builder with power and materials places the blocks again elsewhere, rotated to its facing; the Blueprint Library stores blueprints on disk and shares them between players. A Template records only solid versus empty and the Builder fills the shape with one block type.

### 13.2 How 4.2.2 stores one

`BptSlotInfo` holds, per position: `blockId` (an int, 0 to 4095), `meta` (0 to 15), x/y/z, an optional tile-entity NBT compound, and the list of items the block cost. The file is a Latin-1 text file: a `version:` line, `kind:blueprint` or `kind:template`, size and anchor lines, then an `idMap:` block mapping every used id to a **signature** (`blockName` plus the Java class name for mod blocks), then `contents:` with one NBT compound per position (`bId`, `meta`, tile data), then `requirements:`. Library files live in a `blueprints/` folder next to the game and are indexed by number. On load, `mapWorldId` remaps every id through the signature table and `BptBlock.match` star-matches names and class names, so a blueprint made with one id layout works in another.

Rotation is bit twiddling on `meta` done by 18 `BptBlock` subclasses in `api/bptblocks` (Bed, Door, Stairs, Piston, Lever, Sign, Pumpkin, Redstone Repeater, Wall Side, Rotate Meta, Rotate Inventory, Inventory, Fluid, Dirt, Ignore, Ignore Meta, Custom Stack, Delegate). Item requirements come from `BptBlock.getRequirements`, which maps id + meta to an item stack.

### 13.3 Why none of that can be kept

Modern Minecraft has no numeric block ids and no metadata. A block is a namespaced name plus named properties (`minecraft:oak_stairs[facing=north,half=top,shape=straight,waterlogged=false]`). The 1.13 flattening split every meta-packed block into separate blocks (16 wool colours, log species, slab types, door halves). Rotation is built into the game: `BlockState.rotate(Rotation)`. Block entities save and load themselves. So the storage unit, the remapping table and most of the rotation code have nothing to map onto.

### 13.4 What Buildcraft Reforged does

1. A slot becomes (position, `BlockState`, optional block-entity NBT, requirements). The state is written with the vanilla `BlockState` codec (name + property map), so a blueprint is readable by any world that has those blocks, which is what the id table was for.
2. The file is compressed NBT with two headers: the game's `DataVersion`, so vanilla data fixers can upgrade blueprints across future Minecraft versions, and a `BptVersion` of ours. Templates use the same container with presence bits.
3. Every 4.2.2 class name survives as a thin schematic. The `BptBlock` base delegates rotation to `BlockState.rotate`, requirements to the block's item plus block-entity contents for inventories, and validity to `canSurvive`. Subclasses remain only where vanilla gives no answer: Door and Bed (two halves placed as one unit), Piston (never copy the extended state), Fluid (place sources only), Dirt (4.2.2's grass → dirt rule), Ignore and Ignore Meta (skip lists), Inventory and Rotate Inventory (chests and furnaces carry contents as requirements), Sign (text), Lever and Wall Side (support checks), Custom Stack and Delegate (our own pipes, modules, engines and tanks store their own state).
4. Other mods' blocks work the way they did in 4.2.2: any block state plus whatever its block entity saves.
5. The Blueprint Library keeps its on-disk layout and index semantics, with the new file format inside. Loading a 1.6.4 file fails with a clear message rather than a crash.
6. Behaviour that stays identical: architect capture, builder build order and requirement checks, rotation on placement, filler and template handling, library sharing between players.

### 13.5 The 1.6.4 importer, in detail

**Timing (decided 29 Oct 2026):** not in 0.1.0. The importer ships in a later 0.x release, after the new blueprint format has been stable for at least one release, as its own phase P9 (section 15). Until then the library shows old `.bpt` files as "1.6.4 blueprint, import not yet available" and leaves them untouched on disk.

What it does: read an old `.bpt` text file and write a new-format blueprint, one way, once.

Why it is feasible:

- Vanilla block ids never changed between 1.6.4 and 1.12.2. Blocks added in 1.7 to 1.12 got new ids; old ones kept theirs.
- The game still ships the 1.12 → 1.13 flattening table. The class `BlockStateData` (in `net.minecraft.util.datafix.fixes`) maps `id << 4 | meta` to a block-state NBT for every vanilla block of 1.12. Calling it directly gives, for example, id 53 meta 2 → `minecraft:oak_stairs[facing=south,half=bottom,shape=straight]`. The resulting NBT then goes through the normal data fixer chain from DataVersion 1343 (1.12.2) to the current version, which handles every later rename (grass path → dirt path, sign → oak sign, and so on).
- Tile-entity NBT in the old file can be run through the same fixer chain as a `BLOCK_ENTITY` of the matching id, which is how the game itself upgrades old chunks.

What it cannot do automatically:

- BuildCraft's own blocks in 4.2.2 (engines, pipes, tanks, machines) were never in a vanilla table. The signature in the `idMap` gives their name (`tile.blockEngine`, `tile.pipe...`) and class name; a hand-written table of roughly 40 entries maps those to our block states. Pipes need a second table from the old pipe item id to our tier pipe plus module (a 4.2.2 diamond item pipe becomes a diamond tier pipe with a Sorter module; a wooden pipe becomes a Basic Extractor).
- Other mods' blocks from 1.6.4 (a RailCraft tank, a Forestry farm) cannot be mapped. The importer records them as "unknown block" slots, which the Builder skips, and lists them in the status panel of the library so the player knows what is missing.
- Requirements lists use 1.6.4 item ids and are recomputed from the new block states rather than imported.
- The old text format is Latin-1 with a hand-rolled NBT stream (`BptDataStream`); the reader is about 200 lines of careful parsing that must tolerate the files people actually have, including ones from 3.x with no `version:` line.

Cost: roughly one to two weeks on top of phase P6, almost all of it the two BuildCraft tables and testing against real old files. Risk: low for vanilla content, medium for BuildCraft blocks (the pipe mapping is a judgement call per pipe), nil for other mods' blocks (skipped by design).

Recommendation: ship 0.1.0 without it and make it a later 0.x release, because it is self-contained, touches no other module, and the people who still have 1.6.4 blueprint files are few. If you want it in 0.1.0, it goes at the end of P6 and the release date moves by that amount.

---

## 14. Decision log

| Date | Decision |
|---|---|
| 29 Oct 2026 | Baseline is tag 4.2.2, not 5.0.x (corrected: 5.0.0 targets Minecraft 1.7.2). |
| 29 Oct 2026 | Name "Buildcraft Reforged", mod id `buildcraftreforged`. No "community port" wording; the MMPL requires none. |
| 29 Oct 2026 | Oil fields as a placed feature in vanilla deserts and oceans; no new biomes, no dependency. |
| 29 Oct 2026 | Fake player with the owner's profile for quarry, mining well, builder and filler; respects protections and team claims. |
| 29 Oct 2026 | Tags only where 4.2.2 used the Ore Dictionary; exact vanilla items elsewhere. |
| 29 Oct 2026 | Integrations in 0.1.0: recipe viewer plugin, wrench tags, comparator output, waterlogged pipes and frames. |
| 29 Oct 2026 | Transport redesigned: instant routing with per-segment throughput caps, no per-item simulation; client animates from a per-segment record (last item, direction, rate); fluids and power drawn thicker with throughput. |
| 29 Oct 2026 | Ten material tiers, doubling per tier, items 1 → 512/s, fluids 20 → 10 240 mB/t, power 80 → 40 960 FE/t; one ladder for all three kinds; ender pearl pipe at tier 10. |
| 29 Oct 2026 | Pipe functions become modules crafted in the Assembly Table, one module per pipe; the full 4.2.2 set kept; Structure Pipe stays a block. Modules never need an engine. |
| 29 Oct 2026 | Extractor module runs at a fixed rate with no power. |
| 29 Oct 2026 | Basic Extractor: a separate nozzle-shaped block between inventory and pipe, driven by redstone engine pulses, animated as sucking items out (user sketch). Replaces the earlier "Wooden Extraction Pipe" idea. |
| 29 Oct 2026 | No destination or no capacity: items stay in the source; machines stop with a red light and a status reason. Nothing spills. |
| 29 Oct 2026 | Routing follows 4.2.2 junction rules on the graph. |
| 29 Oct 2026 | Engines: explosions configurable (default on), overheat timer (default 300 s) with rising whine, Engine Overheating gate trigger and heat comparator output. |
| 29 Oct 2026 | Status panel on every working block: state, blockage reason, flow rates, power, heat, owner. |
| 29 Oct 2026 | Publishing to CurseForge and GitHub only. |
| 29 Oct 2026 | Releases start at 0.1.0 and stay in the 0.x series, marked beta, while in development. 1.0.0 means full parity. |
| 29 Oct 2026 | Water springs dropped entirely. Oil spring kept for oil spouts (lives in the energy module with oil worldgen). |
| 29 Oct 2026 | Blueprints stored as block states in a new versioned format; 1.6.4 importer explained (13.5). |
| 29 Oct 2026 | 1.6.4 blueprint importer ships in a later 0.x release as phase P9, not in 0.1.0. |
| 29 Oct 2026 | Audit (section 19): remove `Version.java` and all network access. Buildcraft Reforged has no call-home of any kind. Client packets validated server-side, blueprint paths fixed, Nether fluid explosion replaced by fire, quarry chunk loading off by default, oil geysers capped, item lifespan back to vanilla. |
| 29 Oct 2026 | P0 versions pinned: NeoForge 26.3.0.99 (no longer labelled beta), ModDevGradle 2.0.148, Gradle 9.8.2, mod-publish-plugin 2.2.1 (2.x needs `client`/`server` set for CurseForge). The project lives at the repository root, not in a `buildcraft-reforged/` subfolder. `publishMods` is a dry run without `CURSEFORGE_TOKEN`. |
| 29 Oct 2026 | No CI checks on pull requests until the project nears release; `build.yml` comes back then. Only the tag-triggered `release.yml` exists. Builds and GameTests run locally meanwhile. |
| 29 Oct 2026 | Tooling stays on the newest LTS: Java 25, Node 24 for anything Node-based (GitHub Actions on `checkout@v7`, `setup-java@v6`, `setup-gradle@v6`, all Node 24). |

---

## 15. Phases and exit criteria

| Phase | Work | Done when |
|---|---|---|
| P0 Bootstrap | Repo, Gradle + ModDevGradle, licence and NOTICE, `reference/` vendored, CI build on PR, empty mod loads | CI green, mod shows in the mod list |
| P1 Core | Registries, config, network, GUI base classes, status panel framework, owner tracking and fake player utility, `SafeTimeTracker`, wrench with tags, markers, path markers, trigger/action framework, creative tab, JEI plugin skeleton | Markers, wrench and status panel work in game; a test asserts the mod opens no sockets during startup and the first 1000 ticks |
| P2 Energy | `PowerHandler` port, three engines + item + GUIs + renderer, overheat timer, whine sound, Engine Overheating trigger, comparator output, oil and fuel fluids and buckets, fuel/coolant registry, oil placed feature | Engines run, chain, overheat with alarm, explode or shut down per config; combustion engine burns oil and fuel with water cooling; oil fields generate in deserts and oceans |
| P3 Transport | Pipe network graph, routing walk with 4.2.2 junction rules, per-segment budgets, 30 tier pipes (10 materials × 3 kinds), Structure Pipe, Basic Extractor with suction animation, 10 modules with GUIs, flow record sync and renderer, facades, plugs, pipe wire, gates with all triggers and actions, Filtered Buffer, waterlogging | Every pipe and module in `PARITY.md` ticked "behaves"; a 10-tier throughput GameTest passes; flow visuals match rates |
| P4 Factory | Quarry + frame + arm renderer + chunk loading + fake player, mining well, pump, refinery, tank, auto workbench, hopper, flood gate, comparator outputs, red-light blocked state | Quarry completes a 9×9, stops in a protected claim, stops with red light when output is blocked; refinery makes fuel at 4.2.2 rates |
| P5 Silicon | Laser + renderer, assembly table + recipes, advanced crafting table, chipsets, gate crafting, facade crafting, module crafting | All 4.2.2 assembly recipes plus the 10 module recipes present and priced × 10 FE |
| P6 Builders | Architect, blueprint/template items, builder, filler + patterns, blueprint library, schematics on block states (section 13), fake player for placement and breaking | A blueprint of a house with doors, stairs, chests builds correctly, rotated |
| P7 Bug fix sweep | Second pass over the harvest sources per class; update `BUGFIXES.md` | Every candidate has a decision |
| P8 Parity QA and release | Reference 1.6.4 instance side by side for machines and engines; GameTests; CurseForge project set up; `v0.1.0` tag | Beta release published automatically |
| P9 1.6.4 blueprint importer (later 0.x) | Old `.bpt` text reader, flattening table for vanilla ids, hand table for BC blocks and pipes, unknown-block slots, library import button | A 4.2.2 blueprint with doors, chests, pipes and an engine rebuilds in the new world; unknown blocks listed on the library panel |

P9 starts only after a 0.x release has shipped the new format. P3 is the largest and newest piece and should start as soon as P1 is in; P4 and P5 can proceed in parallel with P3 once P2 is in. P6 is the riskiest port.

---

## 16. CI and automatic CurseForge upload

### gradle.properties

```
mod_id=buildcraftreforged
mod_version=0.1.0
minecraft_version=26.3
neo_version=26.3.0.99
curseforge_project_id=          # empty until the CurseForge project exists (#3)
```

### build.gradle.kts (publishing part)

```kotlin
plugins {
    id("net.neoforged.moddev") version "2.0.148"
    id("me.modmuss50.mod-publish-plugin") version "2.2.1"
}

publishMods {
    file = tasks.jar.flatMap { it.archiveFile }
    changelog = providers.fileContents(layout.projectDirectory.file("CHANGELOG.md")).asText
        .map { it.substringAfter("## [").substringBefore("\n## [") }   // top section only
    type = if (project.version.toString().startsWith("0.")) BETA else STABLE   // 0.x = development series
    modLoaders.add("neoforge")
    displayName = "Buildcraft Reforged ${project.version} for Minecraft ${minecraft_version}"

    curseforge {
        accessToken = providers.environmentVariable("CURSEFORGE_TOKEN")
        projectId = curseforge_project_id
        minecraftVersions.add(minecraft_version)
        javaVersions.add(JavaVersion.VERSION_25)
        optional("jei")
    }
    github {
        accessToken = providers.environmentVariable("GITHUB_TOKEN")
        repository = "Kvaksrud/Buildcraft-Reforged"
        commitish = "main"
        tagName = "v${project.version}"
    }
}
```

Jar name: `buildcraftreforged-<mod_version>+mc<minecraft_version>.jar`.

### .github/workflows/release.yml

```yaml
name: Release
on:
  push:
    tags: ["v*"]
jobs:
  publish:
    runs-on: ubuntu-latest
    permissions:
      contents: write
    steps:
      - uses: actions/checkout@v7
      - uses: actions/setup-java@v6
        with: { distribution: temurin, java-version: "25" }
      - uses: gradle/actions/setup-gradle@v6
      - run: ./gradlew build --no-daemon
      - run: ./gradlew publishMods --no-daemon
        env:
          CURSEFORGE_TOKEN: ${{ secrets.CURSEFORGE_TOKEN }}
          GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
```

`build.yml` (the same build plus `runGameTestServer` on every PR and push to `main`) is left out until the project nears release; until then builds and GameTests run locally.

Release procedure: bump `mod_version`, write the CHANGELOG section, commit, `git tag v0.1.0`, `git push --tags`. Nothing else is manual. The CurseForge project (name "Buildcraft Reforged", MMPL, source link) must exist and `CURSEFORGE_TOKEN` must be a repository secret before the first tag.

---

## 17. Testing and parity verification

- `docs/PARITY.md`: one row per item in sections 2 and 8, columns compiles / renders / behaves / fixes applied.
- Reference instance: Minecraft 1.6.4 + Forge 9.11.1.949 + BuildCraft 4.2.2 in Prism Launcher, for side-by-side checks of machine GUIs, rates and behaviour. Transport is checked against `docs/TRANSPORT.md` instead.
- Unit tests: `PowerHandler`, routing walk against hand-built graphs (every module rule), per-segment budgets, filler patterns, gate logic, ladder config parsing.
- GameTests: engine chain delivers expected FE; engine overheats, raises the trigger and comparator, then explodes or shuts down per config; extractor at each tier moves the right items per second; routing through Director, Sorter, Colour Sorter reaches the 4.2.2 destination; blocked machine shows red light and resumes when cleared; quarry mines a fixed structure and refuses a protected block; pump drains a 3×3 pool; refinery converts oil to fuel; builder rebuilds a template and a blueprint with doors and chests.
- Manual script per release: a world checklist covering every block, module and status panel.

---

## 18. Remaining open items and risks

| Id | Item | Status / recommendation |
|---|---|---|
| A | Oil biomes | **Decided**: placed feature. |
| B | Blueprint storage | **Decided**: block states, new format. Importer in a later 0.x release as P9, not 0.1.0 (13.5). |
| C | Pipe rendering performance | Mitigated by design: cached model per connection mask, renderer only on segments with flow, per-segment record at most twice a second. Profile in P3. |
| D | NeoForge 26.x moves fast | Pin exact versions in `gradle.properties`; keep version-specific code in `platform/`. |
| E | Textures are 16× and 2013-era; new art needed for three materials, ten modules, red light, flow frames | Keep the style. Budget art time in P3. Resource packs can replace. |
| F | Separate BuildCraft-Localization repository | English only in 0.x; check that repo's licence before importing other languages. |
| G | Name | **Decided**: "Buildcraft Reforged", as is. |
| H | Balance of the tier ladder | Values in config; revisit after first playtest. |
| I | Basic Extractor next to Extractor module | Two ways to extract: the engine-driven starter block and the free-running module. Keep both as decided; the recipe viewer page explains which to use when. |

---

## 19. Code audit of 4.2.2: call-home, dangerous code, annoyances

Audited 29 Oct 2026 against tag 4.2.2. Scope: every outbound network call, every file write, every reflection or native hook, every world-damaging action, and the server-side packet handlers.

### 19.1 Call-home (remove)

| Where | What it does | Action |
|---|---|---|
| `core/Version.java` | On startup, when `update.check` is true (the default), a background thread fetches `http://bit.ly/buildcraftversion` up to three times, following redirects, with a spoofed Firefox 3 user agent. Result drives an "outdated" chat notice and colours `/buildcraft version`. | **Delete the class.** Mod version comes from `gradle.properties`; CurseForge and the launcher handle update notices. No config key replaces it. |
| `core/Version.java` | `/buildcraft changelog` fetches `https://dl.dropboxusercontent.com/u/38558957/Minecraft/Buildcraft/changelogs/<version>` from a personal Dropbox. | **Delete** with the class. Changelog lives in the GitHub release. |
| `core/utils/BCLog.java` | Logs the project URL once at startup. | Harmless; replace with the Buildcraft Reforged repository URL. |

Nothing else opens a socket. No telemetry, no analytics, no remote config.

### 19.2 Dangerous code (fix in the rewrite)

| Where | Problem | Action |
|---|---|---|
| `core/network/PacketHandler.java`, `transport/network/PacketHandlerTransport.java`, `builders/network/PacketHandlerBuilders.java`, `factory/…`, `silicon/…` | One handler class serves both sides. A client can send `TILE_UPDATE`, `STATE_UPDATE`, `PIPE_CONTENTS`, `PIPE_POWER`, library and refinery packets to the server, which looks up the tile at the packet's coordinates and applies the data. No check that the sender is near the tile, owns it, or has its container open (gate packets are the exception: they check `openContainer`). | Server-bound payloads are a separate, small set (GUI actions only). Every handler verifies `player.containerMenu` is the matching menu for that block, or that the player is within reach of the position. Tile-sync payloads are client-bound only. NeoForge's `PayloadRegistrar` enforces direction. |
| `core/blueprints/BptBase.java:112`, `BptPlayerIndex.java:22`, `BptRootIndex.java:36` | Blueprint library writes `blueprints/` relative to the process working directory, not the world or game directory. On a server started from another folder the files land in the wrong place; two servers on one host share one library. Player names are used as index file names. | Store under `<gamedir>/buildcraft-reforged/blueprints/` on the client and `<world>/buildcraft-reforged/blueprints/` on the server (see 13.2). Index by player UUID, sanitise user-typed blueprint names to `[A-Za-z0-9 _-]`, and reject any name that resolves outside the library directory. |
| `energy/BlockBuildcraftFluid.java:59` | Any neighbour change of an oil or fuel block in the Nether triggers a 4-block TNT-strength explosion with fire, then removes the block. A single bucket of fuel placed in the Nether is a griefing tool; a pipe leak chains. | Keep the rule that oil and fuel ignite in the Nether (it is 4.2.2 behaviour), but replace the explosion with fire: set the block on fire and remove it, no `explode`. Config `fluids.netherIgnition`, default on. |
| `energy/BlockBuildcraftFluid.java:80–100` | Oil and fuel are flammable with fire spread 300 and act as a fire source. Fuel lakes next to lava burn off and spread fire to wood. | Keep (expected behaviour, and a reason to pipe fuel properly). Documented in the status panel for tanks: "Flammable". |
| `energy/TileEngine.java:385` | Overheated engine explodes and deletes itself. | Already decided in section 9: configurable, default on, with a 300 s overheat timer, alarm trigger and comparator output first. |
| `transport/PipeTransportItems.java:133–148` | A pipe holding more than 64 stacks or 1024 items destroys itself ("Pipe exploded") and drops everything. | Moot: the new transport has no in-pipe inventory. Budgets cannot overflow; items stay in the source. |
| `api/transport/FacadeManager.java:18`, `transport/Pipe.java:106`, `transport/ItemFacade.java:97`, `factory/FactoryProxyClient.java:53` | Reflection: class lookup by name for facades and NEI, `getDeclaredFields` over `Block` to enumerate facade candidates, `getDeclaredMethod("eventHandler", …)` to dispatch pipe events. Not malicious, but fragile and slow. | Facades enumerate the block registry. Pipe events become a typed `PipeEventBus` with explicit registration. NEI hook replaced by the JEI/EMI plugin. No reflection remains. |
| `core/BlockSpring.java:43` | Spring blocks are unbreakable (hardness −1), so an oil spring under a base can never be removed in survival. | Oil spring block gets normal hardness (obsidian level) and drops nothing. Water springs are dropped entirely (section 3). |

No `Runtime.exec`, `ProcessBuilder`, `System.exit`, `Unsafe`, class loading from disk, or native code anywhere in 4.2.2.

### 19.3 Annoying features (gameplay)

| Feature | 4.2.2 behaviour | Decision |
|---|---|---|
| Version-check chat spam | "Outdated" notice in chat on every login until the config marks it seen. | Gone with `Version.java`. |
| Oil geysers | Large wells (0.04 % of chunks, ×3 in desert and taiga, ×30 in the oil-desert biome) put a column of oil from bedrock level to above the surface, with a cross of oil blocks around it. They are visible from far away and the top block flows over the landscape. | Keep large wells underground and cap the column at the surface: no block above ground level. The 1 in 4 oil spring at the bottom of a large well stays. |
| Oil lakes | 2 % of chunks in oil biomes get a surface lake. Combined with the oil-biome ×30 multiplier, the dedicated biomes were almost solid oil. | Oil biomes are already gone (section 11). Vanilla desert and ocean keep the ×3 bonus only. Config `worldgen.oilWellScalar`, default 1.0, and a `worldgen.oil` toggle. |
| Pump drains lakes | Water is infinite only when the pump has found at least 9 source blocks and `consumeWater` is false (default). Smaller pools are consumed. | Keep as is: it matches vanilla's infinite-water rule and is well understood. |
| Quarry chunk loading | Every quarry takes a Forge chunk ticket for its own chunk and the whole frame area, unconditionally, with no config. Ticket limits per player make later quarries silently refuse to run ("no more chunkloaders available"). | Config `quarry.loadsChunks`, default **off**. A quarry without the flag just pauses when its chunks unload. With FTB Chunks installed the player is expected to claim the area; the quarry only force-loads its own chunk when the config is on. |
| Quarry and mining well leftovers | Quarry frames stay when the quarry is removed. Mining-well pipe column is removed on break (correct). | Breaking a quarry removes its frame blocks. A quarry that has finished shows "Done" on the status panel and stops drawing power. |
| Dropped items from fillers and pipes | `dropBrokenBlocks` default on, `itemLifespan` 1200 ticks (one minute) instead of vanilla's 6000. Items on the ground vanish fast, which surprises players who expected a chest. | Keep `dropBrokenBlocks` on. Raise `itemLifespan` default to vanilla 6000; the short lifespan only existed because pipes used to spill. No spilling in the new transport, so nothing needs to expire early. |
| Filler and builder dig through protected areas | `BlockUtil.canChangeBlock` refuses only unbreakable blocks (hardness below 0). | Section 11 fake player applies: every placement and removal goes through `BlockEvent.BreakEvent` and `EntityPlaceEvent`, so claim mods can veto. |
| Engine explosions | Instant explosion at overheat with no warning. | Section 9: timer, whine, alarm trigger, then explosion or shutdown per config. |
| Water springs | Spawn under random desert sand and flood holes forever; unbreakable. | Dropped (section 3). |
| `/buildcraft` command | Prints version colour-coded by update state and fetches changelog. | Keep a `/buildcraftreforged version` that prints the local version only. |
| Unfinished pollution system | `BlockPollution`, `TilePollution` and a commented-out `createPollution` in `BuildCraftEnergy`. Dead code. | Not ported. |
| Debug mode | `BuildCraftCore.debugMode` forces oil springs on every large well and adds developer items. | Keep as a dev-only system property, not a config key. |

### 19.4 Changes to earlier sections (applied 29 Oct 2026)

- Section 3 "Deliberate additions": add packet validation, blueprint paths, quarry chunk-load config, item lifespan 6000, oil geyser cap, frame removal on quarry break.
- Section 14 decision log: entry "29 Oct 2026, audit: remove Version.java and all network access; no call-home of any kind in Buildcraft Reforged".
- Section 15 P1 exit criteria: a test asserts that the mod opens no sockets during startup and the first 1000 ticks.
