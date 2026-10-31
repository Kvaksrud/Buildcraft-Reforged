# Parity checklist

One row for every block, item, pipe, GUI, trigger and action that BuildCraft 4.2.2 registers, plus the content that
replaces or extends it. 1.0.0 ships when every row is ticked "behaves" (`docs/PLAN.md` section 1).

## How to read and update this file

- **Source.** Rows come from the registrations in `reference/common/buildcraft/`: the six `BuildCraft*.java` module
  classes, the trigger and action providers (`DefaultTriggerProvider`,
  `DefaultActionProvider`, `PipeTriggerProvider`, `BuildersActionProvider`, and the `getTriggers`/`getActions`
  overrides on `TileEngine`, `TileGenericPipe`, `GateVanilla` and the pipes), and the five `GuiHandler`s. Modules are
  listed in the order of the 4.2.2 module classes.
- **New registry name.** Section 6's rule as implemented in #18 (`RegistryNames.of`): the 4.2.2 unlocalised name,
  camel case to snake case, dots to underscores (`engineWood` → `engine_wood`, `spring.oil` → `spring_oil`). Names
  built from a metadata number (`pipe_gate_0`, `redstone_chipset_0`) are placeholders; the owning issue may rename
  them when it splits the variants. Triggers and actions are entries of the `buildcraftreforged:trigger` and
  `buildcraftreforged:action` registries (#31), named after their 4.2.2 `BuildCraft*` field by the same rule
  (`triggerMachineActive` → `trigger_machine_active`). Rows still showing a dotted name list the 4.2.2 unique tag
  until their issue registers them; `ActionManager.triggers`/`actions` still resolve that tag. GUIs name the menu they become.
- **Issue.** The 0.1.0 sub-issue that ports the row. Two or more issues means the row is done when all of them are.
- **Ticks.** Change `[ ]` to `[x]` in the same pull request that makes it true: *compiles* (registered and builds),
  *renders* (model, texture or screen in game), *behaves* (matches 4.2.2 plus the plan's deliberate changes,
  checked against a 1.6.4 reference instance in P8). `—` means the row is dropped and needs no ticks.
- **Pipes.** The 30 pipes of 4.2.2 do not come back one to one. Each row says which tier pipe and module replace it
  (section 8). The tier pipes and modules have their own rows under "New in Buildcraft Reforged".
- **GUI count.** The five `GuiHandler`s open 16 screens. Section 6 of the plan says 31 GUIs; the 16 here are what
  4.2.2 registers.

## Row counts

| Module | Rows | Of which |
|---|---|---|
| core | 27 | blocks and items 8, triggers 15, actions 4 |
| energy | 19 | blocks and items 9, worldgen 4, triggers 4, GUIs 2 |
| factory | 13 | blocks 10, GUIs 3 |
| silicon | 10 | blocks and items 8, GUIs 2 |
| transport | 111 | blocks and items 23, pipes 30, triggers 14, actions 39, GUIs 5 |
| builders | 19 | blocks and items 8, actions 7, GUIs 4 |
| **4.2.2 total** | **199** | |
| new in Reforged | 44 | tier pipes 30, pipe modules 10, other additions 4 |

## core (`BuildCraftCore`)

### Blocks and items (core)

| 4.2.2 name | 4.2.2 class | New registry name | Issue | Compiles | Renders | Behaves | Notes |
|---|---|---|---|---|---|---|---|
| Wrench (`wrenchItem`) | `ItemWrench` | `wrench_item` | #28 | [x] | [ ] | [ ] | Plus `c:tools/wrench` tag both ways (section 6). |
| Oil spring (`spring.oil`) | `BlockSpring + ItemSpring (meta 1)` | `spring_oil` | #45 | [ ] | [ ] | [ ] | Lives with oil worldgen; normal hardness, drops nothing (section 19.2). |
| Water spring (`spring.water`) | `BlockSpring + ItemSpring (meta 0)` | dropped | — | — | — | — | Dropped with its worldgen and config entry (section 3, Dropped). |
| Wooden Gear (`woodenGearItem`) | `ItemBuildCraft` | `wooden_gear_item` | #18, #21 | [x] | [ ] | [ ] | `ItemBuildCraft` since #21. |
| Stone Gear (`stoneGearItem`) | `ItemBuildCraft` | `stone_gear_item` | #18, #21 | [x] | [ ] | [ ] | `ItemBuildCraft` since #21. |
| Iron Gear (`ironGearItem`) | `ItemBuildCraft` | `iron_gear_item` | #18, #21 | [x] | [ ] | [ ] | `ItemBuildCraft` since #21. |
| Gold Gear (`goldGearItem`) | `ItemBuildCraft` | `gold_gear_item` | #18, #21 | [x] | [ ] | [ ] | `ItemBuildCraft` since #21. |
| Diamond Gear (`diamondGearItem`) | `ItemBuildCraft` | `diamond_gear_item` | #18, #21 | [x] | [ ] | [ ] | `ItemBuildCraft` since #21. |

### Triggers (core, `DefaultTriggerProvider` and `TileGenericPipe`)

| 4.2.2 name | 4.2.2 class | New registry name | Issue | Compiles | Renders | Behaves | Notes |
|---|---|---|---|---|---|---|---|
| Machine active | `TriggerMachine` | `trigger_machine_active` | #31 | [x] | [ ] | [ ] | 4.2.2 tag `buildcraft.work.scheduled`. GameTest `trigger_*`; icon drawn by the gate GUI (#60). |
| Machine inactive | `TriggerMachine` | `trigger_machine_inactive` | #31 | [x] | [ ] | [ ] | 4.2.2 tag `buildcraft.work.done`. GameTest `trigger_*`; icon drawn by the gate GUI (#60). |
| Inventory empty | `TriggerInventory` | `trigger_empty_inventory` | #31 | [x] | [ ] | [ ] | 4.2.2 tag `buildcraft.inventory.empty`. GameTest `trigger_*`; icon drawn by the gate GUI (#60). |
| Inventory contains | `TriggerInventory` | `trigger_contains_inventory` | #31 | [x] | [ ] | [ ] | 4.2.2 tag `buildcraft.inventory.contains`. GameTest `trigger_*`; icon drawn by the gate GUI (#60). |
| Inventory space | `TriggerInventory` | `trigger_space_inventory` | #31 | [x] | [ ] | [ ] | 4.2.2 tag `buildcraft.inventory.space`. GameTest `trigger_*`; icon drawn by the gate GUI (#60). |
| Inventory full | `TriggerInventory` | `trigger_full_inventory` | #31 | [x] | [ ] | [ ] | 4.2.2 tag `buildcraft.inventory.full`. GameTest `trigger_*`; icon drawn by the gate GUI (#60). |
| Inventory below 25% | `TriggerInventoryLevel` | `trigger_inventory_below25` | #31 | [x] | [ ] | [ ] | 4.2.2 tag `buildcraft.inventorylevel.below_25`. GameTest `trigger_*`; icon drawn by the gate GUI (#60). |
| Inventory below 50% | `TriggerInventoryLevel` | `trigger_inventory_below50` | #31 | [x] | [ ] | [ ] | 4.2.2 tag `buildcraft.inventorylevel.below_50`. GameTest `trigger_*`; icon drawn by the gate GUI (#60). |
| Inventory below 75% | `TriggerInventoryLevel` | `trigger_inventory_below75` | #31 | [x] | [ ] | [ ] | 4.2.2 tag `buildcraft.inventorylevel.below_75`. GameTest `trigger_*`; icon drawn by the gate GUI (#60). |
| Fluid tank empty | `TriggerFluidContainer` | `trigger_empty_fluid` | #31 | [x] | [ ] | [ ] | 4.2.2 tag `buildcraft.fluid.empty`. GameTest `trigger_*`; icon drawn by the gate GUI (#60). |
| Fluid tank contains | `TriggerFluidContainer` | `trigger_contains_fluid` | #31 | [x] | [ ] | [ ] | 4.2.2 tag `buildcraft.fluid.contains`. GameTest `trigger_*`; icon drawn by the gate GUI (#60). |
| Fluid tank space | `TriggerFluidContainer` | `trigger_space_fluid` | #31 | [x] | [ ] | [ ] | 4.2.2 tag `buildcraft.fluid.space`. GameTest `trigger_*`; icon drawn by the gate GUI (#60). |
| Fluid tank full | `TriggerFluidContainer` | `trigger_full_fluid` | #31 | [x] | [ ] | [ ] | 4.2.2 tag `buildcraft.fluid.full`. GameTest `trigger_*`; icon drawn by the gate GUI (#60). |
| Redstone input active | `TriggerRedstoneInput` | `trigger_redstone_active` | #60 | [x] | [ ] | [ ] | 4.2.2 tag `buildcraft.redtone.input.active`. Offered by the pipe itself (`TileGenericPipe.getTriggers`). Tag misspelt in 4.2.2. GameTest `gate_*`. |
| Redstone input inactive | `TriggerRedstoneInput` | `trigger_redstone_inactive` | #60 | [x] | [ ] | [ ] | 4.2.2 tag `buildcraft.redtone.input.inactive`. As above. GameTest `gate_*`. |

### Actions (core, `DefaultActionProvider`)

| 4.2.2 name | 4.2.2 class | New registry name | Issue | Compiles | Renders | Behaves | Notes |
|---|---|---|---|---|---|---|---|
| Redstone output | `ActionRedstoneOutput` | `action_redstone` | #31 | [x] | [ ] | [ ] | 4.2.2 tag `buildcraft.redstone.output`. GameTest `trigger_*`; icon drawn by the gate GUI (#60). |
| Machine on | `ActionMachineControl` | `action_on` | #31 | [x] | [ ] | [ ] | 4.2.2 tag `buildcraft.machine.on`. GameTest `trigger_*`; icon drawn by the gate GUI (#60). |
| Machine off | `ActionMachineControl` | `action_off` | #31 | [x] | [ ] | [ ] | 4.2.2 tag `buildcraft.machine.off`. GameTest `trigger_*`; icon drawn by the gate GUI (#60). |
| Machine loop | `ActionMachineControl` | `action_loop` | #31 | [x] | [ ] | [ ] | 4.2.2 tag `buildcraft.machine.loop`. GameTest `trigger_*`; icon drawn by the gate GUI (#60). |

## energy (`BuildCraftEnergy`)

### Blocks and items (energy)

| 4.2.2 name | 4.2.2 class | New registry name | Issue | Compiles | Renders | Behaves | Notes |
|---|---|---|---|---|---|---|---|
| Redstone Engine (`tile.engineWood`) | `BlockEngine (meta 0) + ItemEngine, TileEngineWood` | `engine_wood` | #37, #39 | [x] | [ ] | [ ] | One block per engine; facing in the block state. Engine base (`BlockEngine`, `ItemEngine`, `TileEngine`, `EnergyRegistry.engine`) done in #36; renderer, base/trunk/chamber textures and the `buildcraftreforged:engine` item model type in #39. 10 FE per 16 ticks, 10 FE pulses, 1 000 FE; power pipes connect (4.2.2 refused every pipe). GameTests `engine_wood_redstone_output`, `engine_delivery_through_kinesis_pipe`; model and piston not checked in game. |
| Steam (Stirling) Engine (`tile.engineStone`) | `BlockEngine (meta 1) + ItemEngine, TileEngineStone` | `engine_stone` | #38, #39 | [x] | [ ] | [ ] | Renderer and textures from #39. Furnace burn times (`cooking_fuel`), PID output 10 / 3 to 10 FE/t, status OUT_OF_FUEL. GameTests `engine_stone_burns_fuel`, `engine_stone_output_control`, `engine_delivery_through_kinesis_pipe`; model and piston not checked in game. Inventory triggers wait for #41. |
| Combustion Engine (`tile.engineIron`) | `BlockEngine (meta 2) + ItemEngine, TileEngineIron` | `engine_iron` | #44, #39 | [ ] | [ ] | [ ] | Renderer and textures from #39; the engine adds its blockstate and item model (`docs/PORTING-NOTES.md`, #39). |
| Oil (fluid) (`oil`) | `BCFluid` | `oil` | #42 | [x] | [ ] | [ ] | `FluidType` `oil` plus source fluid `oil` and flowing fluid `flowing_oil`. Density 800, viscosity 1500, ticks every 7. |
| Fuel (fluid) (`fuel`) | `BCFluid` | `fuel` | #42 | [x] | [ ] | [ ] | As above: `fuel` and `flowing_fuel`, ticks every 5. |
| Oil (block) (`blockOil`) | `BlockBuildcraftFluid` | `block_oil` | #42 | [x] | [ ] | [ ] | Nether: fire instead of explosion (section 19.2). Flow, buckets and the Nether rule pass GameTests; textures and drips not seen in a client. |
| Fuel (block) (`blockFuel`) | `BlockBuildcraftFluid` | `block_fuel` | #42 | [x] | [ ] | [ ] | As above. |
| Oil Bucket (`bucketOil`) | `ItemBucketBuildcraft` | `bucket_oil` | #42 | [x] | [ ] | [ ] | Places, picks up and fills tanks (GameTest). |
| Fuel Bucket (`bucketFuel`) | `ItemBucketBuildcraft` | `bucket_fuel` | #42 | [x] | [ ] | [ ] | As above. |

### Worldgen (energy)

| 4.2.2 name | 4.2.2 class | New registry name | Issue | Compiles | Renders | Behaves | Notes |
|---|---|---|---|---|---|---|---|
| Desert Oil Field biome | `BiomeGenOilDesert` | replaced | #45 | [ ] | [ ] | [ ] | Placed feature in vanilla deserts, no biome id (section 6). |
| Ocean Oil Field biome | `BiomeGenOilOcean` | replaced | #45 | [ ] | [ ] | [ ] | Placed feature in vanilla oceans, no biome id (section 6). |
| Oil wells and spouts | `OilPopulate` | replaced | #45 | [ ] | [ ] | [ ] | Capped at ground level (section 19.3). |
| Water springs | `SpringPopulate` | dropped | — | — | — | — | Section 3, Dropped. |

### Triggers (energy, `TileEngine.getTriggers`)

| 4.2.2 name | 4.2.2 class | New registry name | Issue | Compiles | Renders | Behaves | Notes |
|---|---|---|---|---|---|---|---|
| Engine heat blue | `TriggerEngineHeat` | `buildcraft.engine.stage.blue` | #41 | [ ] | [ ] | [ ] | Thresholds × 10 (section 7). |
| Engine heat green | `TriggerEngineHeat` | `buildcraft.engine.stage.green` | #41 | [ ] | [ ] | [ ] |  |
| Engine heat yellow | `TriggerEngineHeat` | `buildcraft.engine.stage.yellow` | #41 | [ ] | [ ] | [ ] |  |
| Engine heat red | `TriggerEngineHeat` | `buildcraft.engine.stage.red` | #41 | [ ] | [ ] | [ ] |  |

### GUIs (energy, `energy.GuiHandler`)

| 4.2.2 name | 4.2.2 class | New registry name | Issue | Compiles | Renders | Behaves | Notes |
|---|---|---|---|---|---|---|---|
| Combustion engine (`GuiIds.ENGINE_IRON` 20) | `GuiCombustionEngine / ContainerEngine` | `engine_iron` menu | #44 | [ ] | [ ] | [ ] |  |
| Stirling engine (`GuiIds.ENGINE_STONE` 21) | `GuiStoneEngine / ContainerEngine` | `engine_stone` menu | #38 | [x] | [ ] | [ ] | Engine ledger in FE, plus the status ledger. Slots, shift-click and burn-time sync covered by GameTests `engine_stone_menu` and `engine_stone_burns_fuel`; drawing, flame and ledgers not checked in game. |

## factory (`BuildCraftFactory`)

### Blocks (factory)

| 4.2.2 name | 4.2.2 class | New registry name | Issue | Compiles | Renders | Behaves | Notes |
|---|---|---|---|---|---|---|---|
| Mining Well (`miningWellBlock`) | `BlockMiningWell, TileMiningWell` | `mining_well_block` | #66 | [ ] | [ ] | [ ] |  |
| Mining Pipe (`plainPipeBlock`) | `BlockPlainPipe` | `plain_pipe_block` | #66 | [ ] | [ ] | [ ] |  |
| Automatic Crafting Table (`autoWorkbenchBlock`) | `BlockAutoWorkbench, TileAutoWorkbench` | `auto_workbench_block` | #69 | [ ] | [ ] | [ ] |  |
| Frame (`frameBlock`) | `BlockFrame` | `frame_block` | #67 | [ ] | [ ] | [ ] | Waterloggable; removed with its quarry (section 19.3). |
| Quarry (`machineBlock`) | `BlockQuarry, TileQuarry` | `machine_block` | #67 | [ ] | [ ] | [ ] | Rule gives `machine_block`; section 6's example says `quarry`. #67 picks one and updates this row. |
| Tank (`tankBlock`) | `BlockTank, TileTank` | `tank_block` | #64 | [x] | [ ] | [ ] | Comparator output added (BuildCraft 7.1 formula). GameTests `tank_stacking`, `tank_transfer`, `tank_buckets`; model and fluid renderer not checked in game. |
| Pump (`pumpBlock`) | `BlockPump, TilePump` | `pump_block` | #65 | [ ] | [ ] | [ ] |  |
| Flood Gate (`floodGateBlock`) | `BlockFloodGate, TileFloodGate` | `flood_gate_block` | #65 | [ ] | [ ] | [ ] |  |
| Refinery (`refineryBlock`) | `BlockRefinery, TileRefinery` | `refinery_block` | #68 | [x] | [ ] | [ ] | JSON recipe type `buildcraftreforged:refinery`; status panel added. GameTests `refinery_oil_to_fuel`, `refinery_filters_and_blocked`, `refinery_recipes`; renderer and item model not checked in game. `BptBlockRefinery` waits for the builder (#78). |
| Hopper (`blockHopper`) | `BlockHopper, TileHopper` | `block_hopper` | #70 | [ ] | [ ] | [ ] |  |

### GUIs (factory, `factory.GuiHandler`)

| 4.2.2 name | 4.2.2 class | New registry name | Issue | Compiles | Renders | Behaves | Notes |
|---|---|---|---|---|---|---|---|
| Automatic Crafting Table (`GuiIds.AUTO_CRAFTING_TABLE` 30) | `GuiAutoCrafting / ContainerAutoWorkbench` | `auto_workbench_block` menu | #69 | [ ] | [ ] | [ ] |  |
| Refinery (`GuiIds.REFINERY` 31) | `GuiRefinery / ContainerRefinery` | `refinery_block` menu | #68 | [x] | [ ] | [ ] | Filter sync and return covered by GameTest `refinery_filters_and_blocked`; drawing and clicks not checked in game. |
| Hopper (`GuiIds.HOPPER` 32) | `GuiHopper / ContainerHopper` | `block_hopper` menu | #70 | [ ] | [ ] | [ ] |  |

## silicon (`BuildCraftSilicon`)

### Blocks and items (silicon)

| 4.2.2 name | 4.2.2 class | New registry name | Issue | Compiles | Renders | Behaves | Notes |
|---|---|---|---|---|---|---|---|
| Laser (`laserBlock`) | `BlockLaser, TileLaser` | `laser_block` | #71 | [x] | [ ] | [ ] | FE (MJ × 10). Beam and model not yet seen in a client. GameTests `laser_*`. |
| Assembly Table (`tile.assemblyTableBlock`) | `BlockLaserTable (meta 0) + ItemLaserTable, TileAssemblyTable` | `assembly_table_block` | #72 | [x] | [ ] | [ ] | Separate block, no metadata. JSON recipe type `buildcraftreforged:assembly`, energy in FE (MJ × 10). Model not yet seen in a client; the 4.2.2 recipes wait for their items (#60, #61, #74). GameTests `assembly_*`. |
| Advanced Crafting Table (`tile.assemblyWorkbenchBlock`) | `BlockLaserTable (meta 1) + ItemLaserTable, TileAdvancedCraftingTable` | `assembly_workbench_block` | #73 | [ ] | [ ] | [ ] | Separate block, no metadata. |
| Redstone Chipset (`redstoneChipset.0`) | `ItemRedstoneChipset (meta 0)` | `redstone_chipset_0` | #74 | [ ] | [ ] | [ ] | Metadata suffix names are placeholders; #18 notes they become items or a data component. |
| Redstone Iron Chipset (`redstoneChipset.1`) | `ItemRedstoneChipset (meta 1)` | `redstone_chipset_1` | #74 | [ ] | [ ] | [ ] |  |
| Redstone Golden Chipset (`redstoneChipset.2`) | `ItemRedstoneChipset (meta 2)` | `redstone_chipset_2` | #74 | [ ] | [ ] | [ ] |  |
| Redstone Diamond Chipset (`redstoneChipset.3`) | `ItemRedstoneChipset (meta 3)` | `redstone_chipset_3` | #74 | [ ] | [ ] | [ ] |  |
| Pulsating Chipset (`redstoneChipset.4`) | `ItemRedstoneChipset (meta 4)` | `redstone_chipset_4` | #74 | [ ] | [ ] | [ ] |  |

### GUIs (silicon, `silicon.GuiHandler`)

| 4.2.2 name | 4.2.2 class | New registry name | Issue | Compiles | Renders | Behaves | Notes |
|---|---|---|---|---|---|---|---|
| Assembly Table (GUI id 0, block meta) | `GuiAssemblyTable / ContainerAssemblyTable` | `assembly_table_block` menu | #72 | [x] | [ ] | [ ] | Menu, data slots and the selection GUI return tested by GameTests `assembly_*`; the screen not yet seen in a client. |
| Advanced Crafting Table (GUI id 1, block meta) | `GuiAdvancedCraftingTable / ContainerAdvancedCraftingTable` | `assembly_workbench_block` menu | #73 | [ ] | [ ] | [ ] |  |

## transport (`BuildCraftTransport`)

### Blocks and items (transport)

| 4.2.2 name | 4.2.2 class | New registry name | Issue | Compiles | Renders | Behaves | Notes |
|---|---|---|---|---|---|---|---|
| Pipe block (`pipeBlock`) | `BlockGenericPipe, TileGenericPipe` | `pipe_block` | #48 | [ ] | [ ] | [ ] | Replaced by the tier pipe blocks below; kept as the shared pipe base. |
| Pipe Sealant (`pipeWaterproof`) | `ItemBuildCraft` | `pipe_waterproof` | #53 | [x] | [ ] | [ ] | 4.2.2 fluid-pipe ingredient, kept: fluid pipes are item pipe + sealant (TRANSPORT.md Q17). |
| Red Pipe Wire (`redPipeWire`) | `ItemPipeWire` | `red_pipe_wire` | #60 | [x] | [ ] | [ ] | GameTest `gate_*`. |
| Blue Pipe Wire (`bluePipeWire`) | `ItemPipeWire` | `blue_pipe_wire` | #60 | [x] | [ ] | [ ] | GameTest `gate_*`. |
| Green Pipe Wire (`greenPipeWire`) | `ItemPipeWire` | `green_pipe_wire` | #60 | [x] | [ ] | [ ] | GameTest `gate_*`. |
| Yellow Pipe Wire (`yellowPipeWire`) | `ItemPipeWire` | `yellow_pipe_wire` | #60 | [x] | [ ] | [ ] | GameTest `gate_*`. |
| Gate (`pipeGate.0`) | `ItemGate (meta 0)` | `gate` | #60 | [x] | [ ] | [ ] | Registry name is the 4.2.2 icon name. Crafting in #74. GameTest `gate_*`. |
| Iron AND Gate (`pipeGate.1`) | `ItemGate (meta 1)` | `gate_iron_and` | #60 | [x] | [ ] | [ ] | Registry name is the 4.2.2 icon name. GameTest `gate_*`. |
| Iron OR Gate (`pipeGate.2`) | `ItemGate (meta 2)` | `gate_iron_or` | #60 | [x] | [ ] | [ ] | Registry name is the 4.2.2 icon name. GameTest `gate_*`. |
| Gold AND Gate (`pipeGate.3`) | `ItemGate (meta 3)` | `gate_gold_and` | #60 | [x] | [ ] | [ ] | Registry name is the 4.2.2 icon name. GameTest `gate_*`. |
| Gold OR Gate (`pipeGate.4`) | `ItemGate (meta 4)` | `gate_gold_or` | #60 | [x] | [ ] | [ ] | Registry name is the 4.2.2 icon name. GameTest `gate_*`. |
| Diamond AND Gate (`pipeGate.5`) | `ItemGate (meta 5)` | `gate_diamond_and` | #60 | [x] | [ ] | [ ] | Registry name is the 4.2.2 icon name. GameTest `gate_*`. |
| Diamond OR Gate (`pipeGate.6`) | `ItemGate (meta 6)` | `gate_diamond_or` | #60 | [x] | [ ] | [ ] | Registry name is the 4.2.2 icon name. GameTest `gate_*`. |
| Autarchic Gate (`pipeGateAutarchic.0`) | `ItemGate autarchic (meta 0)` | `autarchic_gate` | #60 | [x] | [ ] | [ ] | Registry name is the 4.2.2 icon name. GameTest `gate_*`. |
| Autarchic Iron AND Gate (`pipeGateAutarchic.1`) | `ItemGate autarchic (meta 1)` | `autarchic_gate_iron_and` | #60 | [x] | [ ] | [ ] | Registry name is the 4.2.2 icon name. GameTest `gate_*`. |
| Autarchic Iron OR Gate (`pipeGateAutarchic.2`) | `ItemGate autarchic (meta 2)` | `autarchic_gate_iron_or` | #60 | [x] | [ ] | [ ] | Registry name is the 4.2.2 icon name. GameTest `gate_*`. |
| Autarchic Gold AND Gate (`pipeGateAutarchic.3`) | `ItemGate autarchic (meta 3)` | `autarchic_gate_gold_and` | #60 | [x] | [ ] | [ ] | Registry name is the 4.2.2 icon name. GameTest `gate_*`. |
| Autarchic Gold OR Gate (`pipeGateAutarchic.4`) | `ItemGate autarchic (meta 4)` | `autarchic_gate_gold_or` | #60 | [x] | [ ] | [ ] | Registry name is the 4.2.2 icon name. GameTest `gate_*`. |
| Autarchic Diamond AND Gate (`pipeGateAutarchic.5`) | `ItemGate autarchic (meta 5)` | `autarchic_gate_diamond_and` | #60 | [x] | [ ] | [ ] | Registry name is the 4.2.2 icon name. GameTest `gate_*`. |
| Autarchic Diamond OR Gate (`pipeGateAutarchic.6`) | `ItemGate autarchic (meta 6)` | `autarchic_gate_diamond_or` | #60 | [x] | [ ] | [ ] | Registry name is the 4.2.2 icon name. GameTest `gate_*`. |
| Facade (`pipeFacade`) | `ItemFacade` | `pipe_facade` | #61 | [ ] | [ ] | [ ] | Captured `BlockState` in a data component (section 6). |
| Pipe Plug (`pipePlug`) | `ItemPlug` | `pipe_plug` | #61 | [ ] | [ ] | [ ] |  |
| Filtered Buffer (`filteredBufferBlock`) | `BlockFilteredBuffer, TileFilteredBuffer` | `filtered_buffer_block` | #62 | [ ] | [ ] | [ ] |  |

### Pipes (transport, `BuildCraftTransport.buildPipe`, superseded by section 8)

| 4.2.2 name | 4.2.2 class | New registry name | Issue | Compiles | Renders | Behaves | Notes |
|---|---|---|---|---|---|---|---|
| Wooden Transport Pipe (`PipeItemsWood`) | `PipeItemsWood (ItemPipe)` | Extractor module (#55) on an item tier pipe; Basic Extractor block (#58) for the engine-driven setup | #53, #55, #58 | [ ] | [ ] | [ ] | Importer maps it to a Basic Extractor (section 13.5). |
| Emerald Transport Pipe (`PipeItemsEmerald`) | `PipeItemsEmerald (ItemPipe)` | Filtered Extractor module on an item tier pipe | #53, #55 | [x] | [ ] | [ ] |  |
| Cobblestone Transport Pipe (`PipeItemsCobblestone`) | `PipeItemsCobblestone (ItemPipe)` | plain item tier pipe, tier 2 (stone) | #53 | [ ] | [ ] | [ ] | Cobblestone is not on the ladder; nearest tier is a judgement call for #53. |
| Stone Transport Pipe (`PipeItemsStone`) | `PipeItemsStone (ItemPipe)` | plain item tier pipe, tier 2 (stone) | #53 | [ ] | [ ] | [ ] |  |
| Quartz Transport Pipe (`PipeItemsQuartz`) | `PipeItemsQuartz (ItemPipe)` | plain item tier pipe, tier 6 (quartz) | #53 | [ ] | [ ] | [ ] |  |
| Iron Transport Pipe (`PipeItemsIron`) | `PipeItemsIron (ItemPipe)` | Director module on an item tier pipe | #53, #55 | [x] | [ ] | [ ] |  |
| Golden Transport Pipe (`PipeItemsGold`) | `PipeItemsGold (ItemPipe)` | plain item tier pipe, tier 5 (gold) | #53 | [ ] | [ ] | [ ] | 4.2.2 speed-up replaced by the tier rate. |
| Diamond Transport Pipe (`PipeItemsDiamond`) | `PipeItemsDiamond (ItemPipe)` | Sorter module on an item tier pipe | #53, #56 | [ ] | [ ] | [ ] |  |
| Obsidian Transport Pipe (`PipeItemsObsidian`) | `PipeItemsObsidian (ItemPipe)` | Pickup module on an item tier pipe | #53, #56 | [ ] | [ ] | [ ] |  |
| Lapis Transport Pipe (`PipeItemsLapis`) | `PipeItemsLapis (ItemPipe)` | Colour Tag module on an item tier pipe | #53, #57 | [ ] | [ ] | [ ] |  |
| Daizuli Transport Pipe (`PipeItemsDaizuli`) | `PipeItemsDaizuli (ItemPipe)` | Colour Sorter module on an item tier pipe | #53, #57 | [ ] | [ ] | [ ] |  |
| Sandstone Transport Pipe (`PipeItemsSandstone`) | `PipeItemsSandstone (ItemPipe)` | Isolator module on an item tier pipe | #53, #57 | [ ] | [ ] | [ ] |  |
| Void Transport Pipe (`PipeItemsVoid`) | `PipeItemsVoid (ItemPipe)` | Void module on an item tier pipe | #53, #56 | [ ] | [ ] | [ ] |  |
| Emzuli Transport Pipe (`PipeItemsEmzuli`) | `PipeItemsEmzuli (ItemPipe)` | Filtered Colour Tag module on an item tier pipe | #53, #57 | [ ] | [ ] | [ ] |  |
| Wooden Waterproof Pipe (`PipeFluidsWood`) | `PipeFluidsWood (ItemPipe)` | Extractor module on a fluid tier pipe | #51, #53, #55 | [ ] | [ ] | [ ] |  |
| Cobblestone Waterproof Pipe (`PipeFluidsCobblestone`) | `PipeFluidsCobblestone (ItemPipe)` | plain fluid tier pipe, tier 2 (stone) | #51, #53 | [ ] | [ ] | [ ] | Judgement call, as for items. |
| Stone Waterproof Pipe (`PipeFluidsStone`) | `PipeFluidsStone (ItemPipe)` | plain fluid tier pipe, tier 2 (stone) | #51, #53 | [ ] | [ ] | [ ] |  |
| Iron Waterproof Pipe (`PipeFluidsIron`) | `PipeFluidsIron (ItemPipe)` | Director module on a fluid tier pipe | #51, #53, #55 | [ ] | [ ] | [ ] |  |
| Golden Waterproof Pipe (`PipeFluidsGold`) | `PipeFluidsGold (ItemPipe)` | plain fluid tier pipe, tier 5 (gold) | #51, #53 | [ ] | [ ] | [ ] |  |
| Emerald Waterproof Pipe (`PipeFluidsEmerald`) | `PipeFluidsEmerald (ItemPipe)` | Filtered Extractor module on a fluid tier pipe | #51, #53, #55 | [ ] | [ ] | [ ] | 4.2.2 opens no GUI for it. |
| Sandstone Waterproof Pipe (`PipeFluidsSandstone`) | `PipeFluidsSandstone (ItemPipe)` | Isolator module on a fluid tier pipe | #51, #53, #57 | [ ] | [ ] | [ ] |  |
| Void Waterproof Pipe (`PipeFluidsVoid`) | `PipeFluidsVoid (ItemPipe)` | Void module on a fluid tier pipe | #51, #53, #56 | [ ] | [ ] | [ ] |  |
| Wooden Kinesis Pipe (`PipePowerWood`) | `PipePowerWood (ItemPipe)` | plain power tier pipe, tier 1 (wood) | #52, #53 | [ ] | [ ] | [ ] | 4.2.2 engine input pipe; any power tier pipe accepts engines (section 7). |
| Cobblestone Kinesis Pipe (`PipePowerCobblestone`) | `PipePowerCobblestone (ItemPipe)` | plain power tier pipe, tier 2 (stone) | #52, #53 | [ ] | [ ] | [ ] | Judgement call, as for items. |
| Stone Kinesis Pipe (`PipePowerStone`) | `PipePowerStone (ItemPipe)` | plain power tier pipe, tier 2 (stone) | #52, #53 | [ ] | [ ] | [ ] |  |
| Quartz Kinesis Pipe (`PipePowerQuartz`) | `PipePowerQuartz (ItemPipe)` | plain power tier pipe, tier 6 (quartz) | #52, #53 | [ ] | [ ] | [ ] |  |
| Iron Kinesis Pipe (`PipePowerIron`) | `PipePowerIron (ItemPipe)` | Director module on a power tier pipe, as the Power Limiter (TRANSPORT Q7), with the Power Limiter action | #52, #53, #55, #60 | [ ] | [ ] | [ ] | #55 keeps the limit and the wrench; #52 applies it, #60 adds the action. |
| Golden Kinesis Pipe (`PipePowerGold`) | `PipePowerGold (ItemPipe)` | plain power tier pipe, tier 5 (gold) | #52, #53 | [ ] | [ ] | [ ] |  |
| Diamond Kinesis Pipe (`PipePowerDiamond`) | `PipePowerDiamond (ItemPipe)` | plain power tier pipe, tier 7 (diamond) | #52, #53 | [ ] | [ ] | [ ] |  |
| Cobblestone Structure Pipe (`PipeStructureCobblestone`) | `PipeStructureCobblestone (ItemPipe)` | `pipe_structure_cobblestone` | #54 | [ ] | [ ] | [ ] |  |

### Triggers (transport, `PipeTriggerProvider` and `GateVanilla`)

| 4.2.2 name | 4.2.2 class | New registry name | Issue | Compiles | Renders | Behaves | Notes |
|---|---|---|---|---|---|---|---|
| Pipe empty | `TriggerPipeContents` | `trigger_pipe_empty` | #60 | [x] | [ ] | [ ] | 4.2.2 tag `buildcraft.pipe.contents.empty`. Reads the flow record (section 8.5). GameTest `gate_*`. |
| Pipe contains items | `TriggerPipeContents` | `trigger_pipe_items` | #60 | [x] | [ ] | [ ] | 4.2.2 tag `buildcraft.pipe.contents.containsItems`. GameTest `gate_*`. |
| Pipe contains fluids | `TriggerPipeContents` | `trigger_pipe_fluids` | #60 | [x] | [ ] | [ ] | 4.2.2 tag `buildcraft.pipe.contents.containsFluids`. GameTest `gate_*`. |
| Pipe contains energy | `TriggerPipeContents` | `trigger_pipe_contains_energy` | #60 | [x] | [ ] | [ ] | 4.2.2 tag `buildcraft.pipe.contents.containsEnergy`. GameTest `gate_*`. |
| Pipe requests energy | `TriggerPipeContents` | `trigger_pipe_requests_energy` | #60 | [x] | [ ] | [ ] | 4.2.2 tag `buildcraft.pipe.contents.requestsEnergy`. GameTest `gate_*`. |
| Pipe too much energy | `TriggerPipeContents` | `trigger_pipe_too_much_energy` | #60 | [x] | [ ] | [ ] | 4.2.2 tag `buildcraft.pipe.contents.tooMuchEnergy`. GameTest `gate_*`. |
| Red pipe signal active | `TriggerPipeSignal` | `trigger_red_signal_active` | #60 | [x] | [ ] | [ ] | 4.2.2 tag `buildcraft.pipe.wire.input.red.active`. GameTest `gate_*`. |
| Red pipe signal inactive | `TriggerPipeSignal` | `trigger_red_signal_inactive` | #60 | [x] | [ ] | [ ] | 4.2.2 tag `buildcraft.pipe.wire.input.red.inactive`. GameTest `gate_*`. |
| Blue pipe signal active | `TriggerPipeSignal` | `trigger_blue_signal_active` | #60 | [x] | [ ] | [ ] | 4.2.2 tag `buildcraft.pipe.wire.input.blue.active`. GameTest `gate_*`. |
| Blue pipe signal inactive | `TriggerPipeSignal` | `trigger_blue_signal_inactive` | #60 | [x] | [ ] | [ ] | 4.2.2 tag `buildcraft.pipe.wire.input.blue.inactive`. GameTest `gate_*`. |
| Green pipe signal active | `TriggerPipeSignal` | `trigger_green_signal_active` | #60 | [x] | [ ] | [ ] | 4.2.2 tag `buildcraft.pipe.wire.input.green.active`. GameTest `gate_*`. |
| Green pipe signal inactive | `TriggerPipeSignal` | `trigger_green_signal_inactive` | #60 | [x] | [ ] | [ ] | 4.2.2 tag `buildcraft.pipe.wire.input.green.inactive`. GameTest `gate_*`. |
| Yellow pipe signal active | `TriggerPipeSignal` | `trigger_yellow_signal_active` | #60 | [x] | [ ] | [ ] | 4.2.2 tag `buildcraft.pipe.wire.input.yellow.active`. GameTest `gate_*`. |
| Yellow pipe signal inactive | `TriggerPipeSignal` | `trigger_yellow_signal_inactive` | #60 | [x] | [ ] | [ ] | 4.2.2 tag `buildcraft.pipe.wire.input.yellow.inactive`. GameTest `gate_*`. |

### Actions (transport, `GateVanilla` and pipe `getActions`)

| 4.2.2 name | 4.2.2 class | New registry name | Issue | Compiles | Renders | Behaves | Notes |
|---|---|---|---|---|---|---|---|
| Red signal output | `ActionSignalOutput` | `action_red_signal` | #60 | [x] | [ ] | [ ] | 4.2.2 tag `buildcraft.pipe.wire.output.red`. GameTest `gate_*`. |
| Blue signal output | `ActionSignalOutput` | `action_blue_signal` | #60 | [x] | [ ] | [ ] | 4.2.2 tag `buildcraft.pipe.wire.output.blue`. GameTest `gate_*`. |
| Green signal output | `ActionSignalOutput` | `action_green_signal` | #60 | [x] | [ ] | [ ] | 4.2.2 tag `buildcraft.pipe.wire.output.green`. GameTest `gate_*`. |
| Yellow signal output | `ActionSignalOutput` | `action_yellow_signal` | #60 | [x] | [ ] | [ ] | 4.2.2 tag `buildcraft.pipe.wire.output.yellow`. GameTest `gate_*`. |
| Energy Pulser | `ActionEnergyPulser` | `action_energy_pulser` | #60 | [x] | [ ] | [ ] | 4.2.2 tag `buildcraft.pulser.constant`. Autarchic gates only. GameTest `gate_*`. |
| Single Energy Pulse | `ActionSingleEnergyPulse` | `action_single_energy_pulse` | #60 | [x] | [ ] | [ ] | 4.2.2 tag `buildcraft.pulser.single`. Autarchic gates only. GameTest `gate_*`. |
| Extraction preset red | `ActionExtractionPreset` | `action_extraction_preset_red` | #57, #60 | [x] | [ ] | [ ] | 4.2.2 tag `buildcraft.extraction.preset.color.red`. Emzuli pipe → Filtered Colour Tag module. GameTest `gate_*`. |
| Extraction preset blue | `ActionExtractionPreset` | `action_extraction_preset_blue` | #57, #60 | [x] | [ ] | [ ] | 4.2.2 tag `buildcraft.extraction.preset.color.blue`. Emzuli pipe → Filtered Colour Tag module. GameTest `gate_*`. |
| Extraction preset green | `ActionExtractionPreset` | `action_extraction_preset_green` | #57, #60 | [x] | [ ] | [ ] | 4.2.2 tag `buildcraft.extraction.preset.color.green`. Emzuli pipe → Filtered Colour Tag module. GameTest `gate_*`. |
| Extraction preset yellow | `ActionExtractionPreset` | `action_extraction_preset_yellow` | #57, #60 | [x] | [ ] | [ ] | 4.2.2 tag `buildcraft.extraction.preset.color.yellow`. Emzuli pipe → Filtered Colour Tag module. GameTest `gate_*`. |
| Pipe colour black | `ActionPipeColor` | `action_pipe_color_black` | #57, #60 | [x] | [ ] | [ ] | 4.2.2 tag `buildcraft.pipe.color.black`. Lapis and Daizuli pipes → Colour Tag and Colour Sorter modules. GameTest `gate_*`. |
| Pipe colour red | `ActionPipeColor` | `action_pipe_color_red` | #57, #60 | [x] | [ ] | [ ] | 4.2.2 tag `buildcraft.pipe.color.red`. GameTest `gate_*`. |
| Pipe colour green | `ActionPipeColor` | `action_pipe_color_green` | #57, #60 | [x] | [ ] | [ ] | 4.2.2 tag `buildcraft.pipe.color.green`. GameTest `gate_*`. |
| Pipe colour brown | `ActionPipeColor` | `action_pipe_color_brown` | #57, #60 | [x] | [ ] | [ ] | 4.2.2 tag `buildcraft.pipe.color.brown`. GameTest `gate_*`. |
| Pipe colour blue | `ActionPipeColor` | `action_pipe_color_blue` | #57, #60 | [x] | [ ] | [ ] | 4.2.2 tag `buildcraft.pipe.color.blue`. GameTest `gate_*`. |
| Pipe colour purple | `ActionPipeColor` | `action_pipe_color_purple` | #57, #60 | [x] | [ ] | [ ] | 4.2.2 tag `buildcraft.pipe.color.purple`. GameTest `gate_*`. |
| Pipe colour cyan | `ActionPipeColor` | `action_pipe_color_cyan` | #57, #60 | [x] | [ ] | [ ] | 4.2.2 tag `buildcraft.pipe.color.cyan`. GameTest `gate_*`. |
| Pipe colour light gray | `ActionPipeColor` | `action_pipe_color_light_gray` | #57, #60 | [x] | [ ] | [ ] | 4.2.2 tag `buildcraft.pipe.color.light.gray`. GameTest `gate_*`. |
| Pipe colour gray | `ActionPipeColor` | `action_pipe_color_gray` | #57, #60 | [x] | [ ] | [ ] | 4.2.2 tag `buildcraft.pipe.color.gray`. GameTest `gate_*`. |
| Pipe colour pink | `ActionPipeColor` | `action_pipe_color_pink` | #57, #60 | [x] | [ ] | [ ] | 4.2.2 tag `buildcraft.pipe.color.pink`. GameTest `gate_*`. |
| Pipe colour lime | `ActionPipeColor` | `action_pipe_color_lime` | #57, #60 | [x] | [ ] | [ ] | 4.2.2 tag `buildcraft.pipe.color.lime`. GameTest `gate_*`. |
| Pipe colour yellow | `ActionPipeColor` | `action_pipe_color_yellow` | #57, #60 | [x] | [ ] | [ ] | 4.2.2 tag `buildcraft.pipe.color.yellow`. GameTest `gate_*`. |
| Pipe colour light blue | `ActionPipeColor` | `action_pipe_color_light_blue` | #57, #60 | [x] | [ ] | [ ] | 4.2.2 tag `buildcraft.pipe.color.light.blue`. GameTest `gate_*`. |
| Pipe colour magenta | `ActionPipeColor` | `action_pipe_color_magenta` | #57, #60 | [x] | [ ] | [ ] | 4.2.2 tag `buildcraft.pipe.color.magenta`. GameTest `gate_*`. |
| Pipe colour orange | `ActionPipeColor` | `action_pipe_color_orange` | #57, #60 | [x] | [ ] | [ ] | 4.2.2 tag `buildcraft.pipe.color.orange`. GameTest `gate_*`. |
| Pipe colour white | `ActionPipeColor` | `action_pipe_color_white` | #57, #60 | [x] | [ ] | [ ] | 4.2.2 tag `buildcraft.pipe.color.white`. GameTest `gate_*`. |
| Pipe direction down | `ActionPipeDirection` | `action_pipe_direction_down` | #55, #60 | [x] | [ ] | [ ] | 4.2.2 tag `buildcraft.pipe.dir.down`. Iron and Daizuli pipes → Director and Colour Sorter modules. GameTest `gate_*`. |
| Pipe direction up | `ActionPipeDirection` | `action_pipe_direction_up` | #55, #60 | [x] | [ ] | [ ] | 4.2.2 tag `buildcraft.pipe.dir.up`. GameTest `gate_*`. |
| Pipe direction north | `ActionPipeDirection` | `action_pipe_direction_north` | #55, #60 | [x] | [ ] | [ ] | 4.2.2 tag `buildcraft.pipe.dir.north`. GameTest `gate_*`. |
| Pipe direction south | `ActionPipeDirection` | `action_pipe_direction_south` | #55, #60 | [x] | [ ] | [ ] | 4.2.2 tag `buildcraft.pipe.dir.south`. GameTest `gate_*`. |
| Pipe direction west | `ActionPipeDirection` | `action_pipe_direction_west` | #55, #60 | [x] | [ ] | [ ] | 4.2.2 tag `buildcraft.pipe.dir.west`. GameTest `gate_*`. |
| Pipe direction east | `ActionPipeDirection` | `action_pipe_direction_east` | #55, #60 | [x] | [ ] | [ ] | 4.2.2 tag `buildcraft.pipe.dir.east`. GameTest `gate_*`. |
| Power limiter 2 | `ActionPowerLimiter` | `action_power_limiter_m2` | #52, #60 | [x] | [ ] | [ ] | 4.2.2 tag `buildcraft.power.limiter.m2`. Iron kinesis pipe; 4.2.2 MJ/t steps, × 10 FE. GameTest `gate_*`. |
| Power limiter 4 | `ActionPowerLimiter` | `action_power_limiter_m4` | #52, #60 | [x] | [ ] | [ ] | 4.2.2 tag `buildcraft.power.limiter.m4`. GameTest `gate_*`. |
| Power limiter 8 | `ActionPowerLimiter` | `action_power_limiter_m8` | #52, #60 | [x] | [ ] | [ ] | 4.2.2 tag `buildcraft.power.limiter.m8`. GameTest `gate_*`. |
| Power limiter 16 | `ActionPowerLimiter` | `action_power_limiter_m16` | #52, #60 | [x] | [ ] | [ ] | 4.2.2 tag `buildcraft.power.limiter.m16`. GameTest `gate_*`. |
| Power limiter 32 | `ActionPowerLimiter` | `action_power_limiter_m32` | #52, #60 | [x] | [ ] | [ ] | 4.2.2 tag `buildcraft.power.limiter.m32`. GameTest `gate_*`. |
| Power limiter 64 | `ActionPowerLimiter` | `action_power_limiter_m64` | #52, #60 | [x] | [ ] | [ ] | 4.2.2 tag `buildcraft.power.limiter.m64`. GameTest `gate_*`. |
| Power limiter 128 | `ActionPowerLimiter` | `action_power_limiter_m128` | #52, #60 | [x] | [ ] | [ ] | 4.2.2 tag `buildcraft.power.limiter.m128`. GameTest `gate_*`. |

### GUIs (transport, `transport.GuiHandler`)

| 4.2.2 name | 4.2.2 class | New registry name | Issue | Compiles | Renders | Behaves | Notes |
|---|---|---|---|---|---|---|---|
| Diamond pipe (`GuiIds.PIPE_DIAMOND` 50) | `GuiDiamondPipe / ContainerDiamondPipe` | Sorter module menu | #56 | [ ] | [ ] | [ ] |  |
| Gate interface (`GuiIds.GATES` 51) | `GuiGateInterface / ContainerGateInterface` | gate menu | #60 | [x] | [ ] | [ ] | GameTest `gate_*`. |
| Emerald pipe (`GuiIds.PIPE_EMERALD_ITEM` 52) | `GuiEmeraldPipe / ContainerEmeraldPipe` | Filtered Extractor module menu | #55 | [x] | [ ] | [ ] | GameTest `module_filtered_extractor_menu`. |
| Emzuli pipe (`GuiIds.PIPE_LOGEMERALD_ITEM` 53) | `GuiEmzuliPipe / ContainerEmzuliPipe` | Filtered Colour Tag module menu | #57 | [ ] | [ ] | [ ] |  |
| Filtered Buffer (`GuiIds.FILTERED_BUFFER` 60) | `GuiFilteredBuffer / ContainerFilteredBuffer` | `filtered_buffer_block` menu | #62 | [ ] | [ ] | [ ] |  |

## builders (`BuildCraftBuilders`)

### Blocks and items (builders)

| 4.2.2 name | 4.2.2 class | New registry name | Issue | Compiles | Renders | Behaves | Notes |
|---|---|---|---|---|---|---|---|
| Template (`templateItem`) | `ItemBptTemplate` | `template_item` | #77 | [ ] | [ ] | [ ] |  |
| Blueprint (`blueprintItem`) | `ItemBptBluePrint` | `blueprint_item` | #77 | [ ] | [ ] | [ ] | Library id + author + name in data components. |
| Land Mark (`markerBlock`) | `BlockMarker, TileMarker` | `marker_block` | #29 | [x] | [ ] | [ ] | Ported in P1 with core (section 15). |
| Path Mark (`pathMarkerBlock`) | `BlockPathMarker, TilePathMarker` | `path_marker_block` | #30 | [x] | [ ] | [ ] | Ported in P1 with core (section 15). |
| Filler (`fillerBlock`) | `BlockFiller, TileFiller` | `filler_block` | #79 | [ ] | [ ] | [ ] |  |
| Builder (`builderBlock`) | `BlockBuilder, TileBuilder` | `builder_block` | #78 | [ ] | [ ] | [ ] |  |
| Architect Table (`architectBlock`) | `BlockArchitect, TileArchitect` | `architect_block` | #77 | [ ] | [ ] | [ ] |  |
| Blueprint Library (`libraryBlock`) | `BlockBlueprintLibrary, TileBlueprintLibrary` | `library_block` | #80 | [ ] | [ ] | [ ] |  |

### Actions (builders, `BuildersActionProvider`: one per filler pattern)

| 4.2.2 name | 4.2.2 class | New registry name | Issue | Compiles | Renders | Behaves | Notes |
|---|---|---|---|---|---|---|---|
| Filler pattern: fill | `ActionFiller + PatternFill` | `filler:buildcraft:fill` | #79 | [ ] | [ ] | [ ] |  |
| Filler pattern: flatten | `ActionFiller + PatternFlatten` | `filler:buildcraft:flatten` | #79 | [ ] | [ ] | [ ] |  |
| Filler pattern: horizon | `ActionFiller + PatternHorizon` | `filler:buildcraft:horizon` | #79 | [ ] | [ ] | [ ] |  |
| Filler pattern: clear | `ActionFiller + PatternClear` | `filler:buildcraft:clear` | #79 | [ ] | [ ] | [ ] |  |
| Filler pattern: box | `ActionFiller + PatternBox` | `filler:buildcraft:box` | #79 | [ ] | [ ] | [ ] |  |
| Filler pattern: pyramid | `ActionFiller + PatternPyramid` | `filler:buildcraft:pyramid` | #79 | [ ] | [ ] | [ ] |  |
| Filler pattern: stairs | `ActionFiller + PatternStairs` | `filler:buildcraft:stairs` | #79 | [ ] | [ ] | [ ] |  |

### GUIs (builders, `builders.GuiHandler`)

| 4.2.2 name | 4.2.2 class | New registry name | Issue | Compiles | Renders | Behaves | Notes |
|---|---|---|---|---|---|---|---|
| Architect Table (`GuiIds.ARCHITECT_TABLE` 10) | `GuiTemplate / ContainerTemplate` | `architect_block` menu | #77 | [ ] | [ ] | [ ] |  |
| Builder (`GuiIds.BUILDER` 11) | `GuiBuilder / ContainerBuilder` | `builder_block` menu | #78 | [ ] | [ ] | [ ] |  |
| Filler (`GuiIds.FILLER` 12) | `GuiFiller / ContainerFiller` | `filler_block` menu | #79 | [ ] | [ ] | [ ] |  |
| Blueprint Library (`GuiIds.BLUEPRINT_LIBRARY` 13) | `GuiBlueprintLibrary / ContainerBlueprintLibrary` | `library_block` menu | #80 | [ ] | [ ] | [ ] |  |

## New in Buildcraft Reforged

Rows for content that replaces a 4.2.2 registration (section 8) or is a deliberate addition (section 3). Registry names here are proposals derived from the 4.2.2 pipe class names; the owning issue confirms them.

### Tier pipes (section 8.1)

| 4.2.2 name | 4.2.2 class | New registry name | Issue | Compiles | Renders | Behaves | Notes |
|---|---|---|---|---|---|---|---|
| Tier 1 items pipe (wood) | — | `pipe_items_wood` | #50, #53 | [x] | [ ] | [ ] |  |
| Tier 1 fluids pipe (wood) | — | `pipe_fluids_wood` | #51, #53 | [x] | [ ] | [ ] |  |
| Tier 1 power pipe (wood) | — | `pipe_power_wood` | #52, #53 | [x] | [ ] | [ ] |  |
| Tier 2 items pipe (stone) | — | `pipe_items_stone` | #50, #53 | [x] | [ ] | [ ] |  |
| Tier 2 fluids pipe (stone) | — | `pipe_fluids_stone` | #51, #53 | [x] | [ ] | [ ] |  |
| Tier 2 power pipe (stone) | — | `pipe_power_stone` | #52, #53 | [x] | [ ] | [ ] |  |
| Tier 3 items pipe (iron) | — | `pipe_items_iron` | #50, #53 | [x] | [ ] | [ ] |  |
| Tier 3 fluids pipe (iron) | — | `pipe_fluids_iron` | #51, #53 | [x] | [ ] | [ ] |  |
| Tier 3 power pipe (iron) | — | `pipe_power_iron` | #52, #53 | [x] | [ ] | [ ] |  |
| Tier 4 items pipe (copper) | — | `pipe_items_copper` | #50, #53 | [x] | [ ] | [ ] | New material and texture (section 8.6). |
| Tier 4 fluids pipe (copper) | — | `pipe_fluids_copper` | #51, #53 | [x] | [ ] | [ ] | New material and texture (section 8.6). |
| Tier 4 power pipe (copper) | — | `pipe_power_copper` | #52, #53 | [x] | [ ] | [ ] | New material and texture (section 8.6). |
| Tier 5 items pipe (gold) | — | `pipe_items_gold` | #50, #53 | [x] | [ ] | [ ] |  |
| Tier 5 fluids pipe (gold) | — | `pipe_fluids_gold` | #51, #53 | [x] | [ ] | [ ] |  |
| Tier 5 power pipe (gold) | — | `pipe_power_gold` | #52, #53 | [x] | [ ] | [ ] |  |
| Tier 6 items pipe (quartz) | — | `pipe_items_quartz` | #50, #53 | [x] | [ ] | [ ] |  |
| Tier 6 fluids pipe (quartz) | — | `pipe_fluids_quartz` | #51, #53 | [x] | [ ] | [ ] |  |
| Tier 6 power pipe (quartz) | — | `pipe_power_quartz` | #52, #53 | [x] | [ ] | [ ] |  |
| Tier 7 items pipe (diamond) | — | `pipe_items_diamond` | #50, #53 | [x] | [ ] | [ ] |  |
| Tier 7 fluids pipe (diamond) | — | `pipe_fluids_diamond` | #51, #53 | [x] | [ ] | [ ] |  |
| Tier 7 power pipe (diamond) | — | `pipe_power_diamond` | #52, #53 | [x] | [ ] | [ ] |  |
| Tier 8 items pipe (emerald) | — | `pipe_items_emerald` | #50, #53 | [x] | [ ] | [ ] |  |
| Tier 8 fluids pipe (emerald) | — | `pipe_fluids_emerald` | #51, #53 | [x] | [ ] | [ ] |  |
| Tier 8 power pipe (emerald) | — | `pipe_power_emerald` | #52, #53 | [x] | [ ] | [ ] |  |
| Tier 9 items pipe (netherite) | — | `pipe_items_netherite` | #50, #53 | [x] | [ ] | [ ] | New material and texture (section 8.6). |
| Tier 9 fluids pipe (netherite) | — | `pipe_fluids_netherite` | #51, #53 | [x] | [ ] | [ ] | New material and texture (section 8.6). |
| Tier 9 power pipe (netherite) | — | `pipe_power_netherite` | #52, #53 | [x] | [ ] | [ ] | New material and texture (section 8.6). |
| Tier 10 items pipe (ender) | — | `pipe_items_ender` | #50, #53 | [x] | [ ] | [ ] | New material and texture (section 8.6). |
| Tier 10 fluids pipe (ender) | — | `pipe_fluids_ender` | #51, #53 | [x] | [ ] | [ ] | New material and texture (section 8.6). |
| Tier 10 power pipe (ender) | — | `pipe_power_ender` | #52, #53 | [x] | [ ] | [ ] | New material and texture (section 8.6). |

### Pipe modules (section 8.2)

| 4.2.2 name | 4.2.2 class | New registry name | Issue | Compiles | Renders | Behaves | Notes |
|---|---|---|---|---|---|---|---|
| Extractor module | — | `module_extractor` | #55, #74 | [x] | [ ] | [ ] | Assembly Table recipe in #74. |
| Filtered Extractor module | — | `module_filtered_extractor` | #55, #74 | [x] | [ ] | [ ] | Assembly Table recipe in #74. |
| Director module | — | `module_director` | #55, #74 | [x] | [ ] | [ ] | Assembly Table recipe in #74. |
| Sorter module | — | `module_sorter` | #56, #74 | [ ] | [ ] | [ ] | Assembly Table recipe in #74. |
| Pickup module | — | `module_pickup` | #56, #74 | [ ] | [ ] | [ ] | Assembly Table recipe in #74. |
| Void module | — | `module_void` | #56, #74 | [ ] | [ ] | [ ] | Assembly Table recipe in #74. |
| Colour Tag module | — | `module_colour_tag` | #57, #74 | [ ] | [ ] | [ ] | Assembly Table recipe in #74. |
| Colour Sorter module | — | `module_colour_sorter` | #57, #74 | [ ] | [ ] | [ ] | Assembly Table recipe in #74. |
| Filtered Colour Tag module | — | `module_filtered_colour_tag` | #57, #74 | [ ] | [ ] | [ ] | Assembly Table recipe in #74. |
| Isolator module | — | `module_isolator` | #57, #74 | [ ] | [ ] | [ ] | Assembly Table recipe in #74. |

### Other additions

| 4.2.2 name | 4.2.2 class | New registry name | Issue | Compiles | Renders | Behaves | Notes |
|---|---|---|---|---|---|---|---|
| Basic Extractor | — | `basic_extractor` | #58 | [ ] | [ ] | [ ] | Section 8.2. |
| Engine Overheating trigger | — | `buildcraft.engine.overheating` | #41 | [ ] | [ ] | [ ] | Section 9. |
| Machine blocked trigger | `TriggerMachine` | `trigger_machine_blocked` | #31 | [x] | [ ] | [ ] | Section 8.3. Tag `buildcraft.work.blocked`; true while `IMachine.isBlocked()`. Its sprite `triggers/trigger_machine_blocked` is new art, not drawn yet. |
| Status panel (shared ledger and the GUI of blocks without one) | — | menu `status` | #26 | [x] | [ ] | [ ] | Section 10. `core/status`, `StatusLedger`; red light `block/status_light`. Sample blocks `sample_gui` and `sample_status` (dev runs). Renders needs a client check. |
