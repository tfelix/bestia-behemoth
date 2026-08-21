---
name: worldgen
description: Explains the bestia-behemoth world generator (`worldgen/` Gradle module) — the 23-stage world-tier pipeline (tectonics/climate/hydrology/biomes/history/towns/...), the vector feature system (Ring/AreaFeature/PolylineFeature, blend modes, StationTable), the chunk/voxel tier (ChunkMaterializer, BlockType palette, RleCodec), the store/ caching+versioning tiers, and how it plugs into zone-server (PersistedWorld, boot-time version gate, chunk streaming) and the Godot client. Read this BEFORE touching any file under worldgen/, before adding/reordering a Stage, before changing a params class or a block type, or when investigating "why does this world look like X" / "how does a chunk get from the generator to the client". Triggers on: worldgen, Stage, GenContext, WorldGenPipeline, WorldParams, LayerId, FeatureKind, VectorFeature, Ring, AreaFeature, StationTable, ChunkMaterializer, VoxelChunk, BlockType, RleCodec, ChunkCache, ChunkStore, DerivedStore, PersistedWorld, WorldService, ChunkService, ChunkStreamSystem, pipelineVersion, paramsVersion.
---

# worldgen: the world generator

`worldgen/` is a standalone Gradle module: pure Kotlin stdlib + JDK, **no Spring, no JPA, no I/O**
outside `viewer/` (enforced at build time by the `:worldgen:checkBoundaries` task — compile classpath
must be kotlin-stdlib only, no `java.io`/`java.nio`/`java.awt`/`javax` import outside `viewer/`). It is
a pure function: `(seed, WorldConfig, WorldParams) → GeneratedWorld`. `zone-server` depends on it and
owns the only real I/O (persistence, streaming); the Godot client only decodes what the server sends.

