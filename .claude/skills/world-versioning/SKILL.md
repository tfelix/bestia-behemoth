---
name: world-versioning
description: How a generated world is versioned, how zone-server decides at boot that the stored world no longer matches the running build, and — the common case in development — how to throw a world away and start over. Read this BEFORE bumping a Stage.version / ChunkMaterializer.VERSION / RleCodec.VERSION, before adding a WorldConfig field, when a boot fails with WORLD_PIPELINE_MISMATCH / WORLD_RECORD_INCOMPLETE, or when the world/map needs resetting, wiping, regenerating or reseeding. Triggers on: pipelineVersion, blockPaletteVersion, chunkFormatVersion, shapeVersion, chunkTierVersion, paramsVersion, VersionGate, PipelineVersion, on-mismatch, onMismatch, REGENERATE, REFUSE, IncompatibleWorldException, IncompleteWorldRecordException, WorldProvisioning, recreate, reset the world, wipe the map, regenerate the world, new seed, stale chunks, world row.
---

# World versioning, and how to reset the world

Two halves that are easy to confuse:

- **The version numbers** are computed in `worldgen/` and are pure functions of code + tunables.
  They answer "would this build generate the same world?"
- **The policy** — what to do when the answer is no — lives entirely in `zone-server/`, in
  `WorldService` and `WorldGenConfig.onMismatch`. `worldgen` has no opinion about it.

**In development you mostly do not need any of this**: `zone-server/src/main/resources/application.yml`
ships `worldgen.on-mismatch: REGENERATE`, so any version move already blows the world away and rebuilds
it on the next boot, with no action from you. The rest of this file is for when that is *not* enough,
or when it fires and you did not expect it to.

## The five numbers

| Number | Answers | Computed at | Stored in |
|---|---|---|---|
| `Stage.version` (hand-written int) | did this stage's **code** change? | each `Stage` subclass | — folded upward only |
| `Stage.paramsVersion` (digest) | did this stage's **tunables** change? | each `Stage` subclass | — folded upward only |
| `pipelineVersion` | did the world tier change **at all**? | `core/WorldGenPipeline.kt:80,133` — folds every stage's two numbers transitively, plus `WorldParams.chunkTierVersion` at `:64` | `world.pipeline_version` |
| `blockPaletteVersion` | did a `BlockType` **id** move? | `store/VersionGate.kt:51-57`, hashes name→id pairs sorted by id | `world.block_palette_version` |
| `chunkFormatVersion` | can a stored chunk even be **decoded**? | `RleCodec.VERSION` (`voxel/RleCodec.kt:78`) | `world.chunk_format_version` |

Plus a sixth that is a *different question* and is checked separately:

| `shapeVersion` | can this **row** rebuild the config the world was born with? | `core/WorldConfig.kt:149-163`, a hand-spelled hash of the terrain-deciding `WorldConfig` fields | `world.shape_version` |

The first three travel together as `PipelineVersion` (`store/VersionGate.kt:25`) and are compared by
`VersionGate.check` (`:95`), which is the *same* gate used for client/server compatibility — the question
is identical, so there is one implementation. `zone-server` calls it with the stored row playing the part
of "client" (`WorldService.incompatibilityOf`, `WorldService.kt:290-310`).

`ChunkMaterializer.VERSION` (`voxel/ChunkMaterializer.kt:879`) is not in this table because it is not
compared directly — it is folded into `WorldParams.chunkTierVersion` (`pipeline/WorldParams.kt:270-284`)
and reaches the gate through `pipelineVersion`. Bump it when chunk-tier *code* changes what a voxel is.

### What is deliberately NOT folded in

- `WeatherParams` — in neither `version` nor `chunkTierVersion` (`pipeline/WorldParams.kt:176-188`).
  Weather is `f(seed, region, t)` with nothing cached on it, so folding it in would refuse every existing
  world over a rain-frequency tweak. The KDoc says "do not fix it"; believe it.
