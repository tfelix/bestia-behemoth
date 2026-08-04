# worldgen: what is left

Handoff for the `world-gen` branch. **Phases 0–8 of the planned 8 are done and committed.** This file is now the
queue for what was never in that plan; [worldgen-architecture.md](worldgen-architecture.md) remains the design
*and* the ledger, and its Implementation Status section is true as of the last commit.

---

## How this module is worked on

Six habits, each of which exists because ignoring it cost a day. The first five were here before; the sixth was
earned across phases 3 to 7, four times over.

1. **Measure, do not reason.** Every constant this branch has moved was moved by printing a distribution and
   looking at it. Phase 7's droplet density was set twenty times too high by a plausible-sounding argument, and
   the measurement — 7% of cells pinned at the delta clamp — is what moved it.
2. **Look at the exported PNGs and the probe every phase.** Both geo bugs, both civ bugs, the six circular lakes,
   Phase 4's 50/50 checkerboard and Phase 6's flooded cloister were all found this way, with every test green.
3. **Confirm a regression test fails against the old code** before keeping it. The doc records two occasions
   where a first attempt passed against the bug it was written for; phases 3, 4, 5 and 7 each added another.
4. **Never pipe gradle into `tail`/`grep` and trust `$?`** — the pipe's exit status is the last command's. Write
   to a log file and check the exit code directly.
5. **An invariant that skips its subject reports success.** This module spent a year with zero lakes while
   `lakes stand above their beds` passed, because it opens with `if (lakes[x,y] == 0) continue`.
6. **A subsystem that is complete, tested and never *reached* looks exactly like one that works.** Three phases
   in a row shipped dead: sea lanes produced none on forty worlds (the port search looked 3 km for a harbour),
   the four built sites produced none on any world (`year - Int.MIN_VALUE` overflowed the interval gate), and the
   seasonal fields had been summed and discarded for a year. **Count the output before believing the tests.**
   Where existence cannot be asserted per seed — most worlds legitimately have no sea lane — pin it to a seed
   that does, rather than writing a conditional check that passes vacuously.

### Verification recipe

Run all of it per phase, in this order. The last three catch what the first misses.

```
./gradlew :worldgen:test                                   # 545 tests at present
./gradlew :worldgen:invariants -Pseeds=200 -Pcells=192      # sweep; watch the reported spreads
./gradlew :worldgen:invariants -Pseeds=200 -Pcells=256
./gradlew :worldgen:viewerExport -Pout=build/viewer         # PNGs + the SeamCheck line; works headless
./gradlew :worldgen:viewerExport -Pgenesis -Pout=build/gen  # the 128 km world zone-server actually boots
./gradlew :worldgen:probe -Pchannels=1                      # river cross-sections against the voxel grid
./gradlew :worldgen:probe -Pon=fort -Pnth=0                 # a built site at voxel scale
./gradlew :worldgen:probe -Pon=mine -Psection -Pbelow=30    # a vertical slice - the only view of a hole
./gradlew :worldgen:probe -Pon=cave_passage -Psection -Pbelow=70   # ...and a gallery, 45 m down
./gradlew :worldgen:probe -Px=91500 -Py=36500 -Psection -Pbelow=2 -Pgenesis  # a tree, in section
./gradlew :worldgen:probe -Pdroplets                        # chunk-scale droplet erosion, which ships off (cost)
./gradlew :worldgen:diff -Pseed=7 -Pother=8                 # two worlds, layer by layer
./gradlew :worldgen:town -Pcensus                           # every settlement in one table
./gradlew :worldgen:chronicle -Pquests                      # unresolved history threads
./gradlew :zone-server:test
```

- `SeamCheck: clean - 64 chunks, 3584 shared columns agree` must appear on **every** export, and so must
  `VoxelSeamCheck: clean`, which is the same promise one tier down: `SeamCheck` compares `Double` heights on a
  single `z`, so it cannot see a carve that leaves occupancy behind or a sampler cache that is not thread safe.
  Check its `not air` count too - a voxel check over open sky is clean and worthless.