Three long-form docs used to sit at the repo root — `worldgen-architecture.md` (the original design
doc), `SUMMARY.md` (a dev-log of one later branch), and `TODO.md` (a phase-by-phase handoff, "worldgen:
what is left") — and all three were narrative history rather than current fact, stale by whole subsystems
(the architecture doc's stage table stopped at twelve stages; the real count reached 22, then 23). This
skill replaced `SUMMARY.md` outright, and later replaced `worldgen-architecture.md` and `TODO.md` too,
once their still-useful design rationale and working habits (the reasoning behind decisions, not the
numbers) had been folded in here and into `bestia-docs`' server-side world-generation page; all three
files were then deleted rather than left to drift further. Treat `pipeline/StandardWorld.kt` and the
actual `Stage` subclasses as ground truth for anything this skill might itself fall behind on — the
current stage count, layer/feature lists, and version numbers above all.

## Two tiers

- **World tier** — immutable rasters (`FloatLayer`/`IntLayer`, addressed by `LayerId`) and vector
  features (`VectorFeature` subclasses, addressed by `FeatureKind`), built once per world by a DAG of
  `Stage`s. Everything here is kilometre-scale.
- **Chunk tier** — stateless samplers over the world tier (`ChunkHeightSampler`, `ChunkMaterializer`)
  that turn a world position into a voxel chunk on demand. Nothing here is stored per-world; it's
  regenerated from the world tier every time, and cached by the `store/` tier keyed on version.

Package layers, each usable only by the ones below it (never sideways between stage packages —
they communicate only through declared `Stage.dependencies`, never by calling into each other):

```
vector/     geometry: Ring, AreaFeature, PolylineFeature, PointFeature, MarkerFeature, FootprintFeature,
            StationTable, Profiles, FeatureIndex, FeatureEvaluator, blend modes
core/       Stage, GenContext, GenRng, LayerStore/FeatureStore, WorldGenPipeline, Chronicle, WorldWrap
fields/     Noise, Grid, Points (Poisson disk), DistanceTransform, DoubleIntHeap

geo/ climate/ hydro/ bio/ resource/ civ/ history/ pop/ mana/ karst/ spawn/ poi/   ← the 23 Stages, see below

voxel/      BlockType palette, Stratigraphy, ChunkMaterializer, RleCodec, CaveNetwork, VegetationScatter
derived/    ChunkDelta (player edits), ColumnSummary, OpacityGrid, WalkableTile, DerivedStore
store/      ChunkCache -> ChunkStore -> DeflatedBlobStore, VersionGate
pipeline/   StandardWorld (assembles stages into a GeneratedWorld), Invariants (regression sweep)
viewer/     Swing tooling + CLI entry points; the only package allowed I/O
```

## The stage pipeline

`StandardWorld.stages()` (`pipeline/StandardWorld.kt:204-256`) constructs **23 stages** in one order,
but that construction order is cosmetic — its own KDoc says so directly: *"in declaration order; the
pipeline sorts them itself"* (`:195-196`). `WorldGenPipeline`'s constructor topologically sorts every
stage by its declared `dependencies`, breaking ties **alphabetically by `StageId.name`**
(`core/WorldGenPipeline.kt:67-68, 122, 315-344`). This tie-break bit the module for real once already —
see [the orphaned glacial stage](#the-glacial-lesson).

Declared construction order, with the load-bearing reason from each stage's own KDoc:

| # | Stage | Package | Raster `LayerId`s | Vector `FeatureKind`s | Why here |
|---|---|---|---|---|---|
| 1 | `TectonicsStage` | `geo/` | `BEDROCK_ELEVATION`, `PLATE_ID`, `ROCK_HARDNESS`, `CRUST_AGE`, `UPLIFT` | `FAULT`, `HOTSPOT` | root, no deps |
| 2 | `ClimateStage` | `climate/` | `TEMPERATURE(_RANGE)`, `PRECIPITATION` + 4 seasonal, `DISTANCE_TO_OCEAN` | — | reads tectonic elevation for the orographic sweep |
| 3 | `ErosionStage` | `geo/` | `ERODED_ELEVATION`, `SEDIMENT` | `TECTONIC_BASIN` | stream-power needs precipitation |
| 4 | `HydrologyStage` | `hydro/` | `FLOW_DIRECTION`, `FLOW_ACCUMULATION`, `DISCHARGE`, `WATER_LEVEL`, `LAKE_ID` | `RIVER_CHANNEL`, `RIVER_CONFLUENCE` | routes flow over the **final** surface — depends on `GlacialStage`, see below |
| 5 | `VolcanismStage` | `geo/` | `VOLCANISM` | `VOLCANIC_VENT`, `LAVA_POOL` | craters must exist before biomes read distance-to-crater; own stage because `Stage.version` reaches the RNG and retuning vent spacing must not reseed every mountain |
| 6 | `BiomeStage` | `bio/` | `BIOME`, `BIOME_SECONDARY`, `BIOME_CONFIDENCE`, `SOIL_FERTILITY`, `SOIL_DEPTH` | — | classifies on volcanism + climate + hydrology |
| 7 | `GlacialStage` | `geo/` | `ELEVATION` (final), `ICE_THICKNESS` | `GLACIAL_TROUGH`, `FJORD`, `CIRQUE`, `MORAINE` | sole producer of `ELEVATION`; carves the fluvial surface last |
| 8 | `PondStage` | `hydro/` | — | `LAKE`, `OXBOW_LAKE` | a moraine dam only exists once the ice that left it is gone; specifically the water priority-flood in hydrology *cannot* find |
| 9 | `AlluviumStage` | `hydro/` | — | `ALLUVIAL_FAN`, `DELTA` | sub-kilometre floodplain shapes the raster can't hold, fed from erosion's own sediment budget |
| 10 | `VegetationStage` | `bio/` | `CANOPY_COVER` | — | kilometre summary of the same density function the chunk-tier scatter uses, so raster and voxels can't disagree |
| 11 | `ResourceStage` | `resource/` | `RESOURCE_VALUE` | `ORE_DEPOSIT` | geology-driven deposit placement |
| 12 | `CaveStage` | `karst/` | — | `CAVE_SYSTEM`, `CAVE_PASSAGE`, `CAVE_ENTRANCE` | reads the chunk tier's own rock tuning (`StrataParams`), not a copy of it |
| 13 | `ManaStage` | `mana/` | `MANA_DENSITY` | — | must precede history (history reacts to it); corruption must *follow* history, which is why mana/corruption are two stages, not one |
| 14 | `HabitabilityStage` | `civ/` | `HABITABILITY`, `MOVEMENT_COST` | — | scores settleability |
| 15 | `SettlementStage` | `civ/` | — | `SETTLEMENT`, `SETTLEMENT_GRADING`, `ROAD`, `BRIDGE`, `SEA_LANE` | places settlements/roads before history judges them |
| 16 | `HistoryStage` | `history/` | — | `SETTLEMENT_HISTORY`, `RUIN`, `ASH_RUIN`, `BATTLEFIELD`, `TOMB`, `MONUMENT`, `MINE`, `MONASTERY`, `FORT`, `LIGHTHOUSE`, `CAVE_HOARD`, `WOUND`, `SHRINE` | dates/holds/burns settlements already placed — **does not place them** |
| 17 | `CorruptionStage` | `mana/` | `CORRUPTION`, `CIVILISATION_DISTANCE` | — | the settlements it suppresses by are the ones history left standing |
| 18 | `SpawnerStage` | `spawn/` | — | `BESTIA_SPAWN` | last in dependency terms: reads corruption + settlements + what history left standing |
| 19 | `VegetationStandStage` | `spawn/` | — | `VEGETATION_STAND` | lives in `spawn/`, not `bio/`, specifically because it reads `CORRUPTION`, which doesn't exist yet when `bio/VegetationStage` runs |
| 20 | `TownStage` | `civ/` | — | `STREET`, `BUILDING`, `DISTRICT`, `TOWN_WALL`, `GATE` | street/building/district layout |
| 21 | `EconomyStage` | `pop/` | — | `SETTLEMENT_ECONOMY`, `BUSINESS`, `ROADSIDE_INN` | businesses/households |
| 22 | `PoiStage` | `poi/` | — | `POI` | the only stage whose output is a coin toss per catalogue entry rather than derived from the land; reads settlements/sites/cave mouths to stay clear of them |
| 23 | `NavGraphStage` | `civ/` | — | — (`StageOutput.Navigation`) | last: routes are read off roads/bridges/gates/cave mouths everything above placed; nothing reads it back |

`history` is the only stage declaring `StageOutput.History`; the pipeline requires there be at most one
(`core/WorldGenPipeline.kt:106-108`) — two stages writing one world's history isn't resolvable.

**The real execution order differs from the table above** because several stages declare dependencies
that outrank their construction position:

```
tectonics -> climate -> erosion -> glacial -> hydrology -> { alluvium, volcanism }
  -> { biomes, pond } -> { caves, mana, vegetation } -> resources -> habitability
  -> settlements -> history -> { corruption, poi } -> { spawners, towns, vegetation_stands }
  -> economy -> nav_graph
```

`PoiStage.dependencies` is `{glacial, hydrology, biomes, caves, settlements, history}` — no dependency on
`corruption`, `towns`, or anything after, so it runs any time after `history`, alongside `corruption`
rather than behind it.

Concretely: `HydrologyStage.dependencies` includes `GlacialStage.ID` (`hydro/HydrologyStage.kt:313-314`)
because it needs the *final* `ELEVATION`, dragging `VolcanismStage` and `BiomeStage` behind glacial too
even though they're constructed before it. `PondStage` depends on `AlluviumStage` even though
`AlluviumStage` is constructed three lines *after* it. `TownStage` runs before `VegetationStandStage`
purely because `"towns" < "vegetation_stands"` alphabetically. **Reordering the list in
`StandardWorld.stages()` changes nothing; only editing a `dependencies` list moves a stage.**

### The glacial lesson

For most of this module's life `GlacialStage` was an undeclared sibling of `HydrologyStage`, and the two
only ever ran in the right order because the topo-sort's alphabetical tie-break happened to put
`"glacial"` before `"hydrology"`. A trough carves the raster absolutely, so every stage below hydrology
was quietly reading a coarse surface a chunk would later carve out from under it — a town built on a
2463 m ridge got a 525 m plinth because the ground moved 525 m after the town's foundation was decided.
Declaring the dependency turned an alphabetical accident into a guarantee, and also gave the world its
first lakes: `hydro/Lakes.kt`'s priority-flood had never once received a closed basin, because erosion's
incision step clamps every cell to its receiver's height, and a carved trough floor is the one thing that
routinely violates that. The general lesson: **the scheduler enforcing "a stage may read only what it
declares" is half a guarantee — it cannot catch something you *should* have declared and didn't.** The
other half is putting a layer in the hands of the last stage that changes it, so skipping the dependency
fails loudly (an undeclared-read exception) instead of quietly returning a non-final surface.

## Cross-cutting mechanisms (`core/`, `pipeline/`)

**`Stage` contract** (`core/Stage.kt:81-135`): declares `dependencies: List<StageId>`, `outputs:
List<StageOutput>` (`Raster`/`Vector`/`History`/`Navigation`), a hand-written `version: Int`, and an
**abstract `paramsVersion: Long` with no default** — a new stage that forgets to fold its tunables into
a digest has no meaningful version, and `ParamsVersionTest` asserts every stage's digest is non-zero.
`LayerStore`/`FeatureStore` hand each stage a view scoped to its transitive dependency closure
(`core/WorldGenPipeline.kt:260-272`); reading an undeclared layer throws immediately
(`core/LayerStore.kt:92-105`). After every stage runs, `WorldGenPipeline.execute` diffs its *declared*
outputs against what it actually wrote (`core/WorldGenPipeline.kt:274-310`) — an undeclared layer or
feature kind is a hard failure, not a silent pass-through.

**RNG** (`core/GenRng.kt`): counter-based/SplitMix64, every stream *derived* by hashing
`(seed, stageId, stageVersion, coordinates...)` (`GenRng.derive`, `:75-83`) — there is no shared mutable
RNG anywhere, so a coordinate gets the same stream regardless of execution order or thread count.
**Params never reach the RNG** — `ParamsDigest`'s KDoc says so directly (`core/ParamsDigest.kt:39-43`):
params decide *what* the arithmetic computes, never *which* random numbers it consumes. This is the
module's core debugging habit: change a tunable and get the same world, just reshaped. `Stage.version`
*does* reach the RNG — bumping it reseeds that stage and everything downstream, so it's reserved for
code changes, never for retuning a constant.

**Versioning — three numbers, three questions.** What follows is the generator's half. The server's half —
what the boot gate does with these, the three failure modes, and **how to reset a world in development** —
is the `world-versioning` skill; read that one before bumping any of these numbers or when a boot fails
with `WORLD_PIPELINE_MISMATCH`.

| Number | Answers | Where |
|---|---|---|
| `Stage.version` / `Stage.paramsVersion` | did this stage's code / tunables change? | folded per-stage, transitively, into `WorldGenPipeline.versionVector` (`core/WorldGenPipeline.kt:124-139`) |
| `pipelineVersion` | did the *world tier* change at all? | the chunk cache key (`WorldGenPipeline.chunkCacheKey`, `:149-150`); becomes `PersistedWorld.pipelineVersion` server-side |
| `WorldParams.chunkTierVersion` | did anything **after** the stage graph change (detail noise, `Stratigraphy`, droplets, `ChunkMaterializer.VERSION`)? | folded into `pipelineVersion` at construction (`core/WorldGenPipeline.kt:64`) — it decides terrain but isn't a stage |

`ParamsDigest` (`core/ParamsDigest.kt:81-181`) hashes fields **by name, sorted by name** — reordering a
constructor is free, swapping two fields' values is not. Deliberately not `hashCode()` (`DoubleArray`'s
is identity-based — every JVM process would get a different digest) and not `toString()`
(locale/runtime-dependent float rendering).

**`WorldParams`** (`pipeline/WorldParams.kt:64-178`) is one object naming every stage's params type
— roughly 220 tunables across ~20 classes. `.resolved` (`:186-209`) forwards values one stage owns but
another must agree with (erosion's ocean margin, settlement's habitability weights, town's grading
limits, nav's habitability threshold) so two independently-defaulted copies of the same number can't
silently diverge.

Any of them can be overridden from a **params file** (`core/ParamsText.kt`, passed as `-Pparams=file`
to the viewer tasks): a flat list of dotted keys, `params-format = 1` first, where the schema is
*derived* from what readers ask for — so a key nobody reads is an error with a line number and a
nearest-key suggestion. `resource/OreTuning.kt` is the per-resource corner of it, and the one most
often wanted:

```
resource.ore.diamond.spacing   = 1.3   # candidate spacing: smaller looks in more places -> more deposits
resource.ore.diamond.abundance = 0.06  # tonnes per 1000 km², the world's total -> richer deposits
resource.ore.diamond.floor     = 2     # deposits EVERY world gets, however badly it suits diamond
```

The first two answer different halves of "how much is there": spacing decides how many finds exist,
abundance decides how rich each one is. `resource.tonnageScale` is the global version of the second
and `resource.candidateSpacing` of the first. Defaults for all three live on the `MinableOre` enum;
an unset key reads back the enum's number, so nothing is duplicated.

**Every world holds every mineable ore**, and `floor` is the promise. `ResourceStage.guarantee` tops a
short ore up from the best ground the world has, and it can only place where suitability is **above
zero** — so it cannot invent a volcano, and the deposit stands where the causal model said it should
have. Two things follow that are easy to get wrong:

- The floors are per ore (3 for the staples, 1 for the luxuries) because each one blocks
  `oreSeparation` of ground around itself, and thirteen ores at three each over-subscribes a 128 km
  world — measured, mithrandium fell from three deposits to one.
- When the full 12 km dispersal distance leaves an ore nowhere, the top-up falls back to
  `resource.guaranteeSeparation` (2.5 km) rather than leave it out. **Coverage outranks dispersal.**
  It never fires at 192 km, touches at most four deposits on a 128 km world, and is what makes 64 km
  worlds work at all.

`OreCoverageTest` asserts the promise on the shipped tuning, per world. `GemDepositTest` and
`VolcanicResourceTest` deliberately build with the floors **off** (`RawGeology.PARAMS`) — on the real
defaults the floor would answer their question for them and hide an unreachable suitability arm.

**`Invariants`** (`pipeline/Invariants.kt`, ~48 named checks) is the regression harness: `sweep()`
builds N worlds in parallel and runs every check over each — physics (discharge never decreases
downstream, lakes stand above their beds), history/chronicle self-consistency, civil structure (nothing
built in water, walled settlements have a gate), mana/corruption targeting, and existence checks for
every newer subsystem (`checkTheWorldHasSpawners`, `checkDistrictsHoldTheirBuildings`, ...). Several are
explicitly framed as tripwires against a subsystem that runs and silently produces nothing — the failure
mode this module has shipped more than once.

**An open question, not a settled one**: river counts going 512 → 1024 km gave 7.2×, 3.5× and 6.1× for 4×
the area across three seeds — channel initiation is a threshold on catchment *area*, which should make the
network scale-free in principle, and the measurement is consistent with that but does not confirm it
(against a 44–207 spread at a single size, three samples can't separate mild superlinearity from noise). If
it matters, it wants a dozen seeds per size, not three - nothing in this cleanup pass re-measured it.

**A subsystem that is complete, tested and never *reached* looks exactly like one that works.** Cited
throughout this module's own comments (and a few in `zone-server`) as "habit 6" - a shorthand for a pattern
that shipped dead three times over: sea lanes that produced none on forty worlds because the port search
looked 3 km for a harbour, four built site kinds that produced none on any world because `year -
Int.MIN_VALUE` overflowed the interval gate, and seasonal precipitation fields that had been summed and
discarded for a year. Every test was green in all three cases. **Count the output before believing the
tests.** Where existence can't be asserted per seed - most worlds legitimately have none of a given rare
thing - pin the check to a seed that does have one, rather than writing a conditional that passes vacuously
on every seed that doesn't.

## The vector feature system (`vector/`)

Five geometry primitives implement `VectorFeature` (`vector/VectorFeature.kt:335-384`), each evaluated
column-by-column via `evaluateColumn`:

- **`PolylineFeature`** (`vector/PolylineFeature.kt:35`) — the workhorse: a centerline + per-vertex
  `StationTable` + a height profile. Rivers, glacial troughs, roads, moraines, fjord axes are all this
  with a different profile.
- **`AreaFeature`** (`vector/AreaFeature.kt:69`) — a closed `Ring` + optional profile, for lakes, fans,
  deltas, coastlines. `Ring.MAX_EXTENT` (100 km — where the fixed-point cross product would overflow) and
  the much tighter `AreaFeature.MAX_AREA_EXTENT` (8 km — measured from `FeatureIndex`'s real overflow
  threshold) are two separate caps for two separate reasons.
- **`PointFeature`** (`vector/PointFeature.kt:32`) — a radial disc, mainly to smooth river confluences
  where two `MIN`-blended cuts would otherwise leave a hard crease along the bisector.
- **`MarkerFeature`**/**`PointMarker`** — geometry + attributes only, `affectsHeight = false`: plate
  boundaries, ore deposits, cave systems, every history site.
- **`FootprintFeature`** (`vector/FootprintFeature.kt:38`) — an oriented rectangle (two dot products),
  cheap, for buildings.

**`Ring`** (`vector/Ring.kt:85`) is deliberately not "a `Polyline` with a repeated endpoint" — its KDoc
lists six ways that breaks. `contains()` is a crossing-number test done **entirely in fixed-point
integers** (`vector/Quantize.kt`), so two chunks straddling a shoreline can never disagree about it by a
rounding difference.

**`StationTable`** (`vector/StationTable.kt:28`) holds per-vertex attributes (width, depth, floor
elevation, ...) with Catmull-Rom interpolation over arc-length. Two flavors: **open** (clamped ends, for
a `Polyline`) and **periodic** (wrapped neighbours, for a `Ring` — continuous *slope*, not just value, at
the seam). Every `Profiles`/`AreaProfiles` cross-section is a pure function of
`(distance, u, station, base)` with **no captured constructor state**, specifically so a future export
codec needs only a profile name plus a table it already has.

**Blend modes** (`vector/VectorFeature.kt:18-30`): `MIN` (carve — rivers, troughs, fjords), `MAX` (raise
— levees), `REPLACE` (overwrite — grading, road surface), `ADD` (pile on — moraines, alluvial fans).
`FeatureEvaluator` (`vector/FeatureEvaluator.kt:17`) applies every feature touching a column in
`(priority, id)` order, so a road follows the valley floor a river already cut.

**`FeatureIndex`** (`vector/FeatureIndex.kt:51`) is a uniform grid of feature-ID buckets, not an R-tree
— features are broadly uniform density and everything fits in RAM. Rebuilt after each vector-producing
stage, frozen once the world tier finishes. The measured hot spot is a town bucket holding up to 37% of a
world's features; the "overflow" list this was originally sized around is essentially always empty.

**Connection to the chunk tier**: `world.features.query(bounds)` is the only read path.
`GeneratedWorld.contentSlabsOf` (`pipeline/StandardWorld.kt:120-154`) and `ChunkMaterializer` both go
through it, and `FeatureEvaluator` is built fresh per chunk from the query result — so a viewer drawing
`outline()` can never diverge from what actually generates.

## The chunk tier (`voxel/`)

`ChunkMaterializer.materialize()` (`voxel/ChunkMaterializer.kt:165`) per chunk: one `features.query(...)`
(`:172`), builds per-chunk samplers (river/pond water, lava, ore veins, bridge decks, town structures,
cave network, `:173-181`), then `fillColumn` (`:470-717`) per column in fixed order (class KDoc
`:58-72`): water-surface contest → bare-rock/soil/blight → **basement fill** → **sedimentary beds**
(`Stratigraphy`) → **soil** → **surface cap** → **fluid/ice** → **ore substitution in place** (never eats
soil/cap) → **bridge decks** → **structures + caves**, additions written first, **removals (carving)
applied last**.

**Carving is subtraction, and it runs last.** `StructureSpans.remove()` (`voxel/TownStructures.kt:96`)
spells removal as `BlockType.AIR` — the same vocabulary `ChunkDelta.set(..., AIR)` uses for a player
breaking a block — so generation and player digging share one representation. Carving after every
addition is the only order with correct semantics: a mine shaft has to be able to pierce its own
masonry collar. The asymmetry to remember: a void's **floor** rounds by the fill rule (fractional,
matches how ground's own top voxel is treated) and its **ceiling** rounds by the centre rule (whole
voxel) — a fractional ceiling would read as a standable floor *inside solid rock* to `WalkableTile` and
`ColumnSummary`, so the cost (up to one voxel of head height) is the right trade.

Trees, crystals, and aetherite are **not written into the voxel grid** — `VegetationScatter`/
`CrystalScatter`/`AetheriteScatter` only feed `ChunkMaterializer.propsIn()` (`:250-273`), returning
`PropInstances` for a runtime to turn into entities. A billion trees per world costs nothing because
there's no per-tree storage anywhere; `trunkSite` still vetoes placement against streets/bridges/
buildings/caves so nothing spawns inside a wall.

`ChunkMaterializer.VERSION` (currently **1**) is a hand-incremented tier version — separate from any
`Stage.version` — folded into `WorldParams.chunkTierVersion`. It climbed to 10 across the branch that
built subtraction, vegetation, blighted cover, wounds, bare rock, lava, props and points of interest, and
was reset to 1 in this cleanup pass alongside every `Stage.version` (see Cross-cutting mechanisms above)
for the same reason: still pre-release, so none of those ten bumps had a real counterparty yet. The git
history holds the old changelog. Expect this number to climb again the moment a materialisation change
actually ships to a client — it is not a promise that the tier will never change again, only that nothing
has shipped to make the bumps so far mean anything.

### Encoding & storage (`voxel/`, `store/`)

- **`BlockType`** — 63 declared types, `id` explicit (never ordinal-derived) and **dense, 0-62**: fluids
  0-3, rock 4-8 (`GRANITE`, `BASALT`, `OBSIDIAN`, `STONE`, `LIMESTONE`), unconsolidated 9-12
  (incl. `MUD`), surface cover 13-15, blighted twins 16-18, worked stone 19-20 (`MASONRY`,
  `COBBLESTONE`), ore/gem 21-62 in fourteen contiguous grade-triples. The ids were sparse in bands of
  ten until the palette cleanup, which deleted the building materials (a building is a
  `PropKind.BUILDING` now, not voxels), folded four sedimentary rocks into `STONE` + `LIMESTONE`
  (limestone kept *only* because `Stratigraphy.SOLUBLE` gates every cave on it), folded `PEAT`/`CLAY`
  into `MUD`, and added four gems. **Every id now fits in six bits**, which `RleCodec`'s declined
  merged-run format was measured against back when it did not — worth re-measuring, and the headroom is
  two ids, not thirty.
- **`RleCodec`** (`voxel/RleCodec.kt`) — `VERSION = 2` (`:78`), the **one** version number that survived
  the stage-version reset, because it's a wire-protocol byte named `CHUNK_ENCODING_RLE_V2` in
  `chunk.proto`. Two separate run-length streams (blocks, then occupancy), not interleaved. A tighter
  merged-run format was measured (−24.5% bytes) and **declined** — the KDoc records the numbers rather
  than the aspiration.
- **`store/ChunkCache`** (`store/ChunkCache.kt:63`) — in-process LRU (`hot`, default 512) → chained
  `ChunkBlobStore` tiers → `generate` fallback. `ChunkKey` folds `(seed, pipelineVersion, chunk)`.
- **`store/ChunkStore`** (`store/ChunkStore.kt:51`) — `merged()` (base ⊕ delta, or a baked blob) is the
  *only* read path, enforcing server-authoritative geometry. `carve()` is batch-only and enforces
  occupancy-never-rises. **`bakedKeyOf` (`:288-289`) now folds in `pipelineVersion`** — a reversal of an
  earlier "deliberately omit it" decision, made because omitting it let a stale build's baked chunk read
  back indistinguishable from a fresh one. Any doc claiming otherwise is describing the prior state.
- **`store/VersionGate`** (`store/VersionGate.kt:73`) — three independently-diagnosable versions
  (`pipelineVersion`, `blockPaletteVersion`, `chunkFormatVersion = RleCodec.VERSION`); `check()` returns
  `Compatible` / `Incompatible(reason)` / `ServerAuthoritativeOnly`.

### Derived structures (`derived/`)

`ChunkDelta` (player edits, sparse), `ColumnSummary` (surface/water/shelter/void-ceiling heights),
`OpacityGrid` (4×-downsampled line-of-sight), `WalkableTile` (CSR standable-height spans), all owned by
`DerivedStore` with a rate-limited rebuild budget. **Only `WalkableTile` has a real gameplay reader
today** — `zone-server`'s local pathfinding queries it directly for 45°-rule walkability.
`OpacityGrid`/`ColumnSummary` remain unread outside `worldgen` itself; don't assume line-of-sight or
shelter logic exists just because the derived layer does.

**And that one reader fired for the first time only recently — habit 6, in this module's own store.**
Every accessor built on demand, `invalidate` early-returned when there was no entry, and the only
production callers guarded themselves on `isTracked` first: so `entries` stayed empty forever, the queue
`rebuild` drains was never filled, and `surfaceAt`/`canStep`/`isResident` answered null-or-false for the
whole life of every server process. Movement silently trusted the client's own z instead. `track(chunk)`
exists so residency can be *pushed in* by whoever knows which chunks matter — `zone-server` drives it off
`ChunkSubscriptionService.onFirstSubscriber`/`onLastSubscriber` — and a queued chunk with no entry is now a
first build rather than a skipped one. Count the output before believing the tests: all thirty passed.

## zone-server integration

The world tier is **regenerated at boot, never persisted** — rasters/features are a pure function of
`(seed, dimensions)`, so persisting them would only be persisting a cache. What players change (voxel
edits) is stored as deltas over that regenerated base.

- **`PersistedWorld`** (real JPA entity, `zone-server/.../world/PersistedWorld.kt`) — name, seed,
  dimensions, `wrapX`/`wrapY`, and the three version columns from `VersionGate` above, plus a
  `shapeVersion` hash over every `WorldConfig` field that decides terrain shape.
- **`WorldGenerationBootRunner`** — `@Order(1) CommandLineRunner`, runs before entity loading, the ECS
  loop, or the socket server, so nothing can observe a world that doesn't exist yet.
- **On-mismatch policy** (`zone-server/.../world/WorldService.kt`, `WorldGenConfig.OnMismatch`) — three
  distinct failure modes, not one check: the row can't rebuild its own config (**always fatal** — a
  `WorldConfig` field with no column means the row describes a *different* world silently), the pipeline
  moved (`REFUSE` default / `REGENERATE` / `IGNORE`), or the requested settings changed (warned always,
  acted on only by `REGENERATE`, since a running world quietly keeping its own dimensions is correct
  behaviour, not a bug).
- **Chunk streaming is fully wired** — `zone-server/.../world/stream/` (`ChunkService`,
  `ChunkStreamSystem`) runs `ChunkCache → ChunkStore → DerivedStore` on the tick thread. Protocol is
  announce-then-serve: `ChunkManifestSMSG` lists `(position, revision)` pairs for the view volume, the
  client requests only what it's missing (`ChunkRequestCMSG` → `ChunkDataSMSG`), edits fan out as
  `ChunkPatchSMSG`. `ChunkStaticEntitiesSMSG` streams the props from `propsIn()` above (trees, crystals,
  wound spires) to a dedicated client renderer.
- **Which vertical slabs get streamed is answered in two halves, and only one of them lives here.**
  `GeneratedWorld.contentSlabsOf` (`pipeline/StandardWorld.kt:121-155`) covers the generated half — the
  terrain heightfield span unioned with intersecting cave passages — and `ChunkService.computeSurfaceSlabs`
  unions it in alongside its own terrain span and the sea-surface pair. (An earlier version of this note
  said it did *not*, which was true and cost a player walking into a cave the ground in front of them; it
  is fixed.) The other half cannot live here at all: **`contentSlabsOf` is generation-only and knows
  nothing about `ChunkDelta`**, so a shaft a player carves through the floor of the lowest slab their
  column's ground reaches is content in a slab this function will never name. `ChunkService` keeps its own
  `editedSlabs` index for that, fed from the one funnel every content change passes through, and unions it
  over the cached generated set on read — which is why that cache still needs no invalidation path. The
  heightfield genuinely cannot express a hole; do not re-derive the offered set from it alone.
- **Master spawn points** — `civ/SettlementSpawnPoints.kt` (pure function, not a stage — it produces
  nothing the pipeline consumes) offers `MAX_HOME_CANDIDATES = 3` settlements, the 2nd/3rd/4th largest
  standing ones, never the obvious capital; `SpawnerStage` reads the same constant for its home safety
  ring, so the number lives in exactly one place. `zone-server`'s `MasterSpawnPointService` computes and
  **caches the rows in the DB once per world** — changing the count does not re-offer on an existing
  world until `WorldProvisioning.recreate` clears the table.

The client (`bestia-client/src/Game/World/ChunkEngine.cs`) mirrors the server's decode/palette/patch
version as `ChunkEngine.VERSION` — currently **1** on both sides, the same reset as `ChunkMaterializer
.VERSION` above but a *different* number in principle: one is wire/decode compatibility, the other is
terrain-generation compatibility, and they will diverge again the moment either changes without the
other. `Mesh/BlockAppearance.cs` mirrors the block palette by hand (not transmitted over the
wire) and meshes via surface-nets, sampling `Occupancy` at cell corners for exact partial-voxel
reconstruction. `StaticEntityRenderer.cs` renders `ChunkStaticEntitiesSMSG` batches with placeholder
meshes — functional, no art yet.

## Viewer / CLI tooling (`viewer/`, Gradle tasks)

| Task | Entry point | Purpose |
|---|---|---|
| `:worldgen:viewer` / `:worldgen:viewerExport` | `ViewerMain` | interactive Swing layer/feature/voxel inspector; export mode renders every layer to PNG (CI-safe) |
| `:worldgen:probe` | `ProbeMain` | prints a small window as text, one char per voxel column — fine enough to see individual river channels |
| `:worldgen:diff` | `DiffMain` | two worlds (two seeds *or* two params files, never both) → table of mean/worst difference per layer, loudest first |
| `:worldgen:bench` | `BenchMain` | serial vs. parallel world-tier build timing |
| `:worldgen:chronicle` | `ChronicleMain` | prints generated history: events, figures, artifacts, sites, `-Pyear` scrubbing |
| `:worldgen:town` | `TownMain` | inspects one settlement: layout, economy, history, `-Pwhy` precondition trace |
| `:worldgen:invariants` | `InvariantsMain` | seed-sweep regression: builds N worlds, runs every `Invariants` check, reports violations |
| `:worldgen:checkBoundaries` | — | build-time purity check (wired into `check`): no I/O import outside `viewer/` |

## The history log is a third world-tier product

`core/Chronicle.kt` is an append-only event store, and it genuinely is a different kind of thing from the
other two: a raster is addressed by position, a feature by position and kind, and an event by **year and
actor** — "what happened to this town", "who held this sword before it was buried", "which war produced
this ruin". None of those is a spatial query, and forcing them into the feature store would mean either a
marker per event (hundreds of thousands of zero-extent points no chunk will ever want) or losing the
causal links between them, which are the entire product. What *does* go in the feature store is the
physical residue — `RUIN`, `BATTLEFIELD`, `TOMB`, `MONUMENT` and the rest of `HistoryStage`'s output above
— because those are places with extent, and chunk generation has to know about them. `StageResult` carries
a `chronicle`, `StageOutput.History` exists as its own kind, and `GenContext.chronicle()` is scoped the
same way layers and features are: a stage that reads the log without declaring `HistoryStage` throws,
rather than quietly seeing an empty history and laying out a town with no walls.

**Names are seeds, not strings.** A station channel is a `Double`, so a `RUIN` marker can't hold the name
of the town it used to be — but it can hold a 48-bit integer, and that integer plus `history/Names.kt` *is*
the name. A settlement, its ruin, its tomb and every event about it all carry the name for eight bytes
rather than four copies of a string, and any tool can print any name with no lookup table. The cost: the
seed is derived from the entity and the world seed rather than from a counter, specifically so that
changing the naming logic is a cosmetic diff rather than a silent rewrite of every name in every existing
world — and it is still a change nobody should make once a world has shipped.

## Ground truth

`pipeline/StandardWorld.kt`, the `Stage` subclasses' own `dependencies`/`outputs`, and
`pipeline/Invariants.kt` are ground truth for anything this skill and this skill alone does not settle.
The module previously carried two long-form docs at the repo root that drifted out of sync with the code
by whole subsystems before being folded into this skill and into `bestia-docs`' server-side
world-generation page, then deleted — see the note at the top of this file. Don't recreate that failure
mode: a fact here that's wrong is worse than no fact, so when this skill and the source disagree, the
source wins, and the skill should be corrected in the same change that made it wrong.
