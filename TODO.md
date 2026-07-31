# worldgen: what is left, and what you need to know to do it

Handoff for the `world-gen` branch. Phases 0–2 of a planned 8 are done and committed; **Phases 3–8 remain**.
Every line reference below was checked against the tree at commit `a82c5704d`.

Read [worldgen-architecture.md](worldgen-architecture.md) first — it is the design *and* the ledger, and its
Implementation Status section is kept true as work lands. This file is the work queue; that file is the argument.

---

## How this module is worked on

Five habits, each of which exists because ignoring it cost a day:

1. **Measure, do not reason.** Every constant this branch has moved was moved by printing a distribution and
   looking at it. My cleanest-sounding argument for `quietUplift` — "twice the interior uplift, so a quiet
   interior scores and an orogen does not" — was simply false, and cost every world all but one of its lakes.
2. **Look at the exported PNGs every phase.** Both geo bugs and both civ bugs in the retune were found that way,
   and so was the last defect in Phase 2: six perfectly circular lakes, every test green, every sweep clean.
3. **Confirm a regression test fails against the old code** before keeping it. The doc records two occasions
   where a first attempt passed against the bug it was written for.
4. **Never pipe gradle into `tail`/`grep` and trust `$?`** — the pipe's exit status is the last command's. Write
   to a log file and check the exit code directly. This hid a real failing test for a round.
5. **An invariant that skips its subject reports success.** This module spent a year with zero lakes on every
   world while `lakes stand above their beds` passed, because it opens with `if (lakes[x,y] == 0) continue`.
   When you add a check, make sure it cannot pass vacuously — or add a companion that asserts the subject exists.

### Verification recipe

Run all of it per phase, in this order. The last three catch what the first misses.

```
./gradlew :worldgen:test                                   # 334 tests at present
./gradlew :worldgen:invariants -Pseeds=120 -Pcells=192      # sweep; watch the reported spreads
./gradlew :worldgen:invariants -Pseeds=30  -Pcells=256
./gradlew :worldgen:viewerExport -Pout=build/viewer         # PNGs + the SeamCheck line; works headless
./gradlew :worldgen:viewerExport -Pgenesis -Pout=build/gen  # the 128 km world zone-server actually boots
./gradlew :worldgen:probe -Pchannels=1                      # river cross-sections against the voxel grid
./gradlew :worldgen:town -Pcensus                           # every settlement in one table
./gradlew :worldgen:chronicle -Pquests                      # unresolved history threads (Phase 6)
./gradlew :zone-server:test
```

- `SeamCheck: clean - 64 chunks, 3584 shared columns agree` must appear on **every** export. Phase 7 is the one
  phase that can genuinely break it.
- Run at **both 128 km and 512 km**. Every bug in the retune was a threshold in metres meeting a world it was not
  tuned for, and the 512 km reference world must not move.
- **`zone-server` has 6 known failures** at HEAD — `AiBehaviorScenarioTest` ×3, `AiLifecycleE2ETest`,
  `AiProfileRegistryTest`, and `ZoneEngineTest > destroying an entity with no synced component sends no vanish`.
  They are unrelated to worldgen. Confirm by `git stash` before blaming yourself for one.

### Things that bite

- **Stage params are not persisted.** They are compile-time defaults folded into `pipelineVersion`, so *any*
  change to them shifts terrain for an existing world and `worldgen.on-mismatch: REFUSE` stops the server. Dev
  ships `REGENERATE`, which is why this is invisible until it isn't.
- **Bump the stage `version`** for any behaviour change. Current: tectonics 4, climate 2, erosion 4, glacial 2,
  hydrology 3, biome 1, resource 1, habitability 1, settlement 2, history 1, town 3, economy 1.
- **`verifyOutputs` demands declared outputs equal produced outputs exactly.** A new layer or feature kind that
  is emitted but not declared throws at runtime, and vice versa.
- **Dependency scoping is transitive**, and reads are denied if undeclared. Adding one edge grants a stage its
  new dependency's whole closure.
- **`StandardWorldTest` pins the exact topological order.** Any genuinely new stage changes it; a new *pass*
  inside an existing stage does not.
- **`MapRenderer.colorOf` is an exhaustive `when` over `FeatureKind` with no `else`** — a new kind is a compile
  error until the viewer draws it. Leave that check to do its job.
- **`Biome.of(ordinal)` *coerces* into range**, so a `-1` sentinel silently reads as the last enum entry. Use
  `entries.getOrNull`. Biome ordinals are on-disk: append only, never insert.

---

