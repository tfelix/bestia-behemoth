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
./gradlew :worldgen:test                                   # 405 tests at present
./gradlew :worldgen:invariants -Pseeds=200 -Pcells=192      # sweep; watch the reported spreads
./gradlew :worldgen:invariants -Pseeds=200 -Pcells=256
./gradlew :worldgen:viewerExport -Pout=build/viewer         # PNGs + the SeamCheck line; works headless
./gradlew :worldgen:viewerExport -Pgenesis -Pout=build/gen  # the 128 km world zone-server actually boots
./gradlew :worldgen:probe -Pchannels=1                      # river cross-sections against the voxel grid
./gradlew :worldgen:probe -Pon=fort -Pnth=0                 # a built site at voxel scale
./gradlew :worldgen:probe -Pdroplets                        # chunk-scale droplet erosion, which ships off
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

- **Stage params are not persisted.** They are compile-time defaults folded into `pipelineVersion`, so *any*
  change to them shifts terrain for an existing world and `worldgen.on-mismatch: REFUSE` stops the server.
- **Bump the stage `version`** for any behaviour change. Current: tectonics 4, climate 3, erosion 4, glacial 2,
  hydrology 3, biome 2, resource 1, habitability 1, settlement 3, town 4, history 2, economy 1.
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
- **The scatter pass.** No vegetation, so the "chunk-seeded randomness is safe here" rule still has no users.
- **Live NPCs.** `pop/` produces the substrate; nothing makes a person walk to the market.
- **Delta persistence** (step 12) and a **client-side base generator** (step 13).
- **Sharding, the work queue and the gRPC surface** — deliberately the server's, and built nowhere.

### Wants a stage or a pass

- **Recalibrate `BIOME_CONFIDENCE`.** Phase 4 found it is not usable as a blend weight — median 0.066 over cells
  that have a runner-up, because nearest and second-nearest distances concentrate in seven dimensions. A
  percentile rank would fix it and would move every biome boundary, so it belongs in a change that measures
  biomes. The same argument deferred the **precipitation seasonality concentration index** in phase 3: it is the
  right definition, it is systematically lower at four seasons, and adopting it moved green land 64% → 68% and
  deleted tropical seasonal forest from the world.
- **Turn droplet erosion on**, once somebody has looked at enough worlds to want it. The blend is seam-free and
  tested; the default is a judgement about risk, not about correctness.
- **Road-junction and trough-tributary smoothing** — the two places a `min` of two profiles still creases.
- **The place → route → regrow → replace settlement iteration** — single pass.
- **Oil and gas** — skipped because nothing downstream consumes them.
- **Town blocks as objects** (deviation 8), **building interiors**, and a **shape grammar** that reads the
  grammar seed every building already carries.
- **History**: deities and monsters as entities, technology as more than a scalar, event templates with pre- and
  postconditions.

### Wants only the work

- **Data-driven configuration.** Every tunable is a Kotlin `data class`. The new `SpecialSites` thresholds and
  `DropletParams` join `BusinessCatalogue` and the biome prototypes in wanting a data file.
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