- Run at **both 128 km and 512 km**, and sweep at least 200 seeds. Phases 6 and 7 each found a real defect only
  above 120 seeds: two latent "built in water" bugs at 30×256, and a `Polyline` precondition 113 worlds into a
  200-seed run.
- **The 6 known `zone-server` failures and the flaky 7th are fixed** (2026-08-04). The 6 —
  `AiBehaviorScenarioTest` ×3, `AiLifecycleE2ETest`, `AiProfileRegistryTest`,
  `ZoneEngineTest > destroying an entity with no synced component sends no vanish` — all traced back to one bug:
  `AiProfileRegistry.load()` resolved `classpath:ai/*.yml` with the singular `classpath:` prefix, which only globs
  inside the *first* `ai/` directory the classloader finds. Once the goap2 fixtures added
  `zone-server/src/test/resources/ai/goap2/wolf.yml`, that test-resources `ai/` root (no top-level `*.yml` of its
  own) could win the lookup ahead of `src/main/resources/ai/`, silently loading zero profiles instead of throwing.
  Fixed by switching to `classpath*:`, which aggregates every matching root. `ZoneEngineTest`'s failure was
  separate and pre-dated that: the "no synced component" case gave the entity a bare `Position`, which is itself
  `Dirtyable` (always `PublicInRange`) — contradicting its own premise. Fixed by giving it no components at all.
  The flaky 7th, `ChunkStreamingScenario > a chunk that was never offered is not served`, was the
  `Awaitility.untilAsserted`-proving-a-negative bug described above — fixed with settle-then-check: queue a second,
  legitimately-offered request right behind the bad one, wait for *that* response (proving the bad one's fate is
  already decided), then assert once rather than retrying "nothing arrived" until timeout.

### Things that bite

- **`version` is for code, `paramsVersion` is for values, and they must not be confused.** Every params class
  fingerprints itself (`ParamsDigest`), each stage folds its own into the abstract `Stage.paramsVersion`, and
  `WorldGenPipeline` folds that into the version vector — so retuning `oceanicShare` now invalidates tectonics
  and everything downstream, reaches `pipelineVersion` and the chunk cache key, and leaves the stages *above*
  it alone. When a pin in `ParamsVersionTest` fails because you moved a number, **re-pin it and do not also bump
  the stage `version`**: that reshuffles every RNG stream below the stage and changes which seeds show a latent
  bug, for nothing. The digest deliberately never reaches `GenRng.derive`, so tuning a value lets you look at
  the same world.
- **A tunable must be a primary-constructor property**, or neither the digest nor the `toString`-based
  completeness oracle can see it. A `val` in the class body is invisible to both and silently unhashed.
- **The chunk tier is not a stage and is easy to forget.** `DetailParams`, `StrataParams` and `DropletParams`
  reach `pipelineVersion` only through `WorldGenPipeline`'s `chunkTierVersion` constructor argument, which
  `StandardWorld.pipeline` supplies from `WorldParams`. Add a chunk-tier tunable and it is `WorldParams`, not a
  stage, that has to fold it.
- **Four numbers are read by two stages each and are forwarded, not duplicated.** `WorldParams.resolved` copies
  the ocean margin from tectonics into erosion, the habitability terms into settlement, and the settlement
  grading and chunk detail noise into town. Those four have **no params-file keys at all** — the parser reports
  them as unknown — because a file key would be applied and then overwritten. Setting one by hand in code
  instead is how buildings end up floating over the ground everywhere.
