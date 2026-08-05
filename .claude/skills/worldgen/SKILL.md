---
name: worldgen
description: Explains the bestia-behemoth world generator (`worldgen/` Gradle module) — the 22-stage world-tier pipeline (tectonics/climate/hydrology/biomes/history/towns/...), the vector feature system (Ring/AreaFeature/PolylineFeature, blend modes, StationTable), the chunk/voxel tier (ChunkMaterializer, BlockType palette, RleCodec), the store/ caching+versioning tiers, and how it plugs into zone-server (PersistedWorld, boot-time version gate, chunk streaming) and the Godot client. Read this BEFORE touching any file under worldgen/, before adding/reordering a Stage, before changing a params class or a block type, or when investigating "why does this world look like X" / "how does a chunk get from the generator to the client". Triggers on: worldgen, Stage, GenContext, WorldGenPipeline, WorldParams, LayerId, FeatureKind, VectorFeature, Ring, AreaFeature, StationTable, ChunkMaterializer, VoxelChunk, BlockType, RleCodec, ChunkCache, ChunkStore, DerivedStore, PersistedWorld, WorldService, ChunkService, ChunkStreamSystem, pipelineVersion, paramsVersion, worldgen-architecture.md.
---

# worldgen: the world generator

`worldgen/` is a standalone Gradle module: pure Kotlin stdlib + JDK, **no Spring, no JPA, no I/O**
outside `viewer/` (enforced at build time by the `:worldgen:checkBoundaries` task — compile classpath
must be kotlin-stdlib only, no `java.io`/`java.nio`/`java.awt`/`javax` import outside `viewer/`). It is
a pure function: `(seed, WorldConfig, WorldParams) → GeneratedWorld`. `zone-server` depends on it and
owns the only real I/O (persistence, streaming); the Godot client only decodes what the server sends.

