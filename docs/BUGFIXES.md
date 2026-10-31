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