- **Bump the stage `version`** for any behaviour change. **Every stage is at 1.** They were reset in one commit
  once the module's feature work landed, because each accumulated bump was a compatibility statement made to a
  world that only this repository's own tests had ever generated. The rule is unchanged and the counting starts
  again from the first world that outlives a branch; `ChunkEngine.VERSION` was reset with them, and
  `RleCodec.VERSION` deliberately **was not** - it is a byte in every payload and an enum name in
  `chunk.proto`, so it is a real statement between two artefacts. The chunk tier has one too now — **`ChunkMaterializer.VERSION`, currently 3** — folded into
  `chunkTierVersion` and therefore
  into `pipelineVersion`. It exists because changing the materialisation *code* used to move no number at all:
  subtraction changed every mine head in every world while `pipelineVersion` held still, so every cached chunk
  still looked valid. Bump it whenever a column materialises into something different.
- **Any version bump reshuffles every RNG stream below it**, which changes *which seeds* show a latent bug. Phase
  6 bumped `HistoryStage` and two pre-existing "built in water" bugs appeared; confirmed pre-existing by bumping
  only the version number at the previous commit and reproducing the same seeds and feature ids. **When a sweep
  fails after a version bump, test that before assuming the new code did it.**
- **`verifyOutputs` demands declared outputs equal produced outputs exactly.**
- **Dependency scoping is transitive**, and reads are denied if undeclared.
- **`StandardWorldTest` pins the exact topological order.**
- **`MapRenderer.colorOf` is an exhaustive `when` over `FeatureKind` with no `else`** — a new kind is a compile
  error until the viewer draws it. It caught all five kinds added on this branch.
- **An exhaustive `when` with an `else` is not exhaustive.** `TownStructures` had `else -> SiteKind.MONUMENT`,
  which would have materialised every new site kind as a stone obelisk without a compiler murmur. Ten more were
  swept in the palette pass and are now exhaustive with every case spelled out: `SurfaceCover.soil` and `.cap`
  over `Biome`, the grazing and movement-cost tables in `civ/Terms`, `EconomyStage`'s grazing, wage pull and
  urban-share tables, `TownBuildings`' three tables over `BuildingFunction`, and `ResourceStage`'s two surface
  tables - the last two of which now *throw* on an ore, because an ore never reaches them and the `else` was
  quietly ready to bury a new surface resource under a hundred and fifty metres of rock. What was **left alone**
  is the other genuine shape: an `else -> Unit` in a loop that dispatches over `FeatureKind` and handles three of
  forty is a real default, not a workaround. Judge, do not sweep blindly.
- **`Biome.of` is gone**, and its absence is the point. It was `entries[ordinal.coerceIn(...)]`, so the
  `NO_SECONDARY = -1` sentinel read back as a real biome and a cell with no runner-up confidently claimed to be
  one. Prose warnings told callers to use `entries.getOrNull` instead, which is a rule somebody has to remember;
  deleting the function is the version of that rule the compiler keeps. Read a `BIOME` ordinal with
  `Biome.entries[v]`, which throws; read anything that may carry a sentinel with `getOrNull` and handle the null.
  The same trap still exists for `SiteChannels.RESOURCE`.
- **A removal is a span made of `AIR`, and `add(AIR)` throws.** That is the same vocabulary
  `ChunkDelta.set(x, y, z, AIR)` uses for a player breaking a block. The *rejected* alternative is why
  `to <= from` is still a silent drop rather than meaning "remove": two elevations the wrong way round is a
  routine slip, and it must build nothing rather than dig a hole in a hillside.
- **A void's floor rounds by the fill rule and its ceiling by the centre rule, and swapping them is a subtle
  disaster.** Occupancy is read as fill-from-below by every derived structure, so a *fractional ceiling* is a
  phantom standable surface inside solid rock — `WalkableTile` would path across it. The floor keeps its
  fraction because that is what occupancy is for. Cost of the asymmetry: up to a voxel of head height.
- **A cave passage's floor sits a sixth of the way up its bed, never on the boundary.** `bedIndexAt` of a bed's
  own bottom face is that bed only if the division comes out exactly, and in floating point it lands one bed
  lower about as often as not - so a facies check on the floor tests the rock *under* the passage. Three
  separate bugs came out of the same family and each needed the invariant to find it: the floor below the
  basement (granite walls, 37 seeds in 40), the void's top leaving a bed the floor was still in (25 in 40), and
  a bicubic-versus-bilinear disagreement putting a cave mouth two metres under the sea (1 in 40).
