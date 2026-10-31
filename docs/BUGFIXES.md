# Bug fixes

Every upstream fix considered for Buildcraft Reforged, with the decision taken on it. `docs/PLAN.md` section 3 sets the
policy; this file is the record it asks for.

## Policy in short

A change counts as a bug fix, and may change 4.2.2 behaviour, only when all three hold:

1. The bug exists in 4.2.2: reproduced in a reference 1.6.4 instance, or the upstream issue is against 4.x.
2. The fix changes no balance number, recipe, texture, GUI layout or feature set beyond the plan's deliberate
   additions.
3. It is recorded here with the upstream commit hash, the upstream issue number and the decision.

Triage happens per ported class. When porting `TileX`, run this in a BuildCraft clone and review each hit:

```bash
git log --oneline 4.2.2..origin/7.1.x -- common/buildcraft/<module>/TileX.java
```

Fixes accepted in advance (section 3) still get an entry when they are applied: engines facing each other no longer
exchange power (#1827), engine orientation on placement (#1771), unbreakable block handling (#1789), pump and
refinery residual liquid, tank fill/drain flags, fluid pipes connecting to tank-less fluid handlers.

## Decisions

- **Accepted**: applied; "Applied in" names the pull request or issue.
- **Rejected**: not applied; the entry says which policy point failed.
- **Not applicable**: the bug or its code path does not exist in 4.2.2, or the rewrite removes it (for example
  anything in the old per-item pipe transport, section 8).
- **Pending**: not yet triaged or not yet reproduced. P7 (#81, #82) leaves no entry in this state.

## Template

Copy this block for each candidate, under the module it belongs to. Upstream numbers are written `BuildCraft#N` so
they are not confused with this repository's issues.

```markdown
### BuildCraft#N: short title

| Field | Value |
|---|---|
| Upstream hash | full 40-character hash |
| Upstream issue | BuildCraft#N (link) |
| Class | 4.2.2 class or classes, as under `reference/common/buildcraft/` |
| Symptom | what goes wrong in 4.2.2, in one or two sentences |
| Decision | Accepted / Rejected / Not applicable / Pending, with the reason |
| Applied in | #issue or pull request here, or — |
```

## Candidates

### core

Triage for #31 (`core/triggers`, `api/gates`): `git log 4.2.2..origin/7.1.x` over both folders lists 56 commits. Most
are the 6.x statements refactor, new triggers and actions, robots and style; the two that change existing trigger
behaviour are below. No fix was applied.

#### Inventory triggers and gate sides (no upstream issue)

| Field | Value |
|---|---|
| Upstream hash | [`ff0307702e3585f0ba51be0cde6c7f692f97a835`](https://github.com/BuildCraft/BuildCraft/commit/ff0307702e3585f0ba51be0cde6c7f692f97a835) ("Inventory triggers should not be side specific by default, following #1886", 16 Jun 2014, 6.0 line) and [`57c338b9af675748a77e21589ad223f45a441e52`](https://github.com/BuildCraft/BuildCraft/commit/57c338b9af675748a77e21589ad223f45a441e52) ("various fixes for gates", 5 Jul 2014) |
| Upstream issue | BuildCraft#1886 (referenced, about side-specific gates) |
| Class | Upstream: `core/triggers/BCTrigger.isTriggerActive(IGate, ...)` and `TriggerInventoryLevel`. 4.2.2 counterpart: `transport/Gate.isNearbyTriggerActive`. |
| Symptom | The first commit passes the pipe's side instead of the tile's side to the trigger in 6.x's new gate loop; the second flips `TriggerInventoryLevel` back with `side.getOpposite()` to compensate. Together they leave the tile's side unchanged. 4.2.2 passes `o.getOpposite()`, the tile's side, already. |
| Decision | Not applicable: the gate loop they change is from the 6.x refactor; in 4.2.2 the side is already correct. #60 keeps 4.2.2's `o.getOpposite()` when it ports `Gate`. |
| Applied in | — |

### energy

Triage for #42 (`energy/BlockBuildcraftFluid`, `ItemBucketBuildcraft`, `BucketHandler`, `render/EntityDropParticleFX`):
`git log` on the 6.4.x line lists the 1.7 port, header and style commits plus the four below. No fix was applied.
`1efa254037` ("Make swimming in oil a lot harder", 6.x) adds a `dense` flag that slows entities in oil: a gameplay
change, rejected under policy point 2. `ce393ec0ef` (BuildCraft#1439) and `05693e1b0b` repair crashes and a lost
bucket mapping introduced by the 1.7 port; the 4.2.2 code does not have them (not applicable).

#### BuildCraft#2649: oil in the Nether explodes at the wrong moment

| Field | Value |
|---|---|
| Upstream hash | [`fc3ebfbb8dbf236f16850bb47f08d7338caf8dd8`](https://github.com/BuildCraft/BuildCraft/commit/fc3ebfbb8dbf236f16850bb47f08d7338caf8dd8) ("fix #2649", 22 Apr 2015, 6.4 line) |
| Upstream issue | [BuildCraft#2649](https://github.com/BuildCraft/BuildCraft/issues/2649) |
| Class | `energy/BlockBuildcraftFluid` (`onNeighborBlockChange`) |
| Symptom | The Nether explosion ran before the fluid block was removed, so the explosion's neighbour updates reached the same block again. Upstream swapped the two calls. |
| Decision | Not applicable: PLAN.md section 19.2 replaces the explosion with fire, so the block is replaced by fire in one call and there is no explosion to order. |
| Applied in | — |

Triage for #36 (`energy/TileEngine`, `BlockEngine`, `ItemEngine`, `TileEngineWithInventory`), decided 31 Oct 2026:
`git log` since 4.2.2 on the 6.0 line lists the 1.7 port, header, style, network and MJ API (`IBatteryObject`)
commits, the creative engine and 5.x's per-part collision boxes and ray traces (`ca1eb0b4ee`, `5d762c6f6d`,
`84b1d58320`, which fix boxes 4.2.2 does not have: not applicable). `42d0e641b8` (BuildCraft#1584) replaces casts to
`TileEngine` with `instanceof` checks; the port already uses `instanceof` everywhere (not applicable). The two fixes
PLAN.md section 3 accepted in advance are below.

#### BuildCraft#1827: engines facing each other pass power back and forth

| Field | Value |
|---|---|
| Upstream hash | [`527cc7d6d465149e78b65e47a0170359cd45b7fd`](https://github.com/BuildCraft/BuildCraft/commit/527cc7d6d465149e78b65e47a0170359cd45b7fd) ("do not send power where power is received, close #1827", 18 May 2014, 6.0 line) |
| Upstream issue | [BuildCraft#1827](https://github.com/BuildCraft/BuildCraft/issues/1827) |
| Class | Upstream: `transport/PipeTransportPower`. Here: `energy/TileEngine` (`isPoweredTile`) |
| Symptom | Power goes back where it came from. Upstream fixed it in the power pipe (no power sent to a side that delivers power); in 4.2.2 the same loop exists between two engines facing each other, each a receptor for the other's pulses. |
| Decision | Accepted (PLAN.md section 3): the upstream commit's pipe code is replaced by the rewrite (section 8), so the rule is applied to engines instead. `isPoweredTile` rejects a neighbour that is an `IPowerEmitter` emitting towards this engine, as `PowerEnergyHandler` already refuses Forge Energy on an emitting side. Engines in a chain still pass power on. GameTest `engine_pushes_into_fe_and_engines`. |
| Applied in | #36 |

#### BuildCraft#1771: engines do not keep their orientation in blueprints

| Field | Value |
|---|---|
| Upstream hash | [`30e4d493b3ea6e551d60e18d5e66a7393cff20c4`](https://github.com/BuildCraft/BuildCraft/commit/30e4d493b3ea6e551d60e18d5e66a7393cff20c4) ("fixed engines orientation, for #1771", 10 May 2014, 6.0 line) |
| Upstream issue | [BuildCraft#1771](https://github.com/BuildCraft/BuildCraft/issues/1771) |
| Class | `energy/BptBlockEngine` (`rotateLeft`; `SchematicEngine` upstream) |
| Symptom | Rotating a blueprint turned engines around the DOWN axis, the wrong way, so built engines faced the wrong side. |
| Decision | Accepted (PLAN.md section 3): the orientation is `BlockEngine.FACING`, and `BlockEngine.rotate`/`mirror` turn it with the structure as vanilla blocks do, which is what the P6 schematic layer uses (`BlockState.rotate`). `BptBlockEngine` is not ported. |
| Applied in | #36 |

Triage for #37 and #38 (`energy/TileEngineWood`, `TileEngineStone`, `gui/ContainerEngine`, `gui/GuiEngine`,
`gui/GuiStoneEngine`), decided 2026-10-31: `git log 4.2.2..origin/7.1.x` lists the 1.7 port, headers, style, the
MJ → RF unit work (`b906f7b7a`, `c961de9daf`, `b5c212970`, which fixes a redstone engine made 10 × weaker by that
conversion, and `d691d72bc`, which fixes the Stirling `TARGET_OUTPUT` that conversion broke), the gate API refactor,
the overheat mechanic (`20031e79e`, #40's business) and creative engines (`3f6fafb4a`). None fixes a bug that exists
in 4.2.2: not applicable. The one deliberate change, power pipes connecting to the redstone engine, is a plan
section 7 decision recorded in `docs/PORTING-NOTES.md`.

Triage for #44 and #40 (`energy/TileEngineIron`, `gui/GuiCombustionEngine`), decided 2026-10-31: the upstream history
of `TileEngineIron.java` on the 6.4.x and 7.1.x lines lists the 1.7 port, headers, style, the MJ → RF and fuel API
rewrites, the gate API refactor, the overheat mechanic (`20031e79e9`, `ba20bea0ec`: replaced by plan section 9) and
these. `0366f3fae0` (engines refuse fluids on their output side) changes behaviour without a bug: rejected under
policy point 2. `00a72e8f20` (BuildCraft#2502) undoes a `getTankInfo` change 4.2.2 does not have, and `b9f16462d8`,
`b977138ec4` add null checks the port does not need (`FluidStack.EMPTY`): not applicable.

#### Combustion engine keeps the old fuel's values after a refill

| Field | Value |
|---|---|
| Upstream hash | [`bf8403fd4e57313d60bf44d1f60a45aa0415894a`](https://github.com/BuildCraft/BuildCraft/commit/bf8403fd4e57313d60bf44d1f60a45aa0415894a) ("fix combustion engine never resetting its fuel value", 2 Apr 2015, 6.4 line) |
| Upstream issue | none |
| Class | `energy/TileEngineIron` (`burn`, `fill`) |
| Symptom | `currentFuel` is reset only when the last millibucket's burn time ends; a different fuel added in those few ticks burns at the old fuel's rates until the tank empties. |
| Decision | Pending: no upstream issue, and not reproduced in a 1.6.4 instance (policy point 1). The port keeps 4.2.2's behaviour. |
| Applied in | — |

#### Coolant used for free when the amount rounds to zero

| Field | Value |
|---|---|
| Upstream hash | [`c93ce3a32613c3bfc0e82331633b6408e21f3da2`](https://github.com/BuildCraft/BuildCraft/commit/c93ce3a32613c3bfc0e82331633b6408e21f3da2) ("Stop the cooling when it round the use to zero", 7 Mar 2015, 6.4 line) |
| Upstream issue | none |
| Class | `energy/TileEngineIron` (`coolEngine`) |
| Symptom | When less than one millibucket would do, `Math.round` drains 0 mB but the heat still drops. With water this is under 0.0023 °C a tick. |
| Decision | Pending: no upstream issue, and not reproduced in a 1.6.4 instance (policy point 1). The port keeps 4.2.2's behaviour. |
| Applied in | — |

### factory

#### BuildCraft#2353: liquid dupe with the tank

| Field | Value |
|---|---|
| Upstream hash | [`2b0885ccdaab66ca7cd7cc78550a5b401e0053e4`](https://github.com/BuildCraft/BuildCraft/commit/2b0885ccdaab66ca7cd7cc78550a5b401e0053e4) ("fix lens stacking and #2353", 3 Jan 2015, 6.3 line) |
| Upstream issue | [BuildCraft#2353](https://github.com/BuildCraft/BuildCraft/issues/2353) |
| Class | `factory/BlockTank` (`onBlockActivated`) |
| Symptom | Upstream: with a full inventory, using a stack of fluid containers on a tank fills the tank and then returns `false`, so the container is not consumed or dumps its contents: liquid is duplicated. The fix drops the container on the ground instead of returning `false`. In 4.2.2 the fill branch never returns `false` after filling, so the dupe path as reported does not exist. Reading the 4.2.2 code shows a neighbouring bug instead: emptying a stack of more than one filled container into the tank removes one container and never gives the empty one back (`Utils.consumeItem` splits the stack and drops the container item). With a full inventory, filling a container from a tank returns `false` before draining, which is safe. |
| Decision | Not applicable (decided 31 Oct 2026, #64): not reproduced in 1.6.4, as no reference instance was available. The container exchange is not ported: `BlockTank.useItemOn` goes through `FluidUtils.handleRightClick` and NeoForge's `ItemAccess.forPlayerInteraction`, which puts the emptied container in the inventory or drops it, so neither the upstream dupe nor the lost container can happen. GameTest `tank_buckets` checks a stack of two buckets. |
| Applied in | — |

#### Tank canFill/canDrain (no upstream issue)

| Field | Value |
|---|---|
| Upstream hash | [`aa24a99d956606b5efdb1da51548fd5d35f1e92f`](https://github.com/BuildCraft/BuildCraft/commit/aa24a99d956606b5efdb1da51548fd5d35f1e92f) ("Fix canFill/canDrain in tanks", 21 May 2014, 6.0 line) |
| Upstream issue | none |
| Class | `factory/TileTank` (`canFill`, `canDrain`) |
| Symptom | 4.2.2 `canFill` answers true for any fluid, though `fill` refuses a fluid other than the stack's; `canDrain` answers false, though `drain` works, so pipes and machines that ask first never drain a tank. |
| Decision | Accepted: the "tank fill/drain flags" fix accepted in advance (plan section 3). `canFill` is true when the bottom tank is empty or holds the fluid, `canDrain` when it holds the fluid. The fluid capability's `isValid` answers `canFill`. |
| Applied in | #64 |

#### Tank drain(FluidStack) checks the wrong tank (no upstream issue)

| Field | Value |
|---|---|
| Upstream hash | [`08edcf1759c884cb95b66c53427bbd4b3f3d9751`](https://github.com/BuildCraft/BuildCraft/commit/08edcf1759c884cb95b66c53427bbd4b3f3d9751) ("Fix drain method causing dupe glitches...", 12 May 2014, 6.0 line) |
| Upstream issue | none |
| Class | `factory/TileTank` (`drain(ForgeDirection, FluidStack, boolean)`) |
| Symptom | 4.2.2 compares the requested fluid with the clicked tank's own fluid but drains the bottom tank. Asking an empty upper tank returns nothing while the bottom holds fluid, and asking a tank for its fluid drains whatever the bottom holds; upstream reports dupes when the two drain methods are mixed. |
| Decision | Accepted: part of the "tank fill/drain flags" fix accepted in advance. The fluid is compared with the bottom tank, in `drain(Direction, FluidStack, boolean)` and in the capability's `extract`. |
| Applied in | #64 |

#### Tank light with an empty fluid stack: BuildCraft#3174

| Field | Value |
|---|---|
| Upstream hash | [`09917470e19163c90a36f12f06f1938e0c3b4f3b`](https://github.com/BuildCraft/BuildCraft/commit/09917470e19163c90a36f12f06f1938e0c3b4f3b) ("fix #3174", 12 Jan 2016, 7.1 line) |
| Upstream issue | [BuildCraft#3174](https://github.com/BuildCraft/BuildCraft/issues/3174) |
| Class | `factory/TileTank` (`getFluidLightLevel`) |
| Symptom | A tank holding a fluid stack of amount 0 still lit up with the fluid's luminosity. |
| Decision | Not applicable: `Tank` holds `FluidStack.EMPTY` for no fluid, and an empty `FluidStack` is any stack of amount 0, so `getFluidLightLevel` already returns 0. |
| Applied in | — |

#### BuildCraft#2340: quarry loses chest contents

| Field | Value |
|---|---|
| Upstream hash | [`970c22bdb53e97f2228221e02b8231ea092842bf`](https://github.com/BuildCraft/BuildCraft/commit/970c22bdb53e97f2228221e02b8231ea092842bf) ("fix #2344, #2340", 28 Dec 2014, 6.3 line) |
| Upstream issue | [BuildCraft#2340](https://github.com/BuildCraft/BuildCraft/issues/2340) |
| Class | `factory/TileQuarry` (`positionReached`), shared with `factory/TileMiningWell`; upstream fixed it in `BlockMiner` with `Utils.preDestroyBlock` |
| Symptom | Upstream: when the quarry digs out a chest (a dungeon chest, for example), the chest's contents vanish. In 4.2.2 `TileQuarry` collects `getBlockDropped` and then calls `setBlockToAir`; whether the chest's own `breakBlock` spills the contents and the quarry's "collect lost items" pass picks them up is not clear from reading. |
| Decision | Pending: reproduce in 1.6.4 during #67 (quarry) and #66 (mining well). With the fake player of section 6, breaking goes through `BlockEvent.BreakEvent` and the block's normal removal, which may settle it on its own; the inventory contents must end up in the quarry output either way. |
| Applied in | — |

The mining well (#66, read on 31 Oct 2026, not reproduced in 1.6.4) keeps 4.2.2's behaviour: 1.6.4's `setBlock` ran the
chest's `breakBlock`, which spilled the contents at the dug position, and 26.x's `setBlock` runs the chest's
`preRemoveSideEffects`, which does the same. The contents land in the hole and do not go to the well's output. The
decision for the quarry stays with #67.

#### Mining well does not record its owner (no upstream issue)

| Field | Value |
|---|---|
| Upstream hash | [`78091876ade1bb55b721e081bf2d05f2c6d676f0`](https://github.com/BuildCraft/BuildCraft/commit/78091876ade1bb55b721e081bf2d05f2c6d676f0) ("fix mining well not calling super when placed down", 16 Sep 2014, 6.1 line) |
| Upstream issue | none |
| Class | `factory/BlockMiningWell` (`onBlockPlacedBy`) |
| Symptom | `onBlockPlacedBy` sets the orientation without calling `BlockBuildCraft.onBlockPlacedBy`, so the tile's `owner` is never set. Here the owner is the profile the well digs as (#27), so the well would always act as the ownerless `[BuildCraft]` player. |
| Decision | Accepted: `setPlacedBy` calls super first. GameTest `mining_well_digs` checks the profile. |
| Applied in | #66 |

#### BuildCraft#2162: mining well breaks blocks without a break event

| Field | Value |
|---|---|
| Upstream hash | [`a3064c12cd559c2a677ae72b19cf254d51f35473`](https://github.com/BuildCraft/BuildCraft/commit/a3064c12cd559c2a677ae72b19cf254d51f35473) ("quarries and mining wells emit blockbreakevents now (for #2162)", 7 Nov 2014, 6.2 line) |
| Upstream issue | [BuildCraft#2162](https://github.com/BuildCraft/BuildCraft/issues/2162) |
| Class | `factory/TileMiningWell` (`doWork`) |
| Symptom | The well replaced blocks with pipe without asking protection mods. |
| Decision | Accepted through the plan's fake-player decision (section 6, #27): `doWork` asks `BuildCraftPlayer.canBreak` with the owner's player. Upstream then set `isDigging = false` for good; here a refusal sets a separate blocked flag (status panel, Machine trigger) and the well tries again on its next work cycle, so it carries on when the claim lifts. The energy for that cycle is used, as upstream and 4.2.2 used it before every check. |
| Applied in | #66 |

#### BuildCraft#2140 and BuildCraft#2998: mining well pipe connections and the 6.x miner (not applicable)

`4ea4bb9eae061db2a7e2c5c410807e7c69479375` (BuildCraft#2140, 2 Nov 2014) changes the 6.x `overridePipeConnection` of
the mining well from DISCONNECT to DEFAULT for non-item pipes, and `15c008b1d4e4933b043e0ca90c063e57a9b79750`
(BuildCraft#2998, 30 Sep 2015) fixes the 6.x `BlockMiner` state machine. Neither code path exists in 4.2.2: there the
item pipe connects through `IMachine.manageSolids` (ported in `BlockPipeItems.isEndpoint`) and digging is one call.
The rest of `git log 4.2.2..origin/7.1.x` over the three classes is the 6.x/7.x rewrite (RF battery, dynamic break
energy, the 6.2 "unnerf", tiles API), the 1.7 port, achievements and style: no other fix.

Triage for #65 (`factory/TilePump`, `BlockPump`, `TileFloodGate`, `BlockFloodGate`, `PumpDimensionList`), decided
2026-10-31: the GitHub commit list for each file on the 7.1.x line since 4.2.2 holds the 1.7 port, headers, style,
the MJ/RF API changes, the 6.x Tiles API, robots, emerald pipe filters and the 7.x fluid pipe rewrite, plus the
entries below. `b3dac2386694fd9722f75223c8f07129d3526000` ("fixed generic fluid pumping", 1.7 line) makes
`BlockUtil.drainBlock`/`getFluid` work for any registered fluid block instead of only water, lava and Forge fluid
blocks; the port's `BlockUtil` already handles every `LiquidBlock` (not applicable). The residual liquid fix accepted
in advance for pumps (PLAN.md section 3, BuildCraft#1790) is about blueprints keeping the pump's fluid; 4.2.2 has no
pump schematic and blueprints are P6 (not applicable here; #78 should not copy tank contents into schematics).

#### BuildCraft#2650: pump tube entities pile up

| Field | Value |
|---|---|
| Upstream hash | [`a11a25456c65848f4780728b951ae377aa014b9d`](https://github.com/BuildCraft/BuildCraft/commit/a11a25456c65848f4780728b951ae377aa014b9d) ("fix pump crash, fix pump entity spam", 13 Apr 2015, 7.0 line) and [`f3844b74b29ae314e2d3bb204144344a0efb5c0d`](https://github.com/BuildCraft/BuildCraft/commit/f3844b74b29ae314e2d3bb204144344a0efb5c0d) ("backport pump entity spam fix, fix #2650", 22 Apr 2015, 6.4 line) |
| Upstream issue | [BuildCraft#2650](https://github.com/BuildCraft/BuildCraft/issues/2650) (referenced by the commit; the issue itself is about a filler) |
| Class | `factory/TilePump` (`onChunkUnload`) |
| Symptom | The tube is an entity spawned by the tile. When the chunk unloads, the tile goes but the entity is saved with the chunk; each reload spawns another tube, so tube entities pile up. Upstream removes the entity in `onChunkUnload`. |
| Decision | Not applicable: the tube is no longer an entity (`EntityBlock` is render data held by `TilePump` and drawn by `RenderPump`), so nothing is saved or spawned. |
| Applied in | — |

#### BuildCraft#2165: flood gate searches below the world

| Field | Value |
|---|---|
| Upstream hash | [`2507218e00d5296041b712aaeeaf40c92f7981ba`](https://github.com/BuildCraft/BuildCraft/commit/2507218e00d5296041b712aaeeaf40c92f7981ba) ("fix #2165", 6 Nov 2014) |
| Upstream issue | [BuildCraft#2165](https://github.com/BuildCraft/BuildCraft/issues/2165) |
| Class | `factory/TileFloodGate` (`queueForFilling`) |
| Symptom | A flood gate over a world without bedrock queues positions below y = 0 as empty (air) and the server crashes placing fluid there. Upstream skips y below 0 or above 255. In 4.2.2 the out-of-world positions read as air and are queued the same way. |
| Decision | Accepted (2026-10-31, #65): a crash, no balance change. `queueForFilling` skips positions where `level.isOutsideBuildHeight(y)`, which follows the dimension's own height instead of 0..255. |
| Applied in | #65 |

#### BuildCraft#1563: flood gate and finite liquids

| Field | Value |
|---|---|
| Upstream hash | [`4ea6f8959f949deb740c20106eb50ed5e68e0136`](https://github.com/BuildCraft/BuildCraft/commit/4ea6f8959f949deb740c20106eb50ed5e68e0136) ("fixed flood gate and finite liquids, close #1563", 2 Apr 2014, 6.0 line) |
| Upstream issue | [BuildCraft#1563](https://github.com/BuildCraft/BuildCraft/issues/1563) |
| Class | `factory/TileFloodGate` (`placeFluid`) |
| Symptom | A Forge `BlockFluidBase` fluid (finite liquids from other mods) was placed with metadata 0, which for those blocks is a nearly empty block; upstream places it at its full-block metadata. |
| Decision | Not applicable: Forge's fluid block classes and metadata levels are gone. The gate places the fluid's source state (`FluidUtils.getFluidBlock`), which is always a full block. |
| Applied in | — |

### silicon

Triage for #72 (`silicon/TileAssemblyTable`, `BlockLaserTable`, `gui/GuiAssemblyTable`, `gui/ContainerAssemblyTable`,
`api/recipes/AssemblyRecipe`): `git log 4.2.2..origin/7.1.x` lists 101 commits, almost all the 6.x/7.x rewrite of the
tables (`TileLaserTableBase`, `AssemblyRecipeManager`, recipe ids, robots, models) and style. Of the rest:
`e1bb0732286b2441a031812777bdc3814b1d3691` and `bb3bf76bb4a20214a39ac89a67494b5e38089d5a` add null checks for
recipes or saved stacks that cannot be found; the port's lookups skip what they cannot find already (not applicable).
`8d3cc1773` ("fixed assembly table networking/GUI", 6.3) repairs the 6.x command packets, which the port does not
have (not applicable). The two below were applied.

#### Assembly table drops the whole output after a partial insert (no upstream issue)

| Field | Value |
|---|---|
| Upstream hash | [`7acb8b45925613a1c5040c76ebf316bf9ed18782`](https://github.com/BuildCraft/BuildCraft/commit/7acb8b45925613a1c5040c76ebf316bf9ed18782) ("made progress with robot recipes and boards, #1732", 27 May 2014, 6.0 line) |
| Upstream issue | none |
| Class | `silicon/TileAssemblyTable` (`updateEntity`) |
| Symptom | When an inventory or pipe next to the table takes part of the output, 4.2.2 still drops `currentRecipe.output.copy()`, the whole output, on top: items are duplicated. Upstream drops `remaining`. |
| Decision | Accepted (31 Oct 2026, #72): a dupe, no balance change. The drop is `remaining`. |
| Applied in | #72 |

#### Assembly table takes too few items for an Ore Dictionary input (no upstream issue)

| Field | Value |
|---|---|
| Upstream hash | [`cc0298ce5bc255ee2209c8995c81fd3ca4312aca`](https://github.com/BuildCraft/BuildCraft/commit/cc0298ce5bc255ee2209c8995c81fd3ca4312aca) ("Clean up Recipe API", 1 Dec 2013, 5.0 line), which replaced the loop |
| Upstream issue | none |
| Class | `silicon/TileAssemblyTable` (`useItems`) |
| Symptom | For an Ore Dictionary input of count `n`, 4.2.2 loops `for (num = 0; num < required; num++)` while counting `required` down inside the loop, so it removes about half of `n` from each ore stack. No 4.2.2 recipe needs more than one of an ore input (the pipe wires take one dye), so it never showed in the base mod; recipes from other mods or data packs would craft for less. |
| Decision | Accepted (31 Oct 2026, #72): the recipe's own count is the balance number, and the fix makes the table honour it. A tag input removes exactly its count of items in the tag. |
| Applied in | #72 |

### builders

#### BuildCraft#2331: filler corrupts the world on reload

| Field | Value |
|---|---|
| Upstream hash | [`30b08352cb1d6d781369586d2e6556f73771dd9d`](https://github.com/BuildCraft/BuildCraft/commit/30b08352cb1d6d781369586d2e6556f73771dd9d) ("fix #2331", 28 Dec 2014, 6.3 line) |
| Upstream issue | [BuildCraft#2331](https://github.com/BuildCraft/BuildCraft/issues/2331) |
| Class | Upstream: `core/builders/patterns/FillerPattern` (`getBlueprint`) and `factory/TileQuarry` (`initializeBlueprintBuilder`). 4.2.2 counterparts: `builders/filler/pattern/FillerPattern`, `builders/TileFiller`. |
| Symptom | Upstream: fillers set to some patterns, with no area, crash the world as a ticking block entity after a reload. The fix wraps blueprint generation in a try/catch and skips a null blueprint. Both changed methods belong to the 6.x blueprint-based filler and quarry, which 4.2.2 does not have. |
| Decision | Pending: the upstream code path is absent in 4.2.2, so this is likely Not applicable. Confirm during #79 by placing fillers in every pattern without markers, saving and reloading in 1.6.4. |
| Applied in | — |