- `PoiParams`, `NavParams`, `VegetationStandParams` — world tier only, no chunk depends on them.
- `CrystalParams`, `AetheriteParams` — chunk tier only, no stage reads them.
- The world's **name** — `driftFrom` (`WorldConfigMapping.kt:61-80`) skips it on purpose; renaming is
  cosmetic and must not read as a request for different terrain.

## What the boot actually checks

`WorldGenerationBootRunner` (`@Order(1)`) → `WorldService.load()` (`WorldService.kt:129`). Three
independent failure modes, and they are **not the same failure**:

1. **The row cannot describe itself** — `verifyRecordIsComplete` (`:320`). Recomputes `shapeVersion` from
   the columns and compares it to the stored one. They can only disagree if a terrain-deciding
   `WorldConfig` field has no column in `PersistedWorld`, so the row silently names a different world.
   **Always fatal** (`IncompleteWorldRecordException`, code `WORLD_RECORD_INCOMPLETE`); no policy applies,
   because regenerating writes the same incomplete row and fails identically next boot. The fix is a
   column plus a mapping in `WorldConfigMapping.kt` — all three places in that one file.
2. **This build generates different terrain** — `incompatibilityOf` (`:290`), the `VersionGate` call.
   Governed by `onMismatch`.
3. **The settings ask for a different world** — `driftFrom` (`WorldConfigMapping.kt:61`), a named
   per-field diff rather than a hash, so the log says `seed: 11753242 -> 42` instead of "incompatible".
   Warned about **always**, acted on **only** under `REGENERATE`. Drift alone never refuses a boot: a
   running world keeping its own dimensions is the documented contract, not a bug.

`resolve` (`:251`) applies the policy. Cases 2 and 3 are reported together in one message.

### The three policies (`WorldGenConfig.OnMismatch`)

- **`REFUSE`** (the code default) — abort the boot. What a live server wants: player edits are deltas
  over a base that no longer exists.
- **`REGENERATE`** — delete and rebuild. **What `application.yml` ships**, because that file is the
  development config.
- **`IGNORE`** — boot anyway and log. For a version bump you have reasoned about. The corruption it
  permits is silent and permanent.

## Resetting the world in development

Pick the smallest one that does what you need.

### 1. Change something versioned, and let `REGENERATE` do it

Already the default. Bump a `Stage.version`, edit a param, change `worldgen.seed` in `application.yml` —
the next `./gradlew :zone-server:bootRun` discards the world and builds the new one. Watch for
`Discarding world 'Genesis' and regenerating.` in the log.

`WorldProvisioning.recreate()` (`WorldProvisioning.kt:64`) is the whole mechanism, and it deletes:

- the `world` row,
- every `master_spawn_point` row (the cached spawn-point candidates),
- every `persisted_entity` of kind `ScriptComponent.KIND`.

Then `WorldService` publishes `WorldRecreatedEvent`, and `MasterWorldResetListener` re-homes every master
onto the new world's spawn points — keeping a master's home settlement if a settlement of that name still
stands, which it does under a pinned seed.

**One thing survives on purpose**: `PersistedWorld.winningOrder` is read off the row being discarded and
carried into the new one as `previousWinningOrder`. Nothing sets `winningOrder` yet, so today this always
carries `null`.

### 2. Force a reset when nothing versioned changed

There is no "reset now" flag. `REGENERATE` only fires on a mismatch or a drift, so if you want a fresh
world from identical code and settings, change something that counts as drift — the seed is the obvious
one (`worldgen.seed` in `application.yml`), and it is compared only when explicitly set
(`WorldConfigMapping.kt:69`). Otherwise, wipe the database (below).

### 3. Wipe the database

The datasource is MariaDB in a compose volume (`zone-server/compose.yaml`), started automatically by
`spring-boot-docker-compose`, so it **persists across restarts** — this is no longer the free action it
was under in-memory H2.