- **A void that stops at the ground surface has a lid on it.** The ceiling rounds down, so the voxel the
  surface falls inside survives. A hole meant to reach the open air needs a ceiling more than one voxel above
  the terrain — see `MineHead.SHAFT_HEADROOM`.
- **`ChunkMaterializer` carves last, in a second pass over the span buffer.** A hole is defined by the material
  it is a hole in, so every addition must land first; and applying removals as their own pass makes the result
  independent of the order two producers happened to author them in. Nothing may carve under standing water,
  and that veto lives at the call site so the next producer cannot forget it.
- **A smooth probability sampled per column is still a coin flip per column.** Vegetation needs three things
  and the third is the one that cannot be skipped: a smooth field, a cutoff in it, **and a decision unit
  larger than a voxel**. `VegetationScatter` hashes a four-metre lattice for exactly that reason, and
  `VegetationTest` measures the canopy run length against a per-column-hash control built inside the test -
  because "runs average 5 m" means nothing until you know the broken version averages 1.5.
- **`Biome.litter` is a fertility term and is not a measure of trees.** Grassland is one of the best litter
  producers in the table and is almost treeless; the two differ by a factor of fourteen. The plan for this
  phase said to reuse `litter`, and doing so would have put a wood on every prairie at four fifths of a
  temperate forest's density. `Biome.canopy` is the separate scalar, and `Biomes.catalogueDigest` now folds
  both - until it did, retuning either moved no version number at all.
- **A crown hangs from the ground under its own trunk, never from the ground under the column being drawn.**
  The difference is invisible on flat ground and drapes every tree over its hillside on a slope. Avoiding it
  is what costs `ChunkColumnSource.heights` a halo: a trunk just outside a chunk needs a column belonging to
  the neighbour. The halo is requested only when a chunk actually has candidate trunks, which is why most of
  the world does not pay for it.
- **A sub-sampled raster of a fine field is a picture of its own sampling.** `CANOPY_COVER` averages a 140 m
  patch field and a 14 m biome dither over a kilometre cell; at four samples per axis the exported map was
  visibly grainy on the cell grid, and at one site sample per cell every ecotone was a spray of loose pixels.
  Both were sampling error rather than world structure, and both were found by looking at the PNG.
- **A ring built by sampling a radius can only ever be star-shaped.** `Ring.crescent`'s first version marched
  outward from a centre and produced something crescent-*looking* whose boundary a radial type could have drawn -
  which would have made the whole vertex ring unnecessary and wrong. `RingTest` catches it by counting how many
  times a ray from the centroid crosses the boundary; a shape that never manages more than one has not earned
  the type.
- **A guard in front of an exact decision has to be exact too.** `Ring.contains` counts crossings in fixed point
  and used to pre-reject with a bounding-box test on the raw doubles. Near a vertex, where the box edge and the
  boundary touch, that is a float branch two chunks can take differently - so the function as a whole was not a
  pure function of the quantised position, whatever the loop below it did. It was found by breaking the *edge
  rule* during the fails-first pass and watching the *quantisation* test go red instead.
- **A centroid computed with the wrong winding is reflected through the origin.** `Ring` normalises its vertices
  counter-clockwise and then passed the *pre-normalisation* signed area to the centroid formula, so any
  clockwise caller got a centroid hundreds of kilometres outside the world - and since `contains` rejects
  against a disc centred on it, containment answered false everywhere. Every test in the file used a
  counter-clockwise fixture and passed. What found it was the first producer whose shapes came out clockwise,
  reporting **no ponds anywhere on any world**.
- **Shrinking a feature never fixes a boundary artefact; it causes one.** A vector lake's extent is its ring, so
  a column outside the ring gets no water however low the ground there is. Tapering the pond's ends to close
  them more tidily pulled the boundary *inside* the waterline and took the shore invariant from 8 violations to
  576. The fix was the opposite: draw the ring past the waterline, onto ground already above the surface.