Two long-form docs sit at the repo root — `worldgen-architecture.md` (the original design doc) and,
until this skill replaced it, `SUMMARY.md` (a dev-log of one later branch). **Both are narrative history,
not current fact**, and are known stale in specific, verified ways — see [Where the docs lie](#where-the-docs-lie).
Treat `pipeline/StandardWorld.kt` and the actual `Stage` subclasses as ground truth; the docs are useful
only for the *reasoning* behind a decision, never for the current stage count, layer list, or version
numbers.

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

geo/ climate/ hydro/ bio/ resource/ civ/ history/ pop/ mana/ karst/ spawn/   ← the 22 Stages, see below

voxel/      BlockType palette, Stratigraphy, ChunkMaterializer, RleCodec, CaveNetwork, VegetationScatter
derived/    ChunkDelta (player edits), ColumnSummary, OpacityGrid, WalkableTile, DerivedStore
store/      ChunkCache -> ChunkStore -> DeflatedBlobStore, VersionGate
pipeline/   StandardWorld (assembles stages into a GeneratedWorld), Invariants (regression sweep)
viewer/     Swing tooling + CLI entry points; the only package allowed I/O
```

## The stage pipeline

`StandardWorld.stages()` (`pipeline/StandardWorld.kt:203-250`) constructs **22 stages** in one order,
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
| 22 | `NavGraphStage` | `civ/` | — | — (`StageOutput.Navigation`) | last: routes are read off roads/bridges/gates/cave mouths everything above placed; nothing reads it back |

`history` is the only stage declaring `StageOutput.History`; the pipeline requires there be at most one
(`core/WorldGenPipeline.kt:106-108`) — two stages writing one world's history isn't resolvable.

**The real execution order differs from the table above** because several stages declare dependencies
that outrank their construction position:

```
tectonics -> climate -> erosion -> glacial -> hydrology -> { alluvium, volcanism }
  -> { biomes, pond } -> { caves, mana, vegetation } -> resources -> habitability
  -> settlements -> history -> corruption -> { spawners, towns, vegetation_stands }
  -> economy -> nav_graph
```

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

**Versioning — three numbers, three questions:**

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

**`Invariants`** (`pipeline/Invariants.kt`, ~48 named checks) is the regression harness: `sweep()`
builds N worlds in parallel and runs every check over each — physics (discharge never decreases
downstream, lakes stand above their beds), history/chronicle self-consistency, civil structure (nothing
built in water, walled settlements have a gate), mana/corruption targeting, and existence checks for
every newer subsystem (`checkTheWorldHasSpawners`, `checkDistrictsHoldTheirBuildings`, ...). Several are
explicitly framed as tripwires against a subsystem that runs and silently produces nothing — the failure
mode this module has shipped more than once.

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

`ChunkMaterializer.VERSION` (`:886`, currently **9**) is a hand-incremented tier version — separate from
any `Stage.version` — folded into `WorldParams.chunkTierVersion`. It has climbed steadily (2 =
subtraction, 3 = vegetation lattice, 5 = blighted cover, 6 = wounds, 7 = no more raw `CLIFF` biome, 8 =
lava, 9 = trees/crystals become props instead of voxels) — a live signal that any doc claiming "reset to
1" is describing a past snapshot, not today.

### Encoding & storage (`voxel/`, `store/`)

- **`BlockType`** — 61 declared types, `id` explicit and permanent (never ordinal-derived), sparse by
  design: fluids 0-3, basement 10-12, sedimentary 20-23, unconsolidated 30-35, surface cover 40-42 +
  blighted 49-52, worked 60-67 (id 65 `PLANK` deliberately freed, not reused), ore/gem 100-129.
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
- **`GeneratedWorld.contentSlabsOf`** (`pipeline/StandardWorld.kt:120-154`) answers which vertical slabs
  hold anything worth streaming — the terrain heightfield span unioned with intersecting cave passages —
  but **`ChunkService.computeSurfaceSlabs` still only unions the terrain span**, not this. A cave forty
  metres down is generated and never streamed to a nearby player. This gap is real; check
  `ChunkService.kt` before assuming it's fixed.
- **Master spawn points** — `civ/SettlementSpawnPoints.kt` (pure function, not a stage — it produces
  nothing the pipeline consumes) ranks settlements second-largest-downward, never the obvious capital;
  `zone-server`'s `MasterSpawnPointService` computes and caches the result once per world.

The client (`bestia-client/src/Game/World/ChunkEngine.cs`) mirrors the server's decode/palette/patch
version as `ChunkEngine.VERSION` — currently **6** on both sides, a *different* number from
`ChunkMaterializer.VERSION` (9): one is wire/decode compatibility, the other is terrain-generation
compatibility. `Mesh/BlockAppearance.cs` mirrors the block palette by hand (not transmitted over the
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

## Where the docs lie

`worldgen-architecture.md` (repo root) says of itself that it's "kept as written rather than rewritten to
match the code" — deviations are meant to be recorded at the point in the code where they happen, not by
editing the doc. In practice it has fallen behind by whole subsystems, confirmed against current source:

- Its stage table/diagram says **"twelve world-tier stages"** and lists only
  `tectonics, climate, erosion, glacial, hydrology, biomes, resources, habitability, settlements, history,
  towns, economy`. The real count is **22** — missing entirely: `VolcanismStage`, `PondStage`,
  `AlluviumStage`, `VegetationStage`, `CaveStage`, `ManaStage`, `CorruptionStage`, `SpawnerStage`,
  `VegetationStandStage`, `NavGraphStage`. Its per-stage layer/feature table is missing every layer and
  feature those ten stages produce (`VOLCANISM`, `MANA_DENSITY`, `CORRUPTION`, `CANOPY_COVER`, `LAKE`,
  `CAVE_SYSTEM`, `BESTIA_SPAWN`, ...), and even its surviving rows are individually stale (tectonics is
  missing `HOTSPOT`, towns is missing `DISTRICT`, history lists 9 kinds where there are now 13).
- It claims baked chunks are "deliberately not keyed on pipeline version" and that derived structures and
  vegetation/props have no reader anywhere — all three have since reversed or been superseded (see
  [Encoding & storage](#encoding--storage-voxel-store) and [Derived structures](#derived-structures-derived)
  above).
- `SUMMARY.md` claimed `hydro/` "has no meander model at all" — false since `hydro/Meander.kt`'s
  `offset` was wired into `HydrologyStage`'s channel-shape profile (commit `75344ba4d`, "Rivers get a bank
  that is not a drawn line"). It's a dev-log of one branch's slice of work (params-as-data, caves,
  vegetation, the `Ring`/`AreaFeature` type, districts), not a description of the module's current scope
  — it never mentions history, mana, corruption, spawner, or nav-graph at all.

When in doubt, `pipeline/StandardWorld.kt`, the `Stage` subclasses' own `dependencies`/`outputs`, and
`pipeline/Invariants.kt` are ground truth. This skill is meant to replace both docs as the day-to-day
reference; use them only for archived design rationale, never for current stage count, layer/feature
lists, or version numbers.
