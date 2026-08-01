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
./gradlew :worldgen:test                                   # 408 tests at present
./gradlew :worldgen:invariants -Pseeds=200 -Pcells=192      # sweep; watch the reported spreads
./gradlew :worldgen:invariants -Pseeds=200 -Pcells=256
./gradlew :worldgen:viewerExport -Pout=build/viewer         # PNGs + the SeamCheck line; works headless
./gradlew :worldgen:viewerExport -Pgenesis -Pout=build/gen  # the 128 km world zone-server actually boots
./gradlew :worldgen:probe -Pchannels=1                      # river cross-sections against the voxel grid
./gradlew :worldgen:probe -Pon=fort -Pnth=0                 # a built site at voxel scale
./gradlew :worldgen:probe -Pdroplets                        # chunk-scale droplet erosion, which ships off (cost)
./gradlew :worldgen:town -Pcensus                           # every settlement in one table
./gradlew :worldgen:chronicle -Pquests                      # unresolved history threads
./gradlew :zone-server:test
```

- `SeamCheck: clean - 64 chunks, 3584 shared columns agree` must appear on **every** export.
- Run at **both 128 km and 512 km**, and sweep at least 200 seeds. Phases 6 and 7 each found a real defect only
  above 120 seeds: two latent "built in water" bugs at 30×256, and a `Polyline` precondition 113 worlds into a
  200-seed run.
- **`zone-server` has 6 known failures** at HEAD — `AiBehaviorScenarioTest` ×3, `AiLifecycleE2ETest`,
  `AiProfileRegistryTest`, and `ZoneEngineTest > destroying an entity with no synced component sends no vanish`.
  A **7th is flaky**: `ChunkStreamingScenario > a chunk that was never offered is not served` fails about one run
  in three. It proves a *negative* with `Awaitility.untilAsserted`, which retries until the assertion passes — so
  a legitimately-offered chunk's `ChunkDataSMSG` arriving late after `clearMessages()` makes it fail on every
  retry until timeout. `untilAsserted` cannot express "nothing arrived"; that needs settle-then-check. **Real bug,
  in the test, outside worldgen.**

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
- **Bump the stage `version`** for any behaviour change. Current: tectonics 4, climate 3, erosion 5, glacial 2,
  hydrology 3, biome 2, resource 1, habitability 1, settlement 3, town 5, history 2, economy 1.
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
  which would have materialised every new site kind as a stone obelisk without a compiler murmur. Grep for
  `else ->` over a domain enum before trusting the compiler to find your new case.
- **`Biome.of(ordinal)` *coerces* into range**, so a `-1` sentinel silently reads as the last enum entry. Use
  `entries.getOrNull`. The same trap now exists for `BIOME_SECONDARY` and `SiteChannels.RESOURCE`.
- **Nothing in the voxel tier can subtract.** `StructureSpans` adds spans and cannot remove them, so a hole is
  not expressible — which is why a mine head is a planked shaft cover and why caves are a change to the tier
  rather than a pass.

---

## What is left

Nothing from the original 8-phase plan. What follows is the architecture document's *Still missing* list, minus
what phases 3–7 closed, grouped by what it would take. Each is argued where it belongs rather than here.

### Wants a subsystem

- **The polygon geometry type** — the root of deviations 2, 3 and 5. Alluvial fans, deltas, lakes, coastlines and
  settlement footprints all want an area and have none. `COASTLINE`, `ALLUVIAL_FAN`, `DELTA`, `LAKE`,
  `OXBOW_LAKE` and `ROAD_JUNCTION` are declared feature kinds nothing emits. A subsystem, not a pass.
- **Caves**, which need voxel subtraction first — see *Things that bite*. The client's surface-nets mesher already
  handles them, so the renderer is ahead of the generator here.
- **The scatter pass.** No vegetation — and the block palette has no vegetation *material* either, so this is a
  palette change before it is a pass. The "chunk-seeded randomness is safe here" rule still has no users, but
  world-position-hashed scatter does: `TownStructures.ruinColumn` hashes the quantised world position for rubble,
  which is the pattern a vegetation pass should copy rather than the chunk-seeded permission the doc grants.
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