- **A basin cannot be derived from a corridor.** Three versions of the moraine-dammed pond set its water level
  from the dam - "the water backs up to some fraction of the moraine's height" - and all three left walls of
  standing water up to seventy metres tall. A glacial trough is overdeepened along its length, its stated wall
  height describes the carve rather than the surrounding land, and a `MIN` blend makes even that an upper bound
  on the real ground. What works is filling from the lowest point and stopping just before the water finds a way
  out, which makes a wall impossible by construction rather than by a threshold.
- **`queryStrict` is for narrow queries, not world-wide ones.** It returns everything and throws if any of it
  came from an undeclared stage, which is right when a surprise means a bug and wrong when the query asks for
  the whole region - there it trips on every stage that happens to sort before yours.
- **A prediction of the ground that reads the raster is wrong wherever a feature is additive.** `TownStage`
  decides a building's floor from `WorldGround`, whose base is the `ELEVATION` layer, and used to stamp only the
  `SETTLEMENT_GRADING` discs on top of it. But `GlacialStage.carveInto` rasterises only its `MIN` carves - so a
  `MORAINE`, an `ALLUVIAL_FAN` and a `DELTA` exist in the *chunk* and not in the layer, and a town on a moraine
  had ten metres of ridge under it that nothing at the world tier could see. `standsLevel` approved the lot and
  the house came out buried. The fix is to evaluate **every** `affectsHeight` feature, which meant declaring
  glacial, alluvium and pond as `TownStage` dependencies - they are read for their features, not their layers.
  Anything else that predicts a surface has the same hole.
- **The version reset is the argument for doing version resets.** The moraine bug had been latent for as long as
  moraines have existed and no seed in a 200-seed sweep had put a settlement on one. Resetting every stage
  `version` reshuffled every RNG stream at once, and seed 909 - a *unit test's* world, not a sweep's - landed a
  town on one immediately. Expect a batch of failures after a reset, and separate the two kinds: an existence
  pin on a seed that no longer has the thing gets re-pinned, and an *invariant* gets investigated.
- **Baked chunk blobs are keyed on `pipelineVersion` now.** They used to leave it out deliberately, so that a
  baked chunk would survive a generator change - which is what makes baking a migration path, and is exactly
  wrong during development, where it means terrain from two builds sits in one store indistinguishably. When
  there is something to migrate, the answer is a re-key step that reads at the old version and writes at the
  new, not a hash that is incomplete on purpose.
- **`project.hasProperty('x')` in a Gradle build is true for the name of any `Project` getter.** `-Pdepth`
  silently arrived as the project's nesting level, and `-Partifacts` on `chronicle` had been *permanently on*.
  Every switch in `worldgen/build.gradle` now goes through the `cli` helper, which reads
  `startParameter.projectProperties` and nothing else. Add a flag with any other mechanism and it may already
  mean something.

---

## What is left

Nothing from the original 8-phase plan. What follows is the architecture document's *Still missing* list, minus
what phases 3–7 closed, grouped by what it would take. Each is argued where it belongs rather than here.

### Wants a subsystem

- ~~**The polygon geometry type**~~ — built. `vector/Ring.kt` is a simple closed polygon with **exact integer
  containment** (the crossing number in fixed point, no epsilon anywhere, so two chunks decide a shoreline
  identically), and `vector/AreaFeature.kt` is the feature around it. `StationTable` gained a `periodic` flag so
  a boundary's attributes are smooth across the seam rather than merely continuous. Four of the six dead kinds
  now have producers: `LAKE` and `OXBOW_LAKE` from `hydro/PondStage`, `ALLUVIAL_FAN` and `DELTA` from
  `hydro/AlluviumStage`.

  Two of the six turn out **not to be polygons at all**, and both are closed rather than pending. `COASTLINE` is
  a boundary *curve* - everything wanting it wants distance to a curve, which a `BoundaryTracer` plus a
  `MarkerFeature` delivers with no new geometry. `ROAD_JUNCTION` was measured during the calibration batch and
  the crease it was for does not exist (see the struck-through entry further down); it stays unemitted by
  decision.

  Still not built: **settlement outlines**. `SettlementStage.gradingFor` is still a `PointFeature` disc, so a
  town in a river bend is levelled in the shape of a circle. The type is there and the producer is a shape swap;
  what stopped it was budget, not design. Also still absent: clipping, offsetting and any operation over *two*
  areas, because no producer has wanted one.
