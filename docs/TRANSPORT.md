# Buildcraft Reforged — transport specification

The implementation spec for the rate-based pipes decided in `docs/PLAN.md` section 8. Every P3 issue (#48–#63) is
implemented and tested against this file. Where it says "4.2.2" it cites the class and method under
`reference/common/buildcraft/transport/` (paths below are relative to that folder unless they start with
`reference/`). Read those classes for behaviour only; the transport code is new (PLAN section 6, "except transport").

Decided items in PLAN section 8 are not reopened here. Where section 8 is silent, ambiguous, or disagrees with 4.2.2,
this file states a **provisional rule** so the code can be written, marks it **(Qn)**, and lists the question in
section 15. A provisional rule is the default until the question is answered; changing it should touch only the
places that cite it.

---

## 1. Terms

| Term | Meaning |
|---|---|
| Kind | `ITEM`, `FLUID` or `POWER`. Structure pipes have kind `STRUCTURE` and carry nothing. |
| Tier | 1–10, from the pipe's material (section 2). Same tier for every kind. |
| Pipe, segment | One pipe block. "Per-segment" in PLAN 8.3/8.4 is read as "per pipe block" (Q3). |
| Network | A connected component of pipes of one kind (section 3). |
| Endpoint | A non-pipe block face a pipe is connected to: an item handler, fluid handler or energy storage. |
| Source | Something that starts a transfer: an extracting module, a Basic Extractor pulse, a Pickup module, or a block pushing into a pipe through its capability. |
| Walk | The routing search from a source to one destination (section 4). |
| Path | The ordered list of pipes a walk passed through, source pipe first. |
| Budget | The per-pipe throughput allowance that refills every tick (section 5). |
| Side order | `DOWN, UP, NORTH, SOUTH, WEST, EAST`, i.e. `Direction.get3DDataValue()` 0–5, the same order as 4.2.2 `ForgeDirection` 0–5. Every "in side order" below means this order. |

---

## 2. Tiers and configuration

### 2.1 Tier table (PLAN 8.1, decided)

| Tier | Material id | Recipe material | Items/s | Fluid mB/t | Power FE/t |
|---|---|---|---|---|---|
| 1 | `wood` | planks (tag, 4.2.2 `plankWood`) | 1 | 20 | 80 |
| 2 | `stone` | stone (4.2.2 `stone`) | 2 | 40 | 160 |
| 3 | `iron` | iron ingot | 4 | 80 | 320 |
| 4 | `copper` | copper ingot | 8 | 160 | 640 |
| 5 | `gold` | gold ingot | 16 | 320 | 1 280 |
| 6 | `quartz` | block of quartz (4.2.2 `blockNetherQuartz`) | 32 | 640 | 2 560 |
| 7 | `diamond` | diamond | 64 | 1 280 | 5 120 |
| 8 | `emerald` | emerald | 128 | 2 560 | 10 240 |
| 9 | `netherite` | netherite ingot | 256 | 5 120 | 20 480 |
| 10 | `ender` | ender pearl | 512 | 10 240 | 40 960 |

The 30 tier pipes are 10 materials × {item, fluid, power}. The Structure Pipe (cobblestone) and the Basic Extractor
sit outside the ladder.

Recipes (#53): the item pipe is `material, glass, material` in a row, 8 pipes, as 4.2.2
`BuildCraftTransport.buildPipe` with three ingredients. 4.2.2 made fluid pipes shapeless from one item pipe plus Pipe
Sealant, and kinesis pipes from one item pipe plus redstone (`buildPipe` with two ingredients). PLAN 8.1 says "one
recipe pattern per kind (glass between two material items, as 4.2.2)"; which recipe the fluid and power pipes use is
Q17. Provisional rule: the 4.2.2 shapeless recipes, with the item pipe of the same material.

### 2.2 Config keys

Common (server-authoritative) config, laid out as #19 decides. Values are read when a network is built and when a
budget is refilled, so a config reload takes effect on the next tick without a restart.

| Key | Type, default | Origin and use |
|---|---|---|
| `transport.tiers.<material>.itemsPerSecond` | int ≥ 1, table above | PLAN 8.1. Item budget rate. |
| `transport.tiers.<material>.fluidPerTick` | int ≥ 1, table above | PLAN 8.1. Fluid budget rate, mB/t. |
| `transport.tiers.<material>.powerPerTick` | int ≥ 1, table above | PLAN 8.1. Power budget rate, FE/t. |
| `transport.burstSeconds` | double 0.05–10, **1.0** | New. Budget cap in seconds of rate (section 5.2, Q4). |
| `transport.fluidLockTicks` | int 1–1200, **20** | New. How long a pipe stays locked to the last fluid (section 7.3, Q5). |
| `transport.maxRouteVisits` | int 64–65536, **4096** | New. Upper bound on pipes visited by one walk (section 4.4, Q20). |
| `transport.extractor.itemExclusions` | list of item ids, empty | 4.2.2 `woodenPipe.item.exclusion` (`BuildCraftTransport.preInit`, `ExtractionHandler`). Blocks whose items extractors never pull. |
| `transport.extractor.fluidExclusions` | list of block ids, empty | 4.2.2 `woodenPipe.liquid.exclusion`. Same for fluids. |
| `transport.pipeDurability` | double, **0.25** | 4.2.2 `pipes.durability` (`DefaultProps.PIPES_DURABILITY`). Pipe block hardness. |

Dropped 4.2.2 keys: `pipes.groupItemsTrigger` (no items live in pipes any more).

`<material>` is the material id column above. A missing or invalid entry falls back to the default for that tier and
logs one warning. Unit tests (#53) parse a config with all ten tiers, one with a missing tier, and one with a zero
value.

---

## 3. Pipes, connections and the network graph

### 3.1 Pipe block and block entity (#48)

One block class per kind (`ITEM`, `FLUID`, `POWER`) instantiated once per tier, plus the Structure Pipe block. All
implement `SimpleWaterloggedBlock`; water has no effect on connections or routing. The block state carries the
`waterlogged` property only; connections live in the block entity and are rendered through the dynamic model.

The block entity holds (saved fields marked S):

| Field | Purpose |
|---|---|
| `byte connectionMask` | Bit `d` set when connected on side `d` (side order). Recomputed on neighbour change (3.2). Synced to the client in the update tag. |
| `byte pluggedMask` (S) | Plugs (#61). A plugged side never connects. |
| `PipeModule module` (S) | Null for a plain pipe. Module state (filters, facing, colour, round-robin cursor) is saved with it. |
| `long budget`, `long budgetTick` | Section 5. Not saved: a loaded pipe starts at budget 0. |
| `Fluid lockFluid`, `long lockTick` | Fluid pipes only, section 7.3. Not saved. |
| `FlowAccumulator flow` | Section 9. Not saved. |
| `ItemStack overflow` / `FluidStack overflow` (S) | Section 6.4. Empty almost always. |

Pluggables (gates, wire, facades, plugs) are #60/#61 and attach to the block entity as in 4.2.2 `TileGenericPipe`.

### 3.2 Connection rule (#48)

Pipe `P` connects on side `s` to the block `N` at `P.pos + s` when all of these hold. This mirrors 4.2.2
`TileGenericPipe.canPipeConnect` → `Pipe.canPipeConnect` → `PipeTransport*.canPipeConnect`:

1. `P` has no plug on `s`.
2. If `N` implements the 4.2.2 `IPipeConnection` override (kept as an interface; engines implement it in #36) and
   `P`'s module is not the Isolator, its answer for `(P.kind, s.opposite)` wins: `CONNECT` or `DISCONNECT`.
   4.2.2: `IPipeConnectionForced.ignoreConnectionOverrides`, which only the sandstone pipes return true for.
3. If `N` is a pipe: `N` has no plug on `s.opposite`; `N.kind == P.kind` (item, fluid, power and structure never
   connect to each other, as each 4.2.2 `PipeTransport*.canPipeConnect`); and the pair is not banned (3.3).
4. If `N` is not a pipe: `P` is not a Structure Pipe and its module is not the Isolator, and `N` exposes the kind's
   capability on face `s.opposite`:
   - `ITEM`: an item handler with at least one slot (4.2.2 `PipeTransportItems.canPipeConnect`: sided inventory with
     accessible slots, or an inventory of size > 0).
   - `FLUID`: a fluid handler with at least one tank (4.2.2 `PipeTransportFluids.canPipeConnect`: `getTankInfo`
     non-empty).
   - `POWER`: an energy storage (`canReceive()` or `canExtract()` true). 4.2.2 `PipeTransportPower.canPipeConnect`
     connected to receivers on every power pipe and to emitters only on the wooden kinesis pipe; see Q6.
   - The Basic Extractor counts as an endpoint for `ITEM` pipes on its narrow face only (section 10).

The result is cached in `connectionMask` and recomputed when the pipe is placed, on `neighborChanged`, when a plug or
module is added or removed, and when a capability cache of a neighbour fires its invalidation listener.

### 3.3 Connection bans

4.2.2 `PipeConnectionBans` forbids: wood item↔wood item, wood fluid↔wood fluid, wood power↔wood power,
obsidian↔obsidian, emzuli↔emzuli, and stone↔cobblestone↔quartz (items and fluids). Provisional rule (Q16): two pipes
whose modules are both Extractor, both Filtered Extractor, both Pickup, or both Filtered Colour Tag do not connect;
material bans are dropped because the materials are now tiers.

### 3.4 Network graph (#49)

```java
final class PipeNetwork {
    final PipeKind kind;
    final int id;                        // unique per level session, for logs and tests
    final Long2IntOpenHashMap indexByPos; // BlockPos.asLong -> node index
    final PipeNode[] nodes;
    final Endpoint[] endpoints;
    final int[] sourceNodes;             // nodes whose module is a source (extractors, pickup), sorted by pos
    int sourceCursor;                    // fairness rotation, section 6.1
    boolean valid = true;
}

final class PipeNode {
    final long pos;
    final int tier;
    final PipeModule module;             // may be null; the live object from the block entity
    final byte connectionMask;
    final int[] next = new int[6];       // per side: node index, or -1 (none), or -2 - endpointIndex
}

record Endpoint(long pos, Direction face, BlockCapabilityCache<?, Direction> cache) {}
```

`PipeNetworkManager` is one transient object per `ServerLevel` (a level attachment or a map keyed by level; nothing is
saved). It holds `Long2ObjectOpenHashMap<PipeNetwork> byPos` and a `LongOpenHashSet dirty`.

**Building.** `PipeNetworkManager.get(pos)` returns the network containing `pos`. If `pos` is dirty or unmapped, it
builds the component by breadth-first search from `pos` over loaded pipe block entities of the same kind, following
`connectionMask` (a connection counts only if both ends have the bit set). Each reached pipe becomes a node; each
connected non-pipe face becomes an endpoint with a `BlockCapabilityCache` whose invalidation listener calls
`invalidate(pipePos)`. The new network replaces every mapping of its nodes and clears their dirty marks. Node order is
BFS order from the start, neighbours in side order, so the same world always builds the same arrays.

**Invalidation.** `invalidate(pos)` marks the network containing `pos` invalid, marks all its nodes dirty and removes
their mappings, then marks `pos` and its six neighbours dirty. It is called when:

- a pipe block entity loads (`onLoad`) or is removed (`setRemoved`, which also covers chunk unload);
- a pipe's `connectionMask`, module or plug changes;
- an endpoint capability cache is invalidated.

Nothing else invalidates: inventory contents, tank levels and energy levels are read live through the capability
during each walk. A pipe in an unloaded chunk is simply absent, so a chunk unload splits a network and a reload merges
it again. Walks never load chunks.

**Merge and split** need no special code: placing a pipe between two networks invalidates both through its
neighbours, and the next `get` builds one network; removing a pipe invalidates its network and each remaining piece is
built separately on demand.

**Ticking.** The manager runs once per level tick, at `LevelTickEvent.Post` on the server: for every valid network
with sources, run the sources (section 6.1). Pushes into pipe capabilities (machines, engines, other mods) happen
whenever the pushing block ticks and use the same network objects.

Unit tests for #49 run on a fake level that is just a map of positions to pipe stubs (no Minecraft bootstrap needed),
see section 14.

---

## 4. The routing walk (#50)

### 4.1 What 4.2.2 did

An item entering a pipe chose its exit once, on entry (`PipeTransportItems.injectItem` → `resolveDestination`):

1. `getPossibleMovements` (`PipeTransportItems.java:207`): adds the side the item came from to its blacklist, then
   keeps each other side `o` with `container.pipe.outputOpen(o)` and `canReceivePipeObjects(o, item)`.
2. `canReceivePipeObjects` (`:238`): the side must be connected; a neighbouring item pipe is always accepted (no
   look-ahead); a neighbouring inventory is accepted when a simulated `Transactor.add` takes at least one item.
3. The pipe's `PipeEventItem.FindDest` handler may filter the list (Diamond, Daizuli).
4. If the list is empty and `allowBouncing` is set (Iron, Daizuli), the side it came from is added back.
5. `Collections.shuffle`, take the first. An empty list gives `UNKNOWN`, and the item is dropped on the ground at the
   pipe centre (`moveSolids` → `dropItem`).
6. At the pipe end the item is injected into the next pipe, or inserted into the inventory; any remainder bounces back
   (`handleTileReached` → `reverseItem`) and chooses again.

So a junction picks uniformly among all valid sides, pipes and inventories alike, without knowing what is beyond a
pipe. That is the junction rule the walk reproduces (PLAN 8.3, decided).

### 4.2 The walk, step by step

Input: the network, the start node `S`, the side `in` of `S` the transfer enters from (the face toward the source
inventory, tank, Basic Extractor or pushing block), the resource (an item stack, a fluid stack), and a `RandomSource`
(the level's random; tests inject a seeded one).

State carried along: `visited` (node set), `path` (node list), `colour` (item colour, nullable, section 8.7) and a visit
counter.

`visit(node, in)` returns a destination or `FAIL`:

1. Count the visit; if the counter exceeds `transport.maxRouteVisits`, return `FAIL` for the whole walk.
2. Add `node` to `visited` and append it to `path`.
3. **Sink.** If `node.module` is Void: return `node` as the destination (4.2.2 `PipeItemsVoid.eventHandler(ReachedCenter)`
   removes every item reaching the centre; `PipeFluidsVoid.fill` accepts everything).
4. **Candidates.** For each side `o ≠ in` in side order, keep `o` if:
   - `node.next[o] != -1` (connected), and
   - `node.module` allows output on `o` (Director: `o == facing`; everything else: true), and
   - the target is a pipe not in `visited` and, for fluids, not locked to another fluid (7.3); or the target is an
     endpoint and a **simulated insert** of the resource into it on face `o.opposite` accepts at least 1 (one item,
     1 mB). The source endpoint itself is never a candidate.
5. **Module filter** (4.2.2 `FindDest`): Sorter (8.4) and Colour Sorter (8.8) reduce or reweight the list.
6. **Order** the remaining candidates by a random permutation drawn from `RandomSource` (uniform; the Sorter uses its
   weights, 8.4).
7. For each candidate in that order: an endpoint is the destination, return it; a pipe: `r = visit(next, o.opposite)`,
   return `r` unless it is `FAIL`.
8. **Dead end.** Remove `node` from `path` (but keep it in `visited`) and return `FAIL`.

The walk is `visit(S, in)`. Its result is the destination and the `path` at that moment.

Colour effects apply after a pipe's own choice: a Colour Tag module sets `colour` once its node has been entered and
before its children are visited, matching 4.2.2 where `FindDest` runs on entry and `ReachedCenter` (Lapis) runs after.

### 4.3 Differences from 4.2.2, on purpose

| 4.2.2 | Walk | Reason |
|---|---|---|
| A dead end drops the item | The walk backs up and tries the next candidate at the previous junction (step 8) | PLAN 8.3: nothing spills. Q1. |
| Bouncing (Iron, Daizuli `allowBouncing`) sends the item back the way it came, where the previous junction chooses again | Same thing, expressed as step 8 backing up | 4.2.2 `reverseItem` + `item.reset()` clears the blacklist, so the previous pipe re-chooses among its other sides, which is what backing up does |
| Items can circle a loop forever | `visited` stops revisits | No in-pipe state exists to circle |
| Remainder after a partial insert bounces and is routed again | The remainder stays in the source this tick and is tried next tick (6.3) | One destination per transfer keeps budgets simple |

### 4.4 Cost

Each pipe is visited at most once per walk, so a walk is O(pipes + endpoints) in the worst case and usually far less.
`transport.maxRouteVisits` bounds a pathological network (Q20). Simulated inserts are the expensive part; a walk does
one per endpoint candidate it meets.

---

## 5. Budgets (#50, #51, #52)

### 5.1 Units

All three kinds use the same integer accumulator, in a kind-specific unit:

| Kind | Budget unit | Refill per tick | One transfer of `n` costs |
|---|---|---|---|
| `ITEM` | 1/20 item ("item-tick") | `itemsPerSecond` units | `20 × n` units |
| `FLUID` | 1 mB | `fluidPerTick` | `n` mB |
| `POWER` | 1 FE | `powerPerTick` | `n` FE |

The item unit makes the fractional rate of PLAN 8.1 ("1 item/s = 0.05 items per tick accumulated") exact in integers:
a wood pipe gains 1 unit a tick and spends 20 per item. No floating point is involved anywhere in budgets.

### 5.2 Refill and cap

`cap = round(refill × 20 × transport.burstSeconds)`, at least the cost of one unit of transfer (20 item-units, 1 mB,
1 FE). With the default `burstSeconds = 1.0` a pipe can hold one second of throughput (Q4).

Refill is lazy. Reading a pipe's budget at game time `t`:

```
if (budgetTick != t) {
    budget = min(cap, budget + refill * (t - budgetTick));
    budgetTick = t;
}
```

A new or loaded pipe starts at `budget = 0, budgetTick = now`.

### 5.3 Spending

A transfer along `path` of an amount `want` (items, mB or FE) moves

```
n = min(want, accepted(destination, simulated), min over p in path of floor(budget(p) / costPerUnit))
```

and then subtracts `n × costPerUnit` from every pipe on the path. If `n == 0` the transfer is refused (6.3).
Budgets are checked after the walk: the walk ignores saturation, as PLAN 8.3 decides ("If any segment is saturated
for the amount, the transfer is reduced to what fits, or refused if nothing fits").

### 5.4 Worked examples (items, `burstSeconds = 1`)

**Tier 1, wood, 1 item/s.** Refill 1, cap 20. An Extractor on a wood pipe next to a chest, path = that one pipe.

| Tick | Budget before | Moves | Budget after |
|---|---|---|---|
| 1–19 | 1 … 19 | 0 | 1 … 19 |
| 20 | 20 | 1 | 0 |
| 40 | 20 | 1 | 0 |

One item every 20 ticks: 1 item/s.

**Tier 3, iron, 4 items/s.** Refill 4, cap 80. Budget reaches 20 at ticks 5, 10, 15, 20: four items a second.

**Tier 5, gold, 16 items/s.** Refill 16, cap 320.

| Tick | Before | Moves | After |
|---|---|---|---|
| 1 | 16 | 0 | 16 |
| 2 | 32 | 1 | 12 |
| 3 | 28 | 1 | 8 |
| 4 | 24 | 1 | 4 |
| 5 | 20 | 1 | 0 |

Four items per five ticks, sixteen a second.

**Tier 6, quartz, 32 items/s** (1.6 items a tick). Refill 32: tick 1 → 32, moves 1, keeps 12; tick 2 → 44, moves 2,
keeps 4; tick 3 → 36, moves 1, keeps 16; tick 4 → 48, moves 2, keeps 8; tick 5 → 40, moves 2, keeps 0. Eight items in
five ticks.

**Tier 10, ender, 512 items/s** (25.6 a tick). Refill 512, cap 10 240: tick 1 → 512, moves 25, keeps 12; tick 2 →
524, moves 26, keeps 4; over 5 ticks 128 items, over 20 ticks exactly 512. The Extractor's pull per tick is also
limited by the stack in the slot it pulls from (8.1), so a single 64-stack slot empties in three ticks.

**Bottleneck.** Extractor on a gold pipe → wood pipe → iron pipe → chest. Every transfer spends on all three, so the
wood pipe (1/s) sets the rate. The gold and iron budgets sit at their caps.

**Shared trunk.** Two Extractors on iron pipes (4/s each) feed one iron trunk pipe into a chest. The trunk refills 4
units a tick; the sources run in rotating order (6.1), so each moves about 2 items a second and the total is 4/s.

**Burst.** A stone pipe (2/s, cap 40 units) idle for 3 s holds 40 units, not 120: a burst of 2 items, then 2/s.

### 5.5 Worked examples (fluids and power)

**Fluid, tier 2 stone, 40 mB/t.** An Extractor on a stone fluid pipe facing a tank, through two stone pipes into
another tank. Each tick the pull is `min(40, budgets)`: 40 mB a tick, 800 mB a second, 1 bucket every 25 ticks.

**Fluid, mixed tiers.** Extractor on a gold fluid pipe (320 mB/t) through one iron pipe (80 mB/t): 80 mB/t.

**Power, tier 1 wood, 80 FE/t, cap 1 600 FE.** A redstone engine pulses 10 FE into it (PLAN 7): fits. A Stirling
engine pulse of, say, 300 FE every 20 ticks: the pipe has refilled to its cap of 1 600, the whole 300 FE goes through
if the receivers want it. Without accumulation (a cap of one tick, 80 FE) the same pulse would deliver only 80 FE;
this is why the cap matters for engines, which deliver in pulses (Q4).

**Power, tier 7 diamond, 5 120 FE/t.** Two consumers each requesting 3 000 FE/t behind one diamond pipe: requests sum
to 6 000; once any accumulated burst is spent, the pipe passes its refill of 5 120 FE a tick, split proportionally
(7.4): 2 560 each.

---

## 6. Sources, transfers and refusal (#50)

### 6.1 Source order

The manager ticks each network's `sourceNodes` once per tick, starting at index `sourceCursor` and wrapping, then sets
`sourceCursor = (sourceCursor + 1) % sourceNodes.length`. Each source makes **one** transfer attempt per tick
(the Basic Extractor attempts once per pulse instead, section 10). Pushes from blocks (machines, engines, hoppers,
other mods) are handled immediately when they call the pipe's capability.

### 6.2 Capabilities exposed by pipes

On every side whose neighbour is not a pipe of the same kind, a pipe exposes:

| Kind | Capability | Behaviour |
|---|---|---|
| `ITEM` | item handler, one virtual slot | `insertItem(stack, simulate)` runs a walk from this pipe with `in` = that side and returns the remainder (6.3). Extraction returns nothing. 4.2.2 `TileGenericPipe.injectItem` accepted any stack whole; here only what fits is taken. |
| `FLUID` | fluid handler, one tank of capacity = this tier's `fluidPerTick` | `fill(stack, action)` routes like items. `drain` returns empty, as 4.2.2 `PipeTransportFluids.drain` returned null. |
| `POWER` | energy storage, `canReceive` per Q6, `canExtract` false, `getEnergyStored` 0 | `receiveEnergy(x, simulate)` runs the power phases (7.4). |

A simulated call does the walk and the budget arithmetic without changing budgets, locks or flow records. The walk
uses the level random in both cases, so a simulated and a real call can choose different destinations; callers treat
the simulated answer as an estimate, as with any capability.

### 6.3 Results

Every transfer returns `TransferResult(moved, reason)` with `reason` one of `MOVED`, `PARTIAL` (moved < offered),
`NO_DESTINATION` (the walk failed), `NO_CAPACITY` (a destination exists but `n == 0` from budgets) and `SOURCE_EMPTY`.

- Whatever is not moved stays where it was: in the source inventory or tank, in the pushing machine, in the engine.
- Extractors that get `NO_DESTINATION` or `NO_CAPACITY` simply did not pull this tick (PLAN 8.3).
- A machine (P4) whose push returns `moved == 0` stops, enters the Blocked state (#63): red-light overlay, status
  panel reason "Output blocked" (or "No destination" when the reason is `NO_DESTINATION`), and the Machine trigger
  state "blocked". It retries its push every tick while blocked and leaves Blocked on the first `moved > 0`. 4.2.2
  machines dropped such items (`TileQuarry`, `TileMiningWell` via `Utils.addToRandomPipeAround` returning 0, then
  `dropItem`); that is the deliberate change.
- Nothing is ever spawned in the world. Only the Void module destroys, on purpose.

As built (#63, 2026-10-31): machines use `core/status/MachineOutput`. "No destination" means nothing connected takes
items at all; a pipe line whose only destinations are full (the walk backs out of them, so the walk itself returns
`NO_DESTINATION`) reports "Output blocked", as the #63 test in section 14 expects. See `docs/PORTING-NOTES.md`,
"Blocked output (#63)".

### 6.4 Executing a transfer

Items: (1) simulate extract `n` from the source slot; (2) real extract; (3) real insert into the destination; (4) if
the insert returns a remainder (a handler that lied in simulation), insert it back into the source slot; (5) anything
still left goes into the source pipe's `overflow` stack, which the pipe offers first on its next attempt and which is
dropped as an item only when the pipe is broken (as pipe contents were in 4.2.2 `PipeTransportItems.dropContents`).
Fluids: the same with drain and fill. Power has no remainder problem: the amount returned to the caller is the amount
delivered. See Q19.

As built (#50, 2026-10-31): the 26.x transfer API is transactional, so the walk, the destination insert, the budgets
and the source extract run in one transaction and commit together or not at all. Steps (4) and (5) never arise and
the overflow stack is not implemented; see `docs/PORTING-NOTES.md`, "Item routing walk and per-segment budgets".

After a successful transfer: spend budgets (5.3), record flow on every pipe of the path (section 9), and for fluids
lock every pipe of the path (7.3).

---

## 7. Fluids (#51) and power (#52)

### 7.1 What 4.2.2 fluids did

`PipeTransportFluids` keeps seven internal tanks per pipe (six sides plus centre) of 250 mB each (`LIQUID_IN_PIPE`),
with `flowRate` 10 mB/t (40 for gold and emerald) and a `travelDelay` ring of 12 (4) ticks. Each tick `moveFluids`
runs three phases:

1. `computeCurrentConnectionStatesAndTickFlows`: a side that recently received fluid is `Input` for `INPUT_TTL` 60
   ticks; other sides whose neighbour can take fluid (`canReceiveFluid`) are `Output`. A side that fails to deliver for
   `OUTPUT_TTL` 80 ticks cools down for `OUTPUT_COOLDOWN` 30.
2. `moveFromPipe`: each `Output` side tank pushes up to `flowRate` into its neighbour's `fill`.
3. `moveFromCenter`: the centre splits its available fluid across all `Output` sides **in proportion to their free
   space**, not at random.
4. `moveToCenter`: input side tanks drain into the centre, skipping any side whose fluid differs from the centre's
   (`!stackInCenter.isFluidEqual(testStack)`). The centre is a `FluidTank`, which refuses a second fluid.

That is the 4.2.2 one-fluid rule: a pipe's centre holds one fluid, and a different fluid cannot enter until the
centre has drained.

### 7.2 Fluid phases in Buildcraft Reforged

PLAN 8.3 decides that fluids use the same walk with fluid handlers as destinations and mB/t budgets. Each attempt:

1. **Offer.** The source offers fluid `F`, amount `a`: an Extractor offers `min(simulated drain of its facing tank,
   floor of its own budget)`; a push offers what was passed to `fill`.
2. **Walk** (section 4) with fluid endpoint test "simulated `fill(F, a) ≥ 1`" and pipe test "not locked to a fluid
   other than `F`" (7.3).
3. **Amount.** `n = min(a, simulated fill at the destination, budgets along the path)` (5.3).
4. **Execute** (6.4): drain `n` from the source, fill the destination.
5. **Lock** every pipe on the path to `F` and record flow.

The 4.2.2 even split (7.1 step 3) differs from the random walk. The spec follows the decided walk; whether fluids
should split evenly instead is Q2. Over many ticks the random walk spreads fluid across accepting outlets with
equal probability per junction rather than per outlet.

### 7.3 One fluid per segment

A fluid pipe stores no fluid, so the 4.2.2 rule becomes a lock:

- After fluid `F` passes a pipe, `lockFluid = F`, `lockTick = now`.
- The lock is live while `now - lockTick < transport.fluidLockTicks` (Q5). An expired lock counts as no lock.
- A walk carrying fluid `G ≠ F` treats a pipe with a live lock on `F` as impassable (it is not a candidate in step 4).
- If the source pipe itself is locked to another fluid, the attempt is refused with `NO_DESTINATION`.
- A Void module on a locked pipe still only accepts the locked fluid; the lock is checked before the sink test.

So two fluids can share a network as long as their paths do not overlap within the lock window, which matches 4.2.2
where each pipe could hold a different fluid. The GameTest "mixing refusal" (section 14, #51) relies on this.

As built (#51, 2026-10-31): the lock and the flow record's content are one per-pipe state, `FluidPipeState`; every
transfer renews it, a pipe on a branch the walk backed out of is not locked, and a simulated transfer locks nothing.
See `docs/PORTING-NOTES.md`, "Fluid routing (#51)".

### 7.4 Power: the 4.2.2 request/send phases

4.2.2 `PipeTransportPower.updateEntity` runs per pipe per tick:

1. **Send** (top of `updateEntity`): power that arrived last tick on side `i` (`internalPower[i]`) is split among the
   other sides `j` in proportion to their requests: `watts = internalPower[i] / totalPowerQuery * powerQuery[j]`, given
   to a receiver's `receiveEnergy` or to the neighbouring pipe's `receiveEnergy`, which caps what it takes at
   `maxPower` per side per tick.
2. **Request from receivers**: every adjacent non-pipe receiver with `powerRequest() > 0` adds its request on that
   side (`requestEnergy`).
3. **Forward requests**: for each side `i`, `transferQuery[i] = min(sum of powerQuery[j] for j ≠ i, maxPower)` is sent
   to the neighbouring pipe on side `i` as a request for the next tick.
4. `step()` swaps the double buffers once per world tick, so each hop costs one tick of latency.
5. Overload: `displayPower` is smoothed `(prev × 9 + now) / 10`; when the highest side exceeds `0.95 × maxPower` the
   `overload` counter rises, else falls, and the pipe is overloaded at `OVERLOAD_TICKS` 60.

Power entered only through wooden kinesis pipes (`PipePowerWood.updateEntity`), which buffered up to 1 500 MJ from
engines and fed it in.

Buildcraft Reforged keeps the two phases and the proportional split, makes them instant, and stores nothing (PLAN 7
and 8.3). When a block pushes `x` FE into power pipe `P` on side `s` (`receiveEnergy(x, simulate)`):

1. **Tree.** Breadth-first search over the network from `P`, neighbours in side order, giving each reached node one
   parent (shortest path, first found). The pushing endpoint is excluded as a receiver (accepted fix #1827, "do not
   send power where power is received").
2. **Request phase** (bottom-up). For every receiver endpoint `R` in the tree (`canReceive()` true):
   `d(R) = R.receiveEnergy(Integer.MAX_VALUE, simulate=true)`. For each node, children first:
   `req(node) = min(budget(node), sum of req(child) + sum of d(R) for endpoints at node)`.
   This is 4.2.2 step 3 (`transferQuery = min(sum of other sides, maxPower)`) over a tree.
3. **Send phase** (top-down). `a = min(x, req(P))`. A node receiving amount `y` gives each outlet `k` (child or
   endpoint) `share(k) = floor(y × req(k) / sum of req)`; the remainder `y − sum of shares` goes 1 FE at a time to
   outlets in side order whose share is below their request. 4.2.2 step 1.
4. **Deliver**: each endpoint gets `receiveEnergy(share, false)`. The accepted amounts are summed back up the tree;
   every node spends its budget by what actually passed through it. The total is returned to the pusher.
5. With `simulate`, steps 1–3 run without delivering, spending or recording.

Several pushes in one tick each run the phases again; later pushes see reduced budgets and the reduced demand of
receivers that were already filled.

Differences from 4.2.2, on purpose: no one-tick hop latency, no internal buffer, a tree instead of the 4.2.2 mesh (in a
loop 4.2.2 could pass power back and forth; the tree cannot).

**Overload** (for rendering and the TooMuchEnergy trigger): a pipe whose budget spending in a tick is at least 95 % of
its per-tick refill increments `overload`, otherwise decrements it (floor 0, ceiling 60); it is overloaded at 60.
Constants from `PipeTransportPower` (`OVERLOAD_TICKS`, `maxPower * 0.95`).

As built (#52, 2026-10-31): the phases run in the pusher's transaction, so a simulated push is an aborted transaction
(step 5 delivers into it and rolls back) rather than a separate code path. Each receiving block counts once, through
the first of its faces the tree reaches, and the pushing block is excluded on every face. See
`docs/PORTING-NOTES.md`, "Power (kinesis) routing".

**Power Limiter** (4.2.2 `PipePowerIron.PowerMode`: 2, 4, 8, 16, 32, 64, 128 MJ/t, set by wrench or the Power Limiter
gate action): becomes a per-pipe refill override of 20, 40, 80, 160, 320, 640, 1 280 FE/t, capped by the tier. The
Director module on a power pipe carries it (Q7, decided).

---

## 8. Modules (#55, #56, #57)

### 8.1 Framework

- A module is an item crafted in the Assembly Table (P5). Installing and removing it is Q18; provisional rule: use the
  module item on a pipe without a module to install it; sneak-use a wrench on the pipe to remove it, which drops the
  module item with its settings in a data component.
- One module per pipe. Gates, wire, facades and plugs are separate.
- A module has: `type`, saved settings, optional GUI (the 4.2.2 GUI of the pipe it replaces), optional hooks:
  `allowsOutput(side)`, `filterCandidates(list, walkState)`, `onEntered(walkState)`, `isSink()`, `isSource()`
  plus `tickSource(...)`, `onWrench(player, sneaking)`, `onNeighbourChange()`, `actions()` for gates.
- Modules need no power, ever (PLAN 8.2, decided). Source modules move at the tier rate of their pipe.
- Which modules a kind accepts is Q8. Provisional matrix, from what existed in 4.2.2:

| Module | 4.2.2 class | Item | Fluid | Power |
|---|---|---|---|---|
| Extractor | `PipeItemsWood`, `PipeFluidsWood` | yes | yes | no |
| Filtered Extractor | `PipeItemsEmerald` | yes | no | no |
| Director | `PipeItemsIron`, `PipeFluidsIron`, `PipePowerIron` | yes | yes | yes, as the Power Limiter (Q7) |
| Sorter | `PipeItemsDiamond` | yes | no | no |
| Pickup | `PipeItemsObsidian` | yes | no | no |
| Void | `PipeItemsVoid`, `PipeFluidsVoid` | yes | yes | no |
| Colour Tag | `PipeItemsLapis` | yes | no | no |
| Colour Sorter | `PipeItemsDaizuli` | yes | no | no |
| Filtered Colour Tag | `PipeItemsEmzuli` | yes | no | no |
| Isolator | `PipeItemsSandstone`, `PipeFluidsSandstone` | yes | yes | no |

As built (#55, 2026-10-31): the hooks are `PipeModule` methods (`onInstalled`, `onRemoved`, `onNeighbourChange`,
`allowsOutput`, `tickSource`, `onWrench`, `getMenuProvider`, `save`/`load`); `isSink` and `isSource` stay on
`PipeModuleType`; `filterCandidates`, `onEntered` and `actions()` come with #56, #57 and #60. A sneaking wrench puts
the module item in the player's inventory rather than on the ground. See `docs/PORTING-NOTES.md`, "Pipe modules".

"Same item" below means `ItemStack.isSameItem` (data components ignored). 4.2.2 compared id and damage, but ignored
damage for items without subtypes (`StackHelper.isMatchingItem`), and flattening turned subtypes into separate items.

### 8.2 Extractor (`PipeItemsWood`, `PipeLogicWood`, `PipeFluidsWood`)

- **Facing.** The module extracts from one adjacent face, stored in the module (4.2.2: block metadata). A valid facing
  is a non-pipe neighbour with the kind's capability whose block is not in the exclusion list
  (`PipeLogicWood.isValidFacing` → `isValidConnectingTile`, `PipeManager.canExtractItems`).
- On install and on every neighbour change, if the facing is invalid, switch (`switchSourceIfNeeded`). Wrench: switch
  to the next valid facing (`switchSource`: try `facing+1 … facing+6` mod 6 in side order, keep the first valid).
- **Items, per tick.** Let `k = floor(budget(source pipe) / 20)`. If `k == 0`, do nothing. Otherwise scan the facing
  handler's slots in index order (4.2.2 `checkExtractGeneric`: accessible slots from that side, first slot with a
  stack that `canExtractItem`). Provisional rule (Q13): take the first slot whose stack has a destination (one walk
  per slot tried, at most one successful transfer); the transfer moves up to `min(k, stack count)` from that slot,
  further limited by 5.3.
- **Fluids, per tick.** Offer `min(simulated drain of the facing tank, budget(source pipe))` (4.2.2 `PipeFluidsWood`
  drained up to `flowRate` per tick while it had energy).
- No engine. 4.2.2 required MJ (`PowerHandler.configure(1, 64.1f, 1, 64.1f)`, one MJ per item); that is replaced by
  the tier rate (PLAN 8.2, decided). The Basic Extractor keeps the engine model (section 10).

### 8.3 Filtered Extractor (`PipeItemsEmerald`, `GuiEmeraldPipe`, `ContainerEmeraldPipe`)

Extractor with nine ghost filter slots (stack size 1), a `currentFilter` cursor and a two-state button. Per attempt:

1. `getCurrentFilter()`: if slot `currentFilter % 9` is empty, `incrementFilter()` (advance past empty slots, at most
   9 steps). All slots empty → nothing is extracted.
2. Scan the facing slots in order for the first stack that is the same item as the filter and extractable.
3. Found: transfer as 8.2; on success `incrementFilter()`.
4. Not found and the button is **non-blocking**: try the following filters once each (`count < 9`). Button is
   **blocking** (default, `ButtonState.BLOCKING`): wait on this filter.

So both modes are round-robin over the filters; blocking waits for the current filter's item, non-blocking skips it.
PLAN 8.2 names the modes "round-robin/ordered" (Q9). Saved: filters, `currentFilter`, button state (NBT keys of 4.2.2
`currentFilter`, `state`).

### 8.4 Director (`PipeItemsIron`, `PipeLogicIron`, `PipeFluidsIron`)

- One output side, `facing`. `allowsOutput(o) = (o == facing)` (`PipeLogicIron.outputOpen`).
- Valid facing: a connected side whose neighbour is an item pipe without an Extractor-family module, or an endpoint
  (`isValidConnectingTile`: not `PipeItemsWood`, item transport, or `IInventory`). For fluids, the 4.2.2 fluid iron
  pipe equivalent.
- Placement: facing starts at `UP` then moves to the first valid one (`onBlockPlaced`: meta 1, then `switchPosition`).
- Wrench: next valid facing (`switchPosition`). Gate action Pipe Direction: set that facing if valid
  (`actionsActivated` → `setFacing`). The action list offers one Pipe Direction action per connected side.
- 4.2.2 also rotates once on every redstone input change (`switchOnRedstone`, called from `onNeighborBlockChange`);
  PLAN 8.2 lists only wrench and gate. Provisional rule: keep the redstone toggle (Q11).
- Bouncing (4.2.2 `allowBouncing = true`) is the walk's back-up step (4.3).

### 8.5 Sorter (`PipeItemsDiamond`, `GuiDiamondPipe`, `ContainerDiamondPipe`)

54 ghost slots: row `d` (side order, coloured rows in the GUI) holds slots `d × 9 … d × 9 + 8`. `filterCandidates`
mirrors `PipeItemsDiamond.eventHandler(FindDest)`:

1. For each candidate side `d`: if any slot of row `d` holds a filter, the row is "filtered"; each slot in the row
   that is the same item as the stack adds one entry for `d` to `matched` (duplicates are deliberate: "the
   probability to use that filter is higher").
2. A side whose row has no filter at all goes to `unfiltered`.
3. Result: `matched` if non-empty, else `unfiltered`. A filtered row that does not match is never used (the 4.2.2
   default rule of PLAN 8.2).

Ordering (walk step 6) is a weighted draw without replacement: draw an entry uniformly from the multiset, take its
side, remove all entries of that side, repeat. With no back-up needed this gives exactly the 4.2.2 probabilities.

### 8.6 Pickup (`PipeItemsObsidian`)

- Works only when the pipe has exactly one connection; the open side is its opposite (`Pipe.getOpenOrientation`).
- **Touch**: an item entity colliding with the pipe block is picked up (distance 0, `onEntityCollidedWithBlock`).
- **Suction** each tick: for `d = 1 … range`, nearest first, the box of `getSuckingBox(open, d)` (a `(2d+1) × (2d+1)`
  slab `d` blocks out on the open side); the first entity that can be sucked is pulled and the tick ends (`doWork`,
  `suckItem`).
- Can be sucked: live item entities with a non-empty stack that are not among the last 32 entities this pipe itself
  produced (`entitiesDropped`, kept for compatibility although the module never drops); arrows that can be picked up
  (become one arrow item); at `d = 1`, one stack from a chest minecart (`EntityMinecartChest`, now any container
  minecart).
- Amount: up to `floor(budget / 20)` items of that entity's stack, limited by the walk's destination (5.3). The entity
  shrinks or is removed; if nothing routes, it is left alone.
- 4.2.2 scaled with power (1 MJ per item per block of distance, range fixed at 4). PLAN 8.2 makes it tier-scaled
  without a formula: Q12. Provisional rule: `range = 1 + floor((tier − 1) / 3)` (tiers 1–3: 1, 4–6: 2, 7–9: 3, 10: 4),
  rate = the tier's items/s.
- Client effect: the 4.2.2 `TileEntityPickupFX` item-to-pipe particle, sent as part of the flow record (section 9).

### 8.7 Void (`PipeItemsVoid`, `PipeFluidsVoid`)

A sink: the walk ends at the pipe and everything that reaches it is deleted (walk step 3). Items: it is a candidate
like any pipe, so a junction next to a Void pipe sends it a random share, as in 4.2.2. Fluids: accepts any amount of
the fluid it is (or becomes) locked to. Budgets still apply along the path, including the Void pipe itself.

### 8.8 Colour Tag (`PipeItemsLapis`)

- One colour of the 16 (`EnumColor`), saved. Wrench: next colour; sneak + wrench: previous. Gate action Pipe Color:
  set that colour.
- `onEntered`: set the walk's `colour` to this colour (4.2.2 `eventHandler(ReachedCenter)` sets `item.color`).
- The colour exists only for the rest of the walk; the stack inserted at the destination is unchanged, as in 4.2.2
  where `TravelingItem.color` was lost on insertion.

### 8.9 Colour Sorter (`PipeItemsDaizuli`)

- One colour and one output side, both saved. Wrench: next valid output side (`PipeLogicIron`, as the Director);
  sneak + wrench: next colour. Gate actions Pipe Color and Pipe Direction. No redstone toggle (Daizuli does not call
  `switchOnRedstone`).
- `filterCandidates` (`eventHandler(FindDest)`): if the walk's colour equals the pipe's colour and the output side is
  a candidate, the only candidate is the output side; otherwise the output side is removed and the rest stay. So items
  of its colour go out one side and every other item goes elsewhere.
- Bouncing as the Director.
- PLAN 8.2 says "one colour per side"; 4.2.2 has one colour and one side (Q10).

### 8.10 Filtered Colour Tag (`PipeItemsEmzuli`, `GuiEmzuliPipe`, `ContainerEmzuliPipe`)

An extractor (Emzuli extends the wooden pipe) with four ghost filter slots, each with a colour (`slotColors`, 0 =
none, else colour + 1) and an active flag set only by gate actions:

1. Every gate update clears all active flags, then each active Extraction Preset action sets one: red → slot 0, blue
   → 1, green → 2, yellow → 3 (`actionsActivated`, `setActivePreset`).
2. No active flag → nothing is extracted.
3. Each attempt first calls `incrementFilter()`: advance to the next slot that has a filter and is active (at most 4
   steps). If that slot is empty or inactive, nothing this tick.
4. Extract the first facing slot holding the same item as that filter (8.2 rules), and start the walk with `colour` =
   that slot's colour (`makeItem`).

### 8.11 Isolator (`PipeItemsSandstone`, `PipeFluidsSandstone`)

Connects only to pipes and ignores connection overrides (`canPipeConnect` returns false for any non-pipe;
`ignoreConnectionOverrides` true). It never has endpoints, so walks pass through it but never deliver from it to a
machine (PLAN 8.3 "Isolator excludes machines").

---

## 9. Flow records and their sync (#59)

### 9.1 Record format

One record per pipe (and per Basic Extractor):

```java
record FlowRecord(
    PipeKind kind,
    Holder<?> content,  // Item or Fluid; empty for power and for no flow
    byte colour,        // walk colour 0-15, 16 = none (items only)
    byte sides,         // bits 0-2: entry side 0-5 (6 = centre/source); bits 3-5: exit side 0-5 (6 = sink)
    float rate,         // items/s, mB/t or FE/t
    boolean overload    // power only
) {}
```

Stream codec: kind (byte), content (registry holder codec for the kind's registry, optional), colour, sides, rate
(float), overload (bool). Under 16 bytes for any record.

### 9.2 Measuring (server)

Each pipe's `FlowAccumulator` adds the amount of every transfer that passes it (items, mB, FE) into the current
10-tick bucket and remembers the last content, colour and entry/exit sides. At every 10-tick boundary (game time
`% 10 == 0`) it closes the bucket: `rate` = sum of the last two buckets (20 ticks) converted to the unit:
items → per second (= the sum), fluid and power → per tick (sum / 20). Overload is copied from 7.4.

### 9.3 Sending

At each boundary, a pipe compares the new record with the last one it sent. It sends when any of these changed:
content, colour, sides, overload, `rate` crossing zero, or `|rate − sentRate| > 0.125 × max(rate, sentRate)`.
Otherwise nothing is sent. So each pipe sends at most once every 10 ticks, twice a second (PLAN 8.4), and a steady flow
sends nothing at all. A record that drops to zero is sent once; the client then stops rendering that pipe.

All records that changed in a level tick are batched per chunk into one client-bound payload
`FlowRecordsPayload(ChunkPos, short[] localPos, FlowRecord[] records)`, sent to the players tracking that chunk. The
current record is also in the block entity's update tag, so a player who starts tracking the chunk sees running flows
at once. There are no server-bound transport payloads (PLAN 19.2).

### 9.4 Client animation

- **Items**: icons of `content` (tinted marker if `colour` < 16, as 4.2.2 rendered `TravelingItem.color`) enter from
  the entry side, reach the centre and leave by the exit side. A new icon starts every `max(1 / rate s, 2 ticks)`;
  each takes 10 ticks to cross a pipe; at most 8 icons are drawn per pipe, so high tiers look like a full stream.
- **Fluids**: a column of `content`'s texture and tint from entry to exit. Width in pixels of 16 =
  `2 + 6 × clamp(rate / fluidPerTick(tier), 0, 1)` (8 px fills the 4.2.2 inner pipe, 0.25–0.75 block); texture scroll
  speed proportional to the same ratio.
- **Power**: the 4.2.2 kinesis texture glow, width `1 + 7 × clamp(rate / powerPerTick(tier), 0, 1)` px; red flicker
  when `overload` (4.2.2 `PipeRendererTESR` overload rendering).
- Nothing on the client simulates individual items. The block-entity renderer returns at once when `rate == 0`, so
  only pipes with flow cost a draw. The static pipe body is the cached model per connection mask (#48).

---

## 10. Basic Extractor (#58)

A block, not a pipe (PLAN 8.2). Its own classes; behaviour from 4.2.2 `PipeItemsWood` driven by an engine.

**Orientation.** Placed against a block: the wide plate faces the clicked inventory (the *inventory face*); the
narrow end on the opposite face is the *pipe face*. The four remaining faces accept engines, and gates and wire attach
as on a pipe end. A wrench rotates it to the next face that has an item handler (as `PipeLogicWood.switchSource`).

**Connections.** Only an item pipe on the pipe face connects to it, and it is an endpoint of that pipe's network
(3.2). It never accepts items from the pipe.

**Energy in.** It exposes an energy storage on the four side faces: `canReceive` true, stores nothing.
`receiveEnergy(x, simulate)` is one pulse. Engines deliver on each piston pulse (PLAN 7): a redstone engine 10 FE.

**Pulse rule.**

1. Accept at most 641 FE per pulse (4.2.2 wooden pipe receiver `configure(1, 64.1f, …)` × 10).
2. `want = floor(min(x, 641) / 10)` items: one item per 10 FE, as 4.2.2 used 1 MJ per item (`useEnergy(1,
   slot.stackSize)`). A redstone engine pulse moves 1 item; stronger engines more.
3. Pick the slot as the Extractor (8.2, Q13) on the inventory face, `want = min(want, stack count)`.
4. Walk from the pipe on the pipe face, `in` = the side facing the Basic Extractor. Budgets cap the amount (5.3):
   "capped by the line's tier" is the path budget with its burst cap (Q4). Example: a Stirling pulse worth 64 items
   into a wood line moves 1 item if the line's budget is full, 0 if it is not.
5. The pulse uses `10 × moved` FE. 4.2.2 threw away energy left after extracting (`PipeItemsWood.doWork` ends with
   `setEnergy(0)`). Provisional rule (Q14): return the rest to the engine (the return value of `receiveEnergy` is
   `10 × moved`).

**Gates.** A gate on the Basic Extractor gets the pipe gate actions. Energy Pulser and Single Energy Pulse pulse it
like an engine: every 10 ticks, `10 × min(2^(n−1), 64)` FE where `n` is the number of active pulser actions (4.2.2
`EnergyPulser.update`, `GateVanilla.resolveAction`, without the 1.01 factor that existed to clear the MJ activation
threshold). Extraction Preset has no 4.2.2 meaning on a filterless extractor (Q15).

**Without an engine** (and without a pulsing gate) it does nothing.

**Animation (client only).** It has a flow record like a pipe. When the engine facing it plays its pulse (the client
already animates the piston from the engine's synced state; the pulse is the moment progress passes 0.5, 4.2.2
`TileEngine.updateEntity` → `sendPower`), and the Basic Extractor's record has `rate > 0`, it plays one suction: the
diagonal stripes on the three steps scroll one stripe period from the inventory face toward the pipe face, and the
icon of the record's `content` slides out of the inventory face, through the nozzle, into the pipe, both over the
engine's remaining half-stroke (`ceil(0.5 / pistonSpeed)` ticks). Idle, the stripes stand still.

**Recipe.** Planks, glass, planks, as the 4.2.2 wooden pipe; 4.2.2 gave 8 pipes for it, how many Basic Extractors it
gives is part of Q17.

---

## 11. Structure Pipe (#54)

4.2.2 `PipeStructureCobblestone` with `PipeTransportStructure`: connects only to other structure pipes
(`PipeTransportStructure.canPipeConnect`), carries nothing, holds gates, wire and facades. Kind `STRUCTURE`; it never
joins an item, fluid or power network and has no budget, record or capability. Recipe: gravel + cobblestone item pipe
in 4.2.2; the cobblestone item pipe no longer exists, so it is part of Q17.

---

## 12. Gates, triggers and actions on pipes (#60)

Ported from 4.2.2 as PLAN 8.5. Transport-specific mapping:

| 4.2.2 | Buildcraft Reforged |
|---|---|
| `TriggerPipeContents.Empty` | Pipe's flow record has `rate == 0`. |
| `ContainsItems` / `ContainsFluids` (optional parameter) | `rate > 0`; with a parameter, the record's `content` is that item, or the fluid in that container (`FluidContainerRegistry.getFluidForFilledItem` → the item's fluid handler capability). |
| `ContainsEnergy` | Power pipe with `rate > 0` (4.2.2: any `displayPower > 1e-4`). |
| `RequestsEnergy` | 4.2.2 asked the wooden kinesis pipe's buffer (`PipePowerWood.requestsPower`); provisional: the last request phase through this pipe found demand larger than what it delivered (Q21). |
| `TooMuchEnergy` | `overload` (7.4). |
| `ActionPipeDirection` | Director and Colour Sorter facing. |
| `ActionPipeColor` | Colour Tag and Colour Sorter colour. |
| `ActionExtractionPreset` | Filtered Colour Tag (8.10); Basic Extractor (Q15). |
| `ActionPowerLimiter` | Director module on a power pipe (Q7). |
| `ActionEnergyPulser`, `ActionSingleEnergyPulse` | Pulse the Basic Extractor (section 10). On pipes, modules need no power, so they have no effect there (4.2.2 used them to pulse wooden pipes). |

All other triggers and actions (Pipe Signal, Redstone Input, Signal Output, Redstone Output) and every gate kind are
unchanged and are not transport-specific.

Implemented in #60 (31 Oct 2026): the pipe reads these through `PipeGateHolder.flow` (a `PipeFlow`, set by #59 and
#52) and hands module actions to `PipeGateHolder.target` (an `IPipeGateTarget`, set by #55 and #57); see
`docs/PORTING-NOTES.md` "Gates, pipe wire, and pipe triggers and actions (#60)".

---

## 13. Ownership of the remaining P3 issues

| Issue | Sections |
|---|---|
| #48 pipe block, connections, waterlogging | 3.1, 3.2, 3.3, 9.4 (cached model) |
| #49 network graph | 3.4 |
| #50 item walk and budgets | 4, 5, 6 |
| #51 fluids | 7.1–7.3 |
| #52 power | 7.4 |
| #53 30 tier pipes | 2 |
| #54 Structure Pipe | 11 |
| #55 framework, Extractor, Filtered Extractor, Director | 8.1–8.4 |
| #56 Sorter, Pickup, Void | 8.5–8.7 |
| #57 Colour Tag, Colour Sorter, Filtered Colour Tag, Isolator | 8.8–8.11 |
| #58 Basic Extractor | 10 |
| #59 flow records and rendering | 9 |
| #60 gates | 12 |
| #61 facades and plugs | 3.1 (plugs block connections) |
| #62 Filtered Buffer | Port of 4.2.2; a normal item endpoint for pipes |
| #63 blocked output | 6.3 |

---

## 14. Test cases

Unit tests run without a Minecraft server: the graph, walk, budget and power code take small interfaces (a position
→ pipe lookup, endpoint stubs with scripted simulate/execute answers, a seeded `RandomSource`, a game-time supplier).
GameTests use structures under `src/test/resources` or are built in code, and run with `runGameTestServer`.

### #48 Pipe block

- Unit: connection rule table — item↔item yes; item↔fluid no; item↔chest yes; item↔empty block no; fluid↔tank yes;
  power↔energy storage yes; plug on either side no; Isolator↔chest no; Isolator↔pipe yes; structure↔item pipe no;
  override `DISCONNECT` wins except on an Isolator.
- Unit: connection mask bit order matches side order.
- GameTest: a pipe between a chest and another pipe has mask bits for both; breaking the chest clears the bit.
- GameTest: waterlogged pipe keeps its connections and is still routed through.
- GameTest: all 64 masks produce a model without error (client test or model-bake unit test).

### #49 Network graph

- Unit: a straight line of 5 pipes is one network with 5 nodes; node order is BFS order.
- Unit: merge — two lines joined by a new pipe become one network; the old networks are invalid.
- Unit: split — removing the middle pipe of a line gives two networks.
- Unit: invalidation — endpoint capability invalidation marks the network dirty and the next `get` rebuilds it;
  changing chest contents does not.
- Unit: item and fluid pipes adjacent to each other stay separate networks.
- Unit: a pipe whose chunk is "unloaded" in the stub splits the network; reloading merges it.
- Unit: same input, same node and endpoint arrays (determinism).

### #50 Item walk and budgets

- Unit, walk: straight line to one chest reaches it; path is the line.
- Unit: junction with two chests, seeded random, 10 000 walks → each chest 50 % ± 2 %.
- Unit: junction with one chest and one pipe leading to a chest → each branch 50 % ± 2 % (no look-ahead, 4.2.2 rule).
- Unit: dead-end branch is backed out of; the walk still reaches the chest (Q1).
- Unit: loop of pipes with one chest terminates and reaches the chest.
- Unit: full chest is not a candidate; no other destination → `NO_DESTINATION`.
- Unit: the source endpoint is never the destination.
- Unit: `maxRouteVisits` exceeded → `FAIL`.
- Unit, budgets: every worked example of 5.4 (wood, iron, gold, quartz, ender, bottleneck, shared trunk, burst) as
  tick-by-tick assertions.
- Unit: config change of `itemsPerSecond` takes effect on the next refill; cap shrinks immediately.
- Unit: partial — destination accepts 3 of 10 → `PARTIAL`, 3 moved, 7 left in source.
- Unit: budget 0 on one path pipe → `NO_CAPACITY`, nothing moved, nothing spent.
- Unit: a handler that accepts less on execute than on simulate → remainder back in the source; if that fails, in
  `overflow`, offered first next tick.
- GameTest (10-tier throughput): for each tier, chest of 64 × 64 cobblestone → Extractor pipe → 4 pipes of the same
  tier → chest; after 200 ticks the target holds `10 × itemsPerSecond` items (± 1 for the start tick).
- GameTest: no item entity exists anywhere in the test area after any transfer test.

### #51 Fluids

- Unit: fluid walk with tank endpoints, partial fill, `NO_CAPACITY`.
- Unit: lock — pipe locked to water refuses lava within `fluidLockTicks`, accepts it after.
- GameTest (tier caps): for tiers 1, 5, 10, tank of water → Extractor → 3 pipes → empty tank; after 100 ticks the
  target holds `100 × fluidPerTick` mB (± one tick).
- GameTest (mixing refusal): water and lava sources share one trunk pipe; while water flows, lava is refused and the
  lava tank is unchanged; when water stops, lava flows after `fluidLockTicks`.
- GameTest: Void module on a fluid pipe deletes the fluid; Isolator never fills a tank.

### #52 Power

- Unit: request phase sums and caps per node (`min(budget, sum)`), on a hand-built tree.
- Unit: send phase proportional split with remainder in side order; example: 100 FE to requests 30/30/40 → 30/30/40;
  10 FE to requests 10/10/10 → 4/3/3.
- Unit: diamond example of 5.5 (6 000 requested, 5 120 budget → 2 560 each, starting from an empty budget).
- Unit: the pushing endpoint receives nothing (#1827).
- Unit: simulate changes nothing.
- Unit: overload counter reaches 60 after 60 saturated ticks and falls when the flow drops.
- GameTest (caps): creative-style FE source pushing 100 000 FE/t into a tier-1 and a tier-10 line delivers 80 and
  40 960 FE per tick to a sink with unlimited demand, averaged over ticks 21–120 (after the first second's burst).
- GameTest (delivery): a redstone engine through 5 wood power pipes powers a machine; delivered FE equals the engine's
  pulses × 10.
- GameTest: pipes report 0 stored energy at all times.

### #53 Tier pipes

- Unit: config parsing (all tiers, missing tier, invalid value → default + warning).
- GameTest: all 30 pipes exist, are craftable from their recipes, and report the configured caps.
- Datagen check: every pipe has a texture and a model.

### #54 Structure Pipe

- GameTest: places, connects only to structure pipes, joins no network; holds a gate and a facade once #60/#61 land.

### #55 Framework, Extractor, Filtered Extractor, Director

- Unit: Extractor facing switch order `facing+1 … facing+6`; invalid facing auto-switches on neighbour change.
- Unit: Extractor slot choice (Q13): first routable slot.
- Unit: exclusion list blocks extraction.
- Unit: Filtered Extractor blocking vs non-blocking sequences, all-empty filters → no extraction, cursor saved.
- Unit: Director output only on facing; refused facing backs up to the previous junction.
- GameTest per module: Extractor moves items at its tier rate with no engine; Filtered Extractor pulls only filter
  items in round-robin order; Director sends everything out of its facing, wrench and Pipe Direction action rotate it,
  redstone toggle per Q11.

### #56 Sorter, Pickup, Void

- Unit: Sorter matched/unfiltered rule and weights (two matching slots on side A, one on side B → 2:1 over 9 000 walks
  ± 2 %); filtered non-matching side never used.
- Unit: Pickup range per tier (Q12) and the `getSuckingBox` geometry for all six open sides.
- GameTest: Sorter separates cobblestone and dirt into two chests; unfiltered items go to the side with no filters.
- GameTest: Pickup collects an item entity at range 1 and at the tier's maximum range, not beyond; does nothing with two
  connections; leaves the entity if nothing routes.
- GameTest: Void deletes items and fluids; its branch takes its random share at a junction.

### #57 Colour Tag, Colour Sorter, Filtered Colour Tag, Isolator

- Unit: Colour Tag sets the walk colour for later pipes only; destination stack unchanged.
- Unit: Colour Sorter: matching colour → output side only; other colours → never the output side.
- Unit: Filtered Colour Tag preset flags from actions, `incrementFilter` order, no active flag → nothing.
- GameTest: Colour Tag (red) → Colour Sorter (red, east) sends red-tagged items east and untagged ones elsewhere.
- GameTest: Filtered Colour Tag with gate presets red + blue alternates the two filter items, tagged correctly.
- GameTest: Isolator next to a chest never inserts into it, and passes items to the next pipe.

### #58 Basic Extractor

- Unit: pulse arithmetic: 10 FE → 1 item; 641 FE → 64 items; 1 000 FE → capped at 641; budget cap applies; returned
  FE = 10 × moved (Q14).
- GameTest: redstone engine + Basic Extractor + chest + wood pipe line + chest moves one item per engine pulse.
- GameTest: no engine → nothing moves after 200 ticks.
- GameTest: autarchic gate with Energy Pulser pulses it every 10 ticks.
- Client check (manual script): stripes scroll and the item slides on each pulse; still when idle.

### #59 Flow records

- Unit: rate measurement over two 10-tick buckets for each kind.
- Unit: send rule: steady rate sends nothing; 12.5 % change sends; zero crossing sends; content change sends; never
  more than once per 10 ticks.
- Unit: stream codec round trip, record size < 16 bytes.
- GameTest: a running line produces at most 2 records per pipe per second over 200 ticks, and 0 per pipe once steady.
- Client test: renderer skips pipes with `rate == 0`; item icon spacing matches `1 / rate`.

### #60 Gates

- GameTest per mapped trigger and action of section 12 (Pipe Contents kinds, Pipe Direction, Pipe Color, Extraction
  Preset, Power Limiter, Energy Pulser, Single Energy Pulse), plus the unchanged ones per the 4.2.2 behaviour.

### #61 Facades and plugs

- GameTest: a plug on a side removes that connection and invalidates the network; removing it restores both.

### #62 Filtered Buffer

- GameTest: a pipe inserts into and extracts from it per its 4.2.2 filter rules.

### #63 Blocked output

- GameTest: a machine whose only output pipe leads to a full chest shows the red light, reports "Output blocked",
  and its Machine trigger reads "blocked"; emptying the chest clears all three within a tick or two.
- GameTest: with no pipe and no inventory at all the reason is "No destination"; nothing is dropped.

---

## 15. Decisions

Decided 29 Oct 2026 (#97): every provisional rule below is the rule. Q7 is settled as: a Director module on a
power pipe is the 4.2.2 Power Limiter, with modes 20, 40, 80, 160, 320, 640 and 1 280 FE/t, switched by wrench and
the Power Limiter gate action. References to these questions elsewhere in this file point here.

| # | Question | 4.2.2 / PLAN facts | Rule |
|---|---|---|---|
| Q1 | What does the walk do at a dead end? | 4.2.2 dropped the item (`resolveDestination` → `UNKNOWN` → `dropItem`). PLAN 8.3: nothing spills; "the first inventory on that walk that accepts". | Back up to the previous junction and try its next candidate (4.2.2 bouncing already did this for Iron/Daizuli). Alternative: refuse and retry next tick with a new random walk. |
| Q2 | Fluids: random walk or 4.2.2 even split? | 4.2.2 `PipeTransportFluids.moveFromCenter` splits among all accepting sides in proportion to free space. PLAN 8.3 says fluids use "the same walk". | Random walk, as decided. |
| Q3 | Is a "segment" a pipe block? | PLAN 8.1 says per-pipe budget; 8.3 and 8.4 say per-segment. | Yes, one pipe block. |
| Q4 | How much budget can a pipe accumulate? | PLAN 8.1 gives only the per-tick rate. Engines deliver in pulses (PLAN 7), so with a one-tick cap a pipe would take only one tick's worth per pulse. | Cap = 1 s of rate (`transport.burstSeconds` = 1.0) for all kinds. |
| Q5 | How long does a pipe stay "holding" a fluid? | 4.2.2 residual fluid stayed in a pipe until it drained, indefinitely with no outlet. New pipes store nothing. | Lock for `transport.fluidLockTicks` = 20 ticks after the last flow. |
| Q6 | Which power pipes accept power from engines and generators? | 4.2.2: only the wooden kinesis pipe (`PipeTransportPower.canPipeConnect` emitter check, `PipePowerWood`). The new ladder has no per-material behaviour and no power module. | Every power pipe accepts pushes from any adjacent non-pipe block. Alternative: only pipes with an Extractor module. |
| Q7 | Where does the Power Limiter live? | 4.2.2 `PipePowerIron` (iron kinesis pipe, modes 2–128 MJ/t, wrench and gate action). PLAN maps "iron pipe" to the Director, whose "force the exit side" has no meaning for power; 8.5 keeps the Power Limiter action. | Director on a power pipe acts as the limiter, modes × 10 FE/t, wrench and gate action. |
| Q8 | Which modules fit which kinds? | 4.2.2 had wood, iron, void, sandstone and emerald fluid pipes (emerald fluid was only faster), and no special power pipes besides wood and iron. | Matrix in 8.1. |
| Q9 | Filtered Extractor modes. | PLAN 8.2: "round-robin/ordered". 4.2.2 `PipeItemsEmerald`: blocking / non-blocking, both round-robin. | 4.2.2 blocking / non-blocking. |
| Q10 | Colour Sorter: one colour per side, or one colour and one side? | PLAN 8.2: "one colour per side". 4.2.2 `PipeItemsDaizuli`: one colour, one output side, all other items to the other sides. | 4.2.2 behaviour. |
| Q11 | Does a redstone change rotate the Director? | 4.2.2 `PipeLogicIron.switchOnRedstone` rotates iron item and fluid pipes on each redstone change. PLAN 8.2 lists wrench and gate only. | Keep the redstone toggle. |
| Q12 | Pickup tier scaling formula. | 4.2.2: range 4 fixed, 1 MJ per item per block of distance. PLAN 8.2: "tier-scaled", no formula. | Range `1 + floor((tier − 1) / 3)`, rate = tier items/s. |
| Q13 | Which slot does an Extractor pull from? | 4.2.2 `checkExtractGeneric`: the first extractable slot, routed or not (unroutable items were dropped). | The first slot whose stack has a destination. Alternative: first slot only, and wait if it cannot route. |
| Q14 | Basic Extractor: what happens to pulse energy it does not use? | 4.2.2 `PipeItemsWood.doWork` discards it (`setEnergy(0)`). | Return it to the engine (accept only 10 FE per item moved). |
| Q15 | What does Extraction Preset do on a Basic Extractor? | PLAN 8.2 says it keeps working there. In 4.2.2 the action only exists on Emzuli pipes (`PipeItemsEmzuli.getActions`), which have filters; the Basic Extractor has none. | No effect until decided. |
| Q16 | Connection bans. | 4.2.2 `PipeConnectionBans`: wood↔wood (all kinds), obsidian↔obsidian, emzuli↔emzuli, stone↔cobblestone↔quartz. | Keep the module bans (Extractor, Filtered Extractor, Pickup, Filtered Colour Tag never connect to the same module); drop the material bans. |
| Q17 | Recipes for fluid and power pipes, Structure Pipe and the Basic Extractor yield. | PLAN 8.1: one pattern per kind, "glass between two material items, as 4.2.2". 4.2.2: fluid = item pipe + sealant, kinesis = item pipe + redstone, structure = gravel + cobblestone item pipe (no cobblestone pipe now). Wooden pipe recipe gives 8. | 4.2.2 shapeless recipes on the same-material item pipe; structure from gravel + stone item pipe; Basic Extractor yields 1. |
| Q18 | How are modules installed and removed, and do they keep settings? | PLAN 8.2 says crafted in the Assembly Table, one per pipe; nothing about fitting. | Use the module item on the pipe; sneak + wrench removes it, settings kept in a data component. |
| Q19 | Where does a remainder go when a handler accepts less than it promised in simulation? | 4.2.2 bounced it back into the pipe. Nothing may spill. | Back to the source; else a one-stack overflow on the source pipe, retried first, dropped only when the pipe is broken. |
| Q20 | Is a cap on walk size acceptable? | No 4.2.2 equivalent (items walked one pipe at a time). | `transport.maxRouteVisits` = 4096 pipes per walk; beyond that the walk fails. |
| Q21 | Meaning of the RequestsEnergy trigger. | 4.2.2 asked the wooden kinesis pipe's 1 500 MJ buffer (`PipePowerWood.requestsPower`); pipes no longer buffer. | Demand found in the last request phase through this pipe exceeded what it delivered. |
