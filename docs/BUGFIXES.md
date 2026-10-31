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

### factory

#### BuildCraft#2353: liquid dupe with the tank

| Field | Value |
|---|---|
| Upstream hash | [`2b0885ccdaab66ca7cd7cc78550a5b401e0053e4`](https://github.com/BuildCraft/BuildCraft/commit/2b0885ccdaab66ca7cd7cc78550a5b401e0053e4) ("fix lens stacking and #2353", 3 Jan 2015, 6.3 line) |
| Upstream issue | [BuildCraft#2353](https://github.com/BuildCraft/BuildCraft/issues/2353) |
| Class | `factory/BlockTank` (`onBlockActivated`) |
| Symptom | Upstream: with a full inventory, using a stack of fluid containers on a tank fills the tank and then returns `false`, so the container is not consumed or dumps its contents: liquid is duplicated. The fix drops the container on the ground instead of returning `false`. In 4.2.2 the fill branch never returns `false` after filling, so the dupe path as reported does not exist. Reading the 4.2.2 code shows a neighbouring bug instead: emptying a stack of more than one filled container into the tank removes one container and never gives the empty one back (`Utils.consumeItem` splits the stack and drops the container item). With a full inventory, filling a container from a tank returns `false` before draining, which is safe. |
| Decision | Pending: reproduce the lost empty container in 1.6.4 during #64. If it reproduces, apply the upstream remedy (give the empty container back, drop it if the inventory is full). |
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