- ~~**Caves.**~~ Built: `karst/CaveStage` places them, `voxel/CaveNetwork` cuts them, and `HistorySim` hides
  hoards in them. What is *not* built is the streaming half — `GeneratedWorld.contentSlabsOf` says which slabs
  hold a passage and `ChunkService` still subscribes only the surface ones, so a cave below the terrain slab is
  generated and never sent.
  - **`CaveNetwork` has no fails-first unit test**, which is habit 3 unpaid. The world-tier claims are covered
    by four invariants over 400 seeds and the geometry is visible in `probe -Pon=cave_passage -Psection`, but
    the chunk-tier half — the roof clamp against the column's actual `top`, the mouth exemption, the pinch-out
    when the clamp falls below the floor, the two fbm wall fields — is pinned by nothing. It wants a
    `SubtractionTest`-shaped file: a synthetic world, one hand-built passage, and each guard broken in turn to
    confirm the test goes red. Not critical; deferred deliberately rather than overlooked.
- ~~**The scatter pass.**~~ Built: `LOG` and `LEAVES` are in the palette, `voxel/VegetationScatter` decides
  where a tree stands, `bio/VegetationStage` rasterises `CANOPY_COVER` from the same function, and
  `ResourceStage`'s timber suitability reads the layer. Trees are **implicit** — a billion of them per world,
  so there is no feature, no marker and no per-tree storage anywhere, only a function of position. The
  architecture document's "chunk-seeded randomness is safe here" permission is *still* unused and vegetation is
  the case that shows why it should stay unused: a four-metre canopy spans columns, so a chunk-seeded tree at a
  border is half a tree. What is **not** built is anything that turns a tree into an entity — a harvestable
  tree, a stump, a felled log — which would read `GeneratedWorld.vegetation` the way a treasure spawner will
  read `CAVE_HOARD`.
- **Live NPCs.** `pop/` produces the substrate; nothing makes a person walk to the market.
- **Delta persistence** (step 12) and a **client-side base generator** (step 13).
- **Sharding, the work queue and the gRPC surface** — deliberately the server's, and built nowhere.

### Wants a stage or a pass

- **The precipitation seasonality concentration index is now rejected rather than deferred**, and the
  measurement is in `ClimateStage.seasonality`. It is the right definition in general and buys nothing here: the
  annual cycle is one sine, so min-max over four seasons is the same quantity as over two. The repair that would
  have made it free — treat the new index as the old axis in different units and divide it back through
  `BiomeAxisRanges` — does not exist, because the ratio between the two definitions is **not a constant**:
  measured over two world sizes it runs 0.333 (the analytic floor for a phase-aligned sinusoid) to 0.72, median
  0.41. The definitions order cells differently, so adopting it is a fresh calibration of the classifier, not a
  rescale. Revisit if the seasonal cycle ever gains a second harmonic.
- **Droplet erosion still ships off, and the reason changed from seams to cost.** The seam claim is checked
  (`ChunkSeamTest` plus `SeamCheck: clean` with it on). What blocks the default is throughput in the *tools*:
  turning it on took one 128-cell `viewerExport` from 114 s to over twenty minutes unfinished, and
  `:worldgen:test` from ~2.5 min to over 25. The cause is `viewer/ScalarField.kt` evaluating `heightAt` per
  rendered pixel, so a whole-world render wants ~10⁶ droplet tiles against a 512-tile cache — an access pattern
  chunk streaming does not have. **Fix that field first**, then the default is free. Reachable meanwhile via
  `probe --droplets` or `droplets.enabled = true` in a params file.