## Phase 3 — Seasonal precipitation, as four layers

**Far cheaper than it looks: the seasonal passes already run and are thrown away.**
`ClimateStage.seasonalPrecipitation` ([ClimateStage.kt:296](worldgen/src/main/kotlin/net/bestia/worldgen/climate/ClimateStage.kt#L296))
returns a `List<Grid>`, one per season. They are summed into `PRECIPITATION`, collapsed into the
`PRECIPITATION_SEASONALITY` scalar by `seasonality` ([:407](worldgen/src/main/kotlin/net/bestia/worldgen/climate/ClimateStage.kt#L407)),
and dropped. `ClimateParams.seasons` is 2 ([:98](worldgen/src/main/kotlin/net/bestia/worldgen/climate/ClimateStage.kt#L98))
with the KDoc *"More passes buy detail that nothing downstream currently reads."* This phase gives them a reader.

**Settled with the user: four seasonal fields, not twelve monthly. Monthly is derived on demand.**

1. `seasons` 2 → 4, and change the season→belt-shift map from linear to **sinusoidal**. Today
   ([:314](worldgen/src/main/kotlin/net/bestia/worldgen/climate/ClimateStage.kt#L314)) it is
   `seasonalShift * (2*season/(seasons-1) - 1)`, which for four gives −6, −2, +2, +6 — two near-equinox fields
   that differ, when physically spring and autumn share a belt position. `seasonalShift * sin(2π·season/seasons)`
   gives 0, +6, 0, −6.
2. **Sinusoidal shift alone makes spring and autumn identical**, because belt shift is the sweep's only seasonal
   input. Fix by making the sweep's temperature seasonal: `TEMPERATURE_RANGE` already *is* the summer-to-winter
   swing, so `T_season = T_mean ± (range/2)·sin(...)` with the sign flipping by hemisphere. It feeds
   `Winds.capacity`, which drives both evaporation and convective rain, so the four fields become genuinely
   distinct and hemisphere-correct — a southern monsoon in the right half of the year.
3. **`scaleToMean` must return its factor.** It computes `target/mean` locally and mutates only the annual sum
   ([:428](worldgen/src/main/kotlin/net/bestia/worldgen/climate/ClimateStage.kt#L428)), so emitted seasonal
   fields would not sum to `PRECIPITATION`. Return it and apply to each season. **This is a blocker, not a
   detail** — without it the invariant in the last bullet cannot hold.
4. Four new `LayerId`s. Keep `PRECIPITATION` and `PRECIPITATION_SEASONALITY` unchanged so all seven existing
   consumers keep working untouched.
5. `Climate.precipitationAtMonth(x, y, month)` — a pure function, periodic Catmull-Rom over the four.
   `StationTable.sampleChannel` already does Catmull-Rom; reuse the maths rather than a second copy. This is the
   "monthly on demand" half of the answer, and `pop/`'s food model is the first plausible caller.
6. Consider replacing `seasonality`'s min-max with a concentration index — with four seasons it ignores the two
   middle ones. Optional, but note it either way, since the doc quotes the layer as meaningful.
7. **Check `Winds.BELT_TRANSITION = 8.0` still clears the shift**
   ([Winds.kt:105](worldgen/src/main/kotlin/net/bestia/worldgen/climate/Winds.kt#L105)) — its own KDoc warns
   about exactly this, and a belt boundary that stops blending is a step function drawn across every continent.
   That is the bug the retune just finished removing; do not put it back.

**Viewer:** `Palettes.forLayer` ends in `else -> ContinuousPalette(Ramps.VIRIDIS)`, so the four *would* appear
with no change — but each would auto-range independently, which is wrong for four fields whose whole purpose is
being compared. **Add an explicit arm** binding all four to one shared range. Add a `DifferenceField` preset for
summer-minus-winter; `DifferenceField` already exists.

**Invariants:** the four sum to `PRECIPITATION` within scaling; each non-negative; add the new layers to the
lists in `checkNormalisedLayersAreInRange`; and the hemisphere property — a northern cell's wettest season
differs from a southern cell's at matched latitude.

---

## Phase 4 — Top-2 biome blending

Closes the second half of **deviation 4**. Symmetrical to Phase 3: `classify`
([Biome.kt:135](worldgen/src/main/kotlin/net/bestia/worldgen/bio/Biome.kt#L135)) already tracks `secondScore` to
derive `BIOME_CONFIDENCE` and **discards the runner-up's identity**.
[Biome.kt:94](worldgen/src/main/kotlin/net/bestia/worldgen/bio/Biome.kt#L94) already promises this
(*"the runner-up's score gives a blend weight for free"*).

- `classify` tracks `secondBest` beside `secondScore`. **The subtle bit:** set `secondBest = best` inside the
  `sum < bestScore` branch, *before* overwriting `best`
  ([Biome.kt:148](worldgen/src/main/kotlin/net/bestia/worldgen/bio/Biome.kt#L148)); the existing `else if` at
  [:151](worldgen/src/main/kotlin/net/bestia/worldgen/bio/Biome.kt#L151) only fires for non-winners, so writing
  it there alone loses the case where the winner *changes*.
- `BiomeMatch` gains `runnerUp`. New `BIOME_SECONDARY` `IntLayer`. Reuse `BIOME_CONFIDENCE` as the blend weight
  rather than adding a layer that is a monotone function of one already stored.
- **Sentinel care:** see `Biome.of` under *Things that bite*. Use `getOrNull`, as `Labels.forLayer` already does.
- **Decide the overridden-cell policy and say why.**
  [BiomeStage.kt:166](worldgen/src/main/kotlin/net/bestia/worldgen/bio/BiomeStage.kt#L166) sets
  `confidence = 1.0` whenever an edge biome overrides the classifier (*"an overridden cell is not a
  classification at all"*). The same logic says its secondary is either the *climatic* winner or the sentinel.
- `Palettes.forLayer` and `Labels.forLayer` each need one arm copying `BIOME`'s.
- **Give it a consumer or it is Phase 3's problem again.** The natural one is `voxel/SurfaceSampler`, which
  already samples biome at a **noise-warped** position to make boundaries ragged — the dither-not-blend trick the
  classifier KDoc describes. Extend it to dither between the pair by blend weight, hashing *world* position as
  `OreVeins` and the ruin rubble scatter do, so it stays seam-free.
- Bump `BiomeStage.version` (1 → 2).
- Tests: at a prototype centre the winner is that biome with high confidence; on a boundary the pair is the two
  adjacent biomes; the pair is order-stable across runs.

---

## Phase 5 — Sea lanes

Closes **deviation 7**. The rejection is one line —
[SettlementStage.kt:426](worldgen/src/main/kotlin/net/bestia/worldgen/civ/SettlementStage.kt#L426),
`if (route.cells.any { submerged[it] }) continue`. It throws away exactly the information needed: **collect
those pairs instead of dropping them.**

- New `FeatureKind.SEA_LANE` near `ROAD(500)`. See the `colorOf` note under *Things that bite* — the compile
  error is the feature working.
- **A `MarkerFeature`, not a `PolylineFeature`.** It carries a polyline plus per-vertex stations and has
  `affectsHeight = false`, so chunk generation skips it entirely — which matters because an ocean-spanning bbox
  lands in `FeatureIndex`'s `oversized` list and is then tested against *every* query. `FAULT` is the precedent.
- Reuse `RouteFinder` unchanged over a second, *water* cost grid: cheap on water, expensive on land.
  **`minimumCost` must be set to that field's actual floor**
  ([RouteFinder.kt:28](worldgen/src/main/kotlin/net/bestia/worldgen/civ/RouteFinder.kt#L28)) or the A\* heuristic
  stops being admissible and the route stops being optimal. Note
  [`Terms.IMPASSABLE = 400.0`](worldgen/src/main/kotlin/net/bestia/worldgen/civ/Terms.kt#L373) is finite
  deliberately, *"so a path across a strait can still be found"*.
- Reuse `gabrielEdges` ([:464](worldgen/src/main/kotlin/net/bestia/worldgen/civ/SettlementStage.kt#L464)) and
  `simulateTraffic` ([:500](worldgen/src/main/kotlin/net/bestia/worldgen/civ/SettlementStage.kt#L500)) — both are
  generic over a node list and a route map, so the trade network becomes one graph with two edge types.
- New `SeaLaneChannels` beside `BridgeChannels`: traffic, depth, and the two endpoint settlement indices. Use a
  small integer index, **not** a `FeatureId` — the KDoc on `SETTLEMENT_HISTORY` in `VectorFeature.kt` explains
  that a 64-bit hash loses its low bits to a `Double`.
- `StageOutput.Vector(SEA_LANE)` or `verifyOutputs` throws. Bump `SettlementStage.version` (2 → 3).
- Invariants: every station is over water; endpoints are coastal settlements; no lane crosses the ocean margin
  (the margin is where the wrap seam hides, and a lane through it is a road across the seam by another name).

This is also what makes island settlements reachable, which `pop/` needs — a port with no lane has a trade term
it cannot justify.

---

## Phase 6 — Special sites, with lore

**Place them in `HistoryStage`, not `SettlementStage`** — a change from first instinct, on the evidence. The
doc's rule reads *"history does not place settlements"*, and `MONUMENT` and `TOMB` are **already**
history-placed sites, so the rule is really about settlements specifically. Every input the four sites need is
already inside `HistorySim` or one query away, and `HistoryStage` already depends on `ResourceStage`. Placing
them here reuses `SiteKind`, `SiteChannels`, `addSite`, `log`, `siteName`, pruning and provenance wholesale —
which is what makes the lore free rather than a second system.

**`raiseMonuments` ([HistorySim.kt:939](worldgen/src/main/kotlin/net/bestia/worldgen/history/HistorySim.kt#L939))
is a working template** for a gated, once-only, per-civ site founding with an event logged. One new pass per site
type, in its shape:

| Site | Gate, from inputs that already exist |
|---|---|
| Mine | `ORE_DEPOSIT` markers — `RICHNESS`/`QUANTITY`/`DEPTH`, read as `volcanismField` already reads features; plus a standing settlement in range |
| Monastery | remote *and* defensible: low `habitability` in `SiteFacts`, plus the "skip if within clearance of a place" idiom from `roadsideInns` |
| Fort | `frontierDistance` already computes inter-civ frontier distance; `passQuality` already scores passes and its comment says *"which is why a fort or a market ends up on it"* |
| Lighthouse | **`SiteFacts.coastal` ([HistorySim.kt:46](worldgen/src/main/kotlin/net/bestia/worldgen/history/HistorySim.kt#L46)) is computed and never read** — a free input, so adding a reader changes nothing existing. Phase 5 gives lanes for it to guard |

Lore comes from three mechanisms already built: names are 48-bit seeds (`Names.site`'s
`else -> "the $form of $of"` handles new forms with **no `Names` edit**); provenance chains end at a *site*, so a
relic in a sacked monastery is already expressible; and `-Pquests` already mines unresolved threads, so
lore-bearing sites feed the quest miner with no new code.

**Mechanical checklist:**

- 4 `SiteKind` values, 4 `EventKind` values (precedent exists — `SETTLEMENT_GREW` and `FIGURE_BORN` are declared
  and unemitted), 4 `FeatureKind` values, 4 salts from the free `0x25`.
- `importance` comes only from `EventKind.baseImportance` and is never overridden per event, so the constant you
  choose *is* whether the site survives pruning and shows in `topEvents`. Pick deliberately.
- Four exhaustive `when (SiteKind)` will break, which is the good kind: `HistorySim.siteName`,
  `HistoryStage.siteMarker`, and two in `voxel/TownStructures.kt`. **One is a trap:**
  [TownStructures.kt:192](worldgen/src/main/kotlin/net/bestia/worldgen/voxel/TownStructures.kt#L192) has
  `else -> SiteKind.MONUMENT`, so a new kind silently materialises as a stone monument instead of failing to
  compile. Make it exhaustive in the same change.
- `siteColumn` ([TownStructures.kt:387](worldgen/src/main/kotlin/net/bestia/worldgen/voxel/TownStructures.kt#L387))
  is where blocks grow. **No new `BlockType` is needed** — `MASONRY`, `TIMBER`, `PLASTER`, `THATCH`, `ROOF_TILE`,
  `PLANK`, `RUBBLE`, `COBBLESTONE` build a mine head, a cloister, a curtain wall and a lighthouse. So
  `paletteVersion()` does not move and **no client release is required**.
- Keep every site radius under `ChunkMaterializer.MARKER_MARGIN = 320.0` or
  `checkStructuralMarkersFitTheQueryMargin` fails — and note that check filters on an explicit `structural` set,
  so **new kinds must be added to it** or they are exempt by accident.
- `SiteChannels` has no `KIND` channel — kind lives in `FeatureKind`, so four kinds is cheaper than one kind plus
  a `TYPE` channel.
- Bump `HistoryStage.version` (still 1) — it invalidates `TownStage` and `EconomyStage`, which both depend on it.
- Fix the stale reference at
  [HistorySim.kt:1330](worldgen/src/main/kotlin/net/bestia/worldgen/history/HistorySim.kt#L1330):
  `checkStructuralMarkersAreWithinTheQueryMargin` → `checkStructuralMarkersFitTheQueryMargin`.

**Invariants:** a mine names a real deposit and is in reach of a standing settlement; a lighthouse is coastal and
clear of any town; nothing is founded in water; a site's founding year precedes every event about it. Note
`checkEveryRuinHasAnEvent` asserts ruin-site count equals ruined-settlement count *exactly*; it filters on
`SiteKind.RUIN` so new kinds are safe, but do not emit two sites from `abandon`.

**Viewer:** four colours in `colorOf` (compiler-forced). These are real places, so keep them **out** of
`HIDDEN_BY_DEFAULT` — that set is for attribute records pinned to a settlement's coordinates. Consider bumping
`ViewerFrame.LEGEND_HEIGHT` (190, sized for ~12 rows; the pipeline emits ~30 kinds already, though the panel does
scroll). `ChronicleMain` iterates `SiteKind.entries` so it picks the new kinds up free; have `TownMain` mention
sites near the settlement it renders.

---

## Phase 7 — Chunk-scale droplet erosion (last, and gated)

Closes **deviation 1**, which the doc argues *against* closing: *"any error in that blend puts back exactly the
seams the vector tier exists to remove."* Last because it is the riskiest and the only phase nothing else needs.

- The hook is the `BaseHeightField` interface itself, wrapped at
  [StandardWorld.kt:131](worldgen/src/main/kotlin/net/bestia/worldgen/pipeline/StandardWorld.kt#L131). A
  `DropletHeightField(inner, config, seed)` decorator needs no change to `ChunkHeightSampler`,
  `ChunkMaterializer`, `ChunkSeamCheck` or the feature system. Its KDoc already specifies the requirement.
- **Tiles on a fixed lattice, never keyed on the asking chunk.** `ChunkSeamCheck` compares shared columns at
  `epsilon = 0.0` — *bit-identical* — and a blend of two independently simulated overlaps is not bit-identical
  unless the weights sum to exactly 1.0 in floating point at every column. So: partition world space into
  droplet tiles on a fixed grid (say 4× chunk extent) with a margin, and make `heightAt` a deterministic function
  of the tiles containing the position, queried in a fixed order with a partition-of-unity blend. Two chunks
  asking about the same column then ask the same tiles in the same order and get the same double. **This is the
  single thing most likely to go wrong.**
- Needs a **thread-safe** tile cache — `ChunkSeamCheck` runs four threads, and so will any real chunk worker.
- Runs on the base heightfield only, **before vector features are stamped**; droplets would otherwise erode the
  channel just carved.
- **Off by default** behind a param until the seam check is clean at several sizes. If it ships off, say so
  plainly in the doc — a half-closed deviation is what the doc's framing is most careful about.
- If it becomes a `WorldConfig` knob rather than a stage param it must join `shapeVersion`'s explicit field list,
  and then `PersistedWorld`, `WorldConfigMapping`, `WorldGenSettings.FLAGS` and `WorldArgs` all need it. Prefer a
  param and avoid that entirely.
- `ChunkSeamTest` extended to run with droplets on **is the entire safety argument**. `ProbeMain` is the right
  tool to read the result at 48 m — it is what found the dashed rivers.

---

## Phase 8 — Close the loop

Update `worldgen-architecture.md`: move the closed items off *Still missing*, retire deviations 4 (second half),
and 7, add the new layers and feature kinds to *The standard pipeline*'s emission table, extend the
asserted-invariants list, re-run the seed sweep and record the land-fraction and lake-count spread, and update
the test count in the status header.

Then boot the server. Every phase bumps `pipelineVersion`, so an existing world hits `worldgen.on-mismatch`; dev
ships `REGENERATE`, which publishes `WorldRecreatedEvent` and moves every player to the new spawn. **Confirm that
path runs rather than assuming it** — it has never been exercised by any of this work.

---

## Not in scope, and why

From the doc's *Still missing* list, deliberately untouched:

- **The polygon geometry type** — the root of deviations 2–5. Alluvial fans, deltas, lakes, coastlines and
  settlement footprints all want it. That is a subsystem, not a pass, and every one of those deviations is
  survivable without it.
- **Steps 12–13** (delta persistence, client-side base generator), **sharding, the work queue, the gRPC
  surface** — the server's, and built nowhere.
- **Oil and gas** — skipped because nothing downstream consumes them, which is the same reason the seasonal
  fields were dropped for a year. Do not add a layer without a reader.
- **`WorldWrap` has two callers.** Chunk streaming and spawn-point selection normalise; movement, interest
  management and pathing use naive subtraction, so two players ten metres apart across the seam read as a world
  apart. Real bug, outside worldgen.