```sh
docker compose -f zone-server/compose.yaml down -v   # -v drops the zone-mariadb-data volume
```

This also destroys accounts, masters, items and parties, not just the world. For world-only, delete the
`world` and `master_spawn_point` rows and restart; boot will treat it as a first-ever world.

`spring.jpa.hibernate.ddl-auto` is `update`. Setting it to `create` drops and recreates the whole schema
every boot, which is a heavier hammer than `down -v` and easy to leave switched on by accident.

`update` also never *drops* a column, so a removed entity field leaves its column behind on any database
that already existed. Harmless — JPA stops mapping it — but a leftover `NOT NULL` column with no default
will refuse every future insert into that table. `skill.type` (removed when a skill's castability moved
onto its `script`, see the `skill-system` skill) is one such column; the `skill` table is fully rewritten
from `skills.yml` at every boot, so it only matters on a database whose rows predate the change. If an
insert starts failing on a column no entity mentions, that is why: drop the column by hand, or reset.

### What a reset does *not* have to clean

Chunk voxel data. `ChunkService` builds its store over a `MemoryBlobStore()`
(`world/stream/ChunkService.kt:212`) — base chunks and player deltas are **not persisted at all** today,
so every restart already starts from pristine generated terrain. `ChunkCache`'s key folds
`pipelineVersion`, so a stale chunk cannot outlive a version bump even in-process.

`world_object_divergence` (depleted trees, claimed POIs) is self-cleaning: each row stores the
`pipelineVersion` it was written under, and `WorldObjectDivergenceBootRunner` (`@Order(3)`) **discards**
rows whose version disagrees with the live world's, because a lattice retune renames what a `propId`
refers to.

## Gotchas

**`worldgen.previous-winning-order` does not trigger anything on an existing world.** It is absent from
`driftFrom`, and `incompatibilityOf` deliberately recomputes the current version using **the row's**
`previousWinningOrder`, never the config's (`WorldService.kt:290-298`) — otherwise setting the
development lever would report every world as incompatible. `WorldGenConfig.paramsFor`'s KDoc spells out
why: the tuning must be a pure function of the stored world, or the gate compares a version nothing
generated. The comment in `application.yml` claiming this setting "is a mismatch that `on-mismatch`
governs" is wrong; the code is right. To see an Order-shaped world, use
`./gradlew :worldgen:chronicle -Porders` or reset the world entirely.

**A version bump is not free even when it cannot move a voxel.** `Stage.version` reaches the RNG
(`GenRng.derive`), so bumping it reseeds that stage *and everything downstream*. Reserve it for code
changes; retuning a constant belongs in a params class, whose digest reaches `paramsVersion` and
deliberately **never** reaches the RNG (`core/ParamsDigest.kt:39-43`).

**Cached DB rows outlive a constant that has no version.** `MasterSpawnPointService.ensureComputed()`
returns existing rows if the table is non-empty. A change to something like
`SettlementSpawnPoints.MAX_HOME_CANDIDATES` — a plain `const`, in no digest — silently keeps serving the
old answer until the table is cleared. If you change a constant that decides persisted derived state,
either bump the version of the stage that reads it or clear the table by hand.

**Client-side there is one number, not three.** `ChunkEngine.VERSION` (`voxel/ChunkEngine.kt:50`, mirrored
in `bestia-client/src/Game/World/ChunkEngine.cs`) is the wire/decode contract. It is a *different*
question from `ChunkMaterializer.VERSION`, which is terrain-generation compatibility; both are currently
1 and they will diverge the moment either moves without the other.

## Ground truth

`WorldService.kt` (the three-case policy), `WorldProvisioning.kt` (what a reset deletes),
`WorldConfigMapping.kt` (all three row↔config directions in one file), `store/VersionGate.kt` and
`core/WorldGenPipeline.kt` (how the numbers are built). The stage pipeline itself is the `worldgen` skill's
subject, not this one's.