- ~~**Road-junction and trough-tributary smoothing**~~ — **both halves measured and neither is the defect it was
  filed as.** Kept as an entry because the premise is plausible enough to be re-derived by the next reader.
  - *Roads* blend with `REPLACE`, not `MIN`, so two roads meeting never `min` against each other and there is no
    crease of the `RIVER_CONFLUENCE` kind. Any artefact is a priority *step* between overlapping carriageways —
    a different defect, unmeasured. See `LinearFeatures.road`.
  - *Troughs* are traced source-to-snout exactly as rivers are and `donors[cell] >= 2` does mark junctions, but
    the floors merge at the same level (so `min` is a no-op there) and the wedge above them is a **spur**, which
    the stage already claims as a feature. Curvature through the five junctions of the reference world was no
    rougher than control transects 1 200 m away, and smoother in four of five. See `GlacialStage.extract`.
  - A bowl at a trough junction would also have had to avoid erasing **hanging valleys**, which exist because a
    tributary's floor is a running minimum over its own path and therefore sits above the trunk's.
- **The place → route → regrow → replace settlement iteration** — single pass.
- **Oil and gas** — skipped because nothing downstream consumes them.
- **Town blocks as objects** (deviation 8), **building interiors**, and a **shape grammar** that reads the
  grammar seed every building already carries.
- **History**: deities and monsters as entities, technology as more than a scalar, event templates with pre- and
  postconditions.

### Wants only the work

- **Data-driven configuration, three classes in.** The format exists (`core/ParamsText.kt`, flat dotted keys with
  line numbers, duplicate detection and nearest-key suggestions) and `--params` reaches every offline tool.
  `TectonicsParams`, `ClimateParams`, `ErosionParams` and `ClosedBasinParams` load; the remaining twelve prefixes
  are listed in `WorldParams.NOT_YET_LOADABLE`, which is what makes a key under one of them say *"cannot be set
  from a file yet"* rather than *"not a tunable"*. Writing a loader is a `copy(...)` per class plus deleting its
  prefix from that set — `WorldParamsLoadTest` then asserts the loader and the digest cover the same fields.
  The **catalogues** are a separate problem and still want a data file: `BusinessCatalogue`, the biome prototypes,
  `Culture.ALL`, `Names.STYLES`. They are lists rather than field sets, so the flat format does not fit them and
  their only guard is a pinned digest.
- **Nothing in zone-server can point at a params file.** `WorldGenConfig.params` is the one place the server's
  tuning lives and it is hard-coded to the defaults. Making it configurable needs a decision about what happens
  when the file changes under a live world, which is `on-mismatch` territory rather than generator work.
- **Derived structures have no readers.** Walkable tiles, the opacity grid and column summaries are kept fresh on
  a per-tick budget, and movement validation, line of sight and pathing still do not consult them. **Cheap and
  already paid for every tick** — the best value on this list.
- **`WorldWrap` has three callers.** Chunk streaming, spawn-point selection and now the sea-lane cost field
  normalise; movement, interest management and pathing use naive subtraction, so two players ten metres apart
  across the seam read as a world apart. Real bug, outside worldgen.
- **`zone-server/navigation/`** is a separate unused 2.5D nav stack, superseded and untouched.
- **No disk or object-store cache tier.** **No region tier.**
- **Client rendering**: no textures, no LOD, no blocky pass for player-placed voxels.
- **Tooling**: no seed diffing, and no interactive inspector for a clicked river reach.

### An open question rather than a gap

River counts going 512 → 1024 km gave 7.2×, 3.5× and 6.1× for 4× the area across three seeds. Against a 44–207
spread at a single size, three samples cannot separate mild superlinearity from noise. If it matters, it wants a
dozen seeds per size.
