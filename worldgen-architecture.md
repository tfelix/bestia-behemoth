# World Generation Architecture for a Distributed MMORPG

**Status:** Design document, partly implemented — see [Implementation Status](#implementation-status)
**Scope:** World generation pipeline, vector feature system, chunk materialization, client/server split
**Code:** the `worldgen/` Gradle module

This document is the design, and it is deliberately kept as written rather than rewritten to match the
code. Where the implementation deviates, the deviation is recorded in the next section and at the point
in the code where it happens — the design is the argument, the status section is the ledger.

---

## Implementation Status

Build-order steps **1–11** are implemented, along with the parts of **12** and **13** that belong in a
module with no I/O in it. 405 unit tests, plus a seed-sweep regression harness.

| # | Step | Status | Where |
|---|---|---|---|
| 1 | Framework + offline viewer | **done** | `core/`, `viewer/` |
| 2 | Vector primitives | **done** — plus the oriented rectangle; still no polygon | `vector/` |
| 3 | Heightfield → climate → hydrology → biomes | **done** — deviation 3; 4 half closed | `geo/`, `climate/`, `hydro/`, `bio/` |
| 4 | Erosion | **done** — deviation 2; 1 closed but shipping off | `geo/ErosionStage.kt`, `geo/WorldHeightField.kt`, `geo/DropletHeightField.kt` |
| 5 | Chunk materialization + RLE + feature stamping | **done**, plus occupancy and town structures — no scatter pass, no caves | `voxel/` |
| 6 | Derived structures | **done** | `derived/` |
| 7 | Resources + habitability + settlements + roads | **done** — deviation 5 | `resource/`, `civ/` |
| 8 | Town layout + buildings | **done** — deviation 8 | `civ/TownStage.kt`, `civ/StreetNetwork.kt`, `civ/TownBuildings.kt`, `voxel/TownStructures.kt` |
| 9 | Economy + NPC distribution | **done** — no live NPCs, which are a runtime concern | `pop/` |
| 10 | History simulation | **done** | `history/`, `core/Chronicle.kt` |
| 11 | Glacial features | **done** | `geo/GlacialStage.kt` |
| 12 | Distribution & caching | **partial** — cache tiers, delta, baking done and wired to the server; no delta persistence, no sharding/queue/gRPC | `store/`, zone-server `world/stream/` |
| 13 | Client-side base generation | **partial** — merged-RLE wire format, base hashing and version gate done; no client-side generator | `store/`, zone-server `world/stream/`, client `Game/World/` |

The service half of step 12 — Hilbert sharding, consistent hashing, the distributed work queue, the
gRPC surface — is deliberately *not* here. It belongs to the server that owns those concerns rather
than to a module whose entire value is being a pure function over data. Likewise the wire format half
of step 13: `store/` provides the base hash and the version gate that make client-side generation
*safe*, and the protocol that would use them is the network layer's business.

### Steps 8 to 10 run in the order 10, 8, 9

The build order numbers them town layout, economy, history, and the *dependencies* run the other way.
A town's walls enclose the extent it had when it was threatened; its ruins are settlements history
destroyed; how much of it is stone follows the wealth history gave it; and the number of buildings it
has follows its present population, which is what history spent a thousand years deciding. Laying the
town out first and retrofitting history into it would mean either regenerating the layout or leaving
the walls unexplained.

So the pipeline order is `settlements → history → towns → economy`, and the build order's numbering is
a statement about what to *implement* first rather than about what depends on what. The document's
framing of step 10 as a retrofit still holds in the one way that mattered: **history does not place
settlements.** They are already where the land is good, and history dates them, holds them, burns some
and empties others. That inversion is what keeps the simulation from having to re-derive every
habitability term in order to decide where a civ expands.

The consequence to know about: a site history never founded, or destroyed, still has a `SETTLEMENT`
marker from placement. That marker means "somebody would live here", not "somebody does". The
`SETTLEMENT_HISTORY` marker beside it is what says which, and `TownStage` and `EconomyStage` both skip
a site whose founding year is zero or whose abandonment year is not.

### The history log is a third world-tier product

`core/Chronicle.kt` is the append-only event store the scale-tier table has always listed and the
world tier never had. It genuinely is a third kind of thing: a raster is addressed by position, a
feature by position and kind, and an event by **year and actor** — "what happened to this town", "who
held this sword before it was buried", "which war produced this ruin". None of those is a spatial
query, and forcing them into the feature store would mean either a marker per event (hundreds of
thousands of zero-extent points no chunk will ever want) or losing the causal links between them,
which are the entire product.

What *does* go in the feature store is the physical residue: `RUIN`, `BATTLEFIELD`, `TOMB`,
`MONUMENT`. Those are places, they have extent, and chunk generation has to know about them.

`StageResult` gained a `chronicle`, `StageOutput` gained `History`, and `GenContext.chronicle()` is
scoped the same way layers and features are — a stage that reads the log without declaring the stage
that produces it throws, rather than quietly seeing an empty history and laying out a town with no
walls.

### Names are seeds, not strings

A station channel is a `Double`, so a `RUIN` marker cannot hold the name of the town it used to be. It
can hold a 48-bit integer, and that integer plus `history/Names.kt` *is* the name — the same trick the
document proposes for building grammars, applied to text. A name then costs eight bytes wherever it is
mentioned, so a settlement, its ruin, its tomb and every event about it all carry it without four
copies of a string; and any tool can print any name with no lookup table.

The cost is stated plainly in that file: **changing it renames the entire world.** Which is why the
seed is derived from the entity and the world seed rather than from a counter, so a change there is a
cosmetic diff rather than a silent history rewrite — and is still a change nobody should make after a
world ships.

### Still missing

Everything below is unbuilt or half-built, and each entry is argued where it belongs rather than here — this
is an index, not the reasoning. Grouped by what it would take.

**Wants a subsystem.**

- **The polygon geometry type.** The root of deviations 2–5: alluvial fans, deltas, lakes, coastlines and
  settlement outlines all want an area and have none. `COASTLINE`, `ALLUVIAL_FAN`, `DELTA`, `LAKE`,
  `OXBOW_LAKE` and `ROAD_JUNCTION` are declared feature kinds that nothing emits.
  `FootprintFeature` closed the cheap ninety percent; the rest is clipping, offsetting and concave indexing.
- **Caves.** No feature kind, no `carve_caves`. The client's surface-nets mesher already handles them, which
  is the unusual part — the renderer is ahead of the generator here.
- **The scatter pass.** No vegetation, no chunk-seeded anything, so the "chunk-seeded randomness is safe here,
  not in profiles" rule still has no users.
- **Live NPCs.** Schedules, rumour propagation, confidence on knowledge, expand-and-collapse. `pop/` produces
  the substrate; nothing makes a person walk to the market.
- **Delta persistence** (step 12) and a **client-side base generator** (step 13). Whatever persists a delta
  must persist its per-chunk revision with it.
- **Sharding, the work queue and the gRPC surface.** Deliberately the server's, and built nowhere.

**Closed since this list was written**, and each is argued at the point in the code where it happens rather than
here.

- **Seasonal precipitation is four layers.** `climate` runs four seasonal passes instead of two and keeps them,
  with the belt shift sinusoidal and the sweep's temperature seasonal and hemisphere-signed; monthly figures are
  interpolated on demand by `climate/SeasonalPrecipitation` rather than stored. Measured on the reference world,
  summer minus winter is 193 mm on average and the hemispheres are opposed, +117 against −109.
- **Top-2 biome blending**, as `BIOME_SECONDARY` beside `BIOME`, consumed by `voxel/SurfaceSampler` — which
  found that `BIOME_CONFIDENCE` is not calibrated to be a blend weight. See deviation 4.
- **Sea lanes**, closing deviation 7. Most worlds have none, correctly: a lane needs two cities a road cannot
  join. Measured, 3 to 6 worlds in 15 have at least one, rising with size.
- **Special sites**: mines, monasteries, forts and lighthouses, founded by `HistorySim` on terrain
  `history/SpecialSites` picked out. 18, 5, 22 and 4 of them on the 512 km reference world.
- **Chunk-scale droplet erosion**, closing deviation 1 — **and shipping off, on cost rather than seam
  grounds.** A whole-world render evaluates it per pixel and thrashes the tile cache; see that deviation for
  the measurement and for the four ways to make it cheaper.

**Wants a stage or a pass.**

- **Road-junction and trough-tributary smoothing.** The two places where a `min` of two profiles still leaves
  a crease that the confluence feature solves for rivers and nothing solves here.
- **The place → route → regrow → replace settlement iteration** — single pass.
- **Oil and gas** — skipped because nothing downstream consumes them.
- **Caves.** Repeated from the subsystem list above for one reason worth knowing: **nothing in the voxel tier
  can subtract.** `StructureSpans` adds spans and has no way to remove them, which is why a mine head is a
  planked shaft cover rather than a hole, and why caves are not a pass but a change to how the tier works.
- **Town blocks as objects** (deviation 8), **building interiors**, and a **shape grammar** that reads the
  grammar seed every building already carries.
- **History**: deities and monsters as entities, technology as more than a scalar, event templates with pre-
  and postconditions.

**Wants only the work.**

- **Data-driven configuration.** Every tunable is a Kotlin `data class`. `BusinessCatalogue` is the most
  tempting to extract and the clearest case for waiting.
- **Derived structures have no readers.** Walkable tiles, the opacity grid and column summaries are kept
  fresh on a per-tick budget, and movement validation, line of sight and pathing still do not consult them.
  No navmesh polygonisation. No settlement occupancy grid — though now that settlements have buildings in
  them, that one has something to occupy.
- **`WorldWrap` has two callers.** Chunk streaming normalises addresses and spawn-point selection normalises
  its search; movement, interest management and pathing use naive subtraction, so two players ten metres apart
  across the seam read as a world apart.
- **`zone-server/navigation/`** is a separate unused 2.5D nav stack, superseded for voxel terrain and
  untouched.
- **No disk or object-store cache tier** — `MemoryBlobStore` and a deflating wrapper, with the real tiers
  plugging in behind an interface.
- **No region tier.** `StageScale.REGION` is referenced by one pipeline unit test.
- **Client rendering**: no textures (vertex colour stands in), no LOD, no blocky pass for player-placed voxels.
- **Tooling**: no seed diffing, and no interactive inspector for a clicked river reach.

**An open question rather than a gap.** River counts going 512 → 1024 km gave 7.2×, 3.5× and 6.1× for 4× the
area across three seeds. Against a 44–207 spread at a single size, three samples cannot separate mild
superlinearity from noise. If it matters, it wants a dozen seeds per size.

### Actual module layout

The [Module layout](#module-layout) section below plans fifteen Gradle modules. The code is **one**
module, `worldgen/`, with those divisions expressed as packages. Fifteen modules over ~110 files buys
build-graph enforcement of a layering that a package convention and one review already enforce, at
the cost of fifteen build files. The split is worth revisiting if the module ever grows a second
consumer that needs only part of it.

Layers, each of which may use the ones above it and nothing below:

```
vector/     geometry, polylines, oriented footprints, station tables, profiles, blending, spatial index
core/       Stage, GenContext, RNG, layer + feature + history stores, pipeline, chunk column sampling
fields/     noise, grids, distance transforms, Poisson disk, heaps

geo/        tectonics, plates, orogeny, stream-power erosion, glacial flow, base heightfield
climate/    temperature, wind belts, orographic precipitation
hydro/      priority-flood, flow routing, lakes, river graph, meandering
bio/        biome classification, soil
resource/   deposit generation
civ/        habitability, cultures, settlement placement, route finding, roads, town layout, buildings
history/    the thousand-year simulation, place names, ruins and artifacts
pop/        catchments, the business catalogue and its preconditions, household expansion
voxel/      block palette, stratigraphy, chunk materialisation, town structures, RLE codec

derived/    column summaries, opacity grid, walkable tiles, chunk deltas, rebuild budget
store/      tiered chunk cache, delta + baking, base hashing, version gate
pipeline/   assembles the stages into a world; the regression invariants
viewer/     tooling; nothing depends on it, and it is the only package allowed Swing and the filesystem
```

The stage packages are siblings. They depend on the three foundation layers and on each other only
through declared stage dependencies — never by calling into one another.

The whole module depends on the Kotlin stdlib and the JDK, nothing else. That is a hard constraint, not a
coincidence: it is intended to be linked into the zone-server and possibly the client, so it must not drag
in Spring, JPA or any I/O.

**Integration is in progress.** The zone-server now depends on `worldgen` and its own parallel voxel package
is gone — that package had a `Voxel(material, occupancy)`, a boxed `Array<Array<Array<Voxel>>>` chunk and two
serializer pairs, none of it referenced outside its own tests, and keeping two incompatible voxel
representations in one repo was a bigger problem than anything either of them solved. Occupancy came across
into `worldgen`, which is where the idea belonged; the 32³ boxed layout did not.

The zone-server now **owns a world**. `net.bestia.zone.world` holds a `PersistedWorld` row - name, seed,
dimensions, creation instant, and the three version numbers - and `WorldGenerationBootRunner` finds it or
creates it as the first step of the boot sequence, before item import, entity loading, the ECS loop or the
socket server, so nothing can observe a world that does not exist yet. The first world of any server is called
Genesis.

The terrain itself is **regenerated at boot rather than stored**, which looks like a missing feature and is not
one: rasters and vector features are a pure function of the seed and the dimensions in that row, so persisting
them would be persisting a cache whose stale copies are indistinguishable from fresh ones. A 128 km world takes
about half a second. What players change is stored; what the seed implies is computed.

The row's version vector is checked against the running build on every boot, and a mismatch **refuses to
start** by default. That is the enforcement of this document's "once you ship, freeze the pipeline version"
rule: player edits are deltas over a generated base, so booting against a shifted base would move the ground
under them, and by the time anyone noticed the old base would be gone.

Three distinct things can be wrong at boot, and they are not one check:

| Wrong thing | What it means | Answer |
| --- | --- | --- |
| The row cannot rebuild its own config | A terrain-deciding `WorldConfig` field has no column, so the row silently describes a *different* world | **Always fatal.** Regenerating writes the same incomplete row |
| This build generates different terrain | Pipeline, palette or format version moved | `worldgen.on-mismatch`: `REFUSE` (default) / `REGENERATE` / `IGNORE` |
| The settings ask for a different world | Somebody edited `worldgen` in the configuration | Warned always; acted on only by `REGENERATE` |

The first is caught by `WorldConfig.shapeVersion`, a hash over every field that decides terrain, written at
birth and recomputed from the columns on each boot. Nothing else was checking that the persistence layer had
kept up with the generator, and it had not: `wrapX`/`wrapY` decided where the coastline went and had no column
for as long as the row existed, so every stored world rebuilt with the default wrap and said nothing.

The third is deliberately *not* fatal under `REFUSE`. Birth settings are documented as ignored once a world
exists, and a world quietly keeping its own dimensions is honouring that — making it a failed start would stop
running servers over an edit meant for the next world. It is reported as a named field diff
(`wrap-y: false -> true`) rather than a hash, because the question it has to answer is whether you are about to
throw a world away over something you did not mean to change.

`REGENERATE` is destructive and is what the development configuration ships with: it deletes the world row and
everything derived from it, and publishes `WorldRecreatedEvent` so `MasterWorldResetListener` can move every
player to the new spawn — their stored coordinates point into terrain that no longer exists, which otherwise
fails silently by burying them in a hill.

**What a development boot does *not* exercise is the mismatch itself**, and it is worth being exact about why.
The dev datasource is in-memory H2, so every boot starts with no world row at all and takes the *create* path;
there is nothing stored to disagree with. Twelve stage version bumps therefore change `pipelineVersion` without
any boot ever reaching `on-mismatch`. The path that actually matters is covered by `WorldProvisioningTest`,
which builds a stored row and a moved configuration in the same test rather than relying on a server restart to
produce one. A boot confirms the *new* pipeline generates and starts: measured after the seasonal, biome, sea
lane, special site and droplet work, Genesis came up in 951 ms with 4,454 vector features and four master spawn
candidates, inside a 3.2 s start.

**A new master picks where in the world to start.** `civ/SettlementSpawnPoints.kt` offers the settlements ranked
second largest downwards — never the largest, which is the capital everybody already knows about — each with a
coordinate on solid ground clear of the settlement's own built-up area. It is a pure function over the generated
world rather than a stage: it produces nothing the pipeline consumes, so making it a stage would put it in the
dependency graph for no one's benefit. The search walks outward on deterministic bearings derived from the world
seed and the settlement's index, so the chosen spot is stable across regenerations without being stored, and it
normalises through `WorldWrap` — a bearing that leaves the map has to arrive on the far side rather than be
discarded, which makes this the coordinate maths's second caller.

`zone-server` caches the result in a `master_spawn_point` table, computed once by a boot runner ordered directly
after the world loads and before entity loading. There is no world-id column, on the same argument
`PersistedEntity` already makes: the codebase assumes a single world row, and `WorldProvisioning.recreate`
clears the table whenever that row is replaced, so "a row exists" already means "belongs to the current world".

The spawn coordinate is sampled from the generator's own height field, which is better than the sea-level guess
it replaced — that put a new player hundreds of metres inside solid rock on a world whose centre is dry land, and
since every surrounding chunk is then uniform stone that encodes to twelve bytes and meshes to no surface at all,
it rendered as a black screen indistinguishable from the terrain failing to load. But **"sampled from the height
field" and "reconciled with the chunk the player is standing in" are not the same claim**, so the `Grounded`
marker still applies and `ChunkStreamSystem` still snaps an ungrounded entity on the first tick it sees it. That
is the general shape of the problem: the authoritative ground elevation belongs to `ChunkService`, which only the
tick thread may ask, so anything producing a position on a request thread — master creation, a script placing an
entity — has to invent one and be corrected later.

**Chunk streaming is wired.** `net.bestia.zone.world.stream` owns the `ChunkCache` → `ChunkStore` →
`DerivedStore` chain that this module had never been asked for, and streams merged RLE chunks to the client
over five new `bnet-messages` in the MAP range. The protocol is *announce, then serve on request*: a
`ChunkManifestSMSG` lists `(position, revision)` pairs for the view volume — about 1.5 kB for 121 chunks
against ~375 kB of payload — and the client asks only for what it does not already hold. Edits travel as
`ChunkPatchSMSG`, five bytes per voxel, serialised once and fanned out to the chunk's subscriber set as
retained Netty duplicates: thirty players in range of a ten-voxel edit cost about 1.5 kB between them rather
than thirty re-sent chunks. `/setblock` is the development trigger, gated on a new `Authority.TERRAIN`.

What is *not* yet done: no delta persistence, and no client-side rendering — the Godot client decodes chunks
and prints them. Nothing yet *queries* the derived structures for gameplay, though the streaming layer does
invalidate them on every edit and pays for their rebuild out of a per-tick budget.

`zone-server/src/main/kotlin/net/bestia/zone/navigation/` is a separate unused stack — a 2.5D `NavGrid` of
`Tile(height, canWalkLeft/Right/Up/Down)` with an A* over it — which `derived/WalkableTile` supersedes for
voxel terrain but which has not been touched, since agent pathing is a different job from the world-scale
route finding in `civ/RouteFinder`.

### The standard pipeline

```
tectonics → climate → erosion → glacial → hydrology → biomes → resources → habitability
                                                                              ↓
                                                   settlements → history → towns → economy
```

Twelve world-tier stages, in `pipeline/StandardWorld.kt`. Each declares only what it reads and the
scheduler enforces that, so the stage list is the entire wiring — there is no order to get right
beyond the dependencies the stages already state.

The diagram is drawn with glacial before hydrology because that is now a declared dependency. **For a long time
it was not**: the two were siblings, neither declaring the other, and they executed in that order only because
the topological sort breaks ties on stage name and `"glacial"` sorts before `"hydrology"` — an alphabetical
accident standing where a dependency belonged, and the mechanism behind
[the orphaned glacial stage](#the-glacial-stage-was-an-orphan-and-fixing-it-produced-the-worlds-first-lakes).
The lesson generalises: the scheduler enforcing "read only what you declare" is only half a guarantee, because
it cannot tell you about something you *should* have declared and did not. The other half comes from putting a
layer everything reads in the hands of the last stage that changes it, so that omitting the dependency fails
loudly instead of quietly returning a surface that is not the final answer.

Emitted layers and feature kinds:

| Stage | Raster layers | Vector features |
|---|---|---|
| `tectonics` | `BEDROCK_ELEVATION`, `PLATE_ID`, `ROCK_HARDNESS`, `CRUST_AGE`, `UPLIFT` | `FAULT` |
| `climate` | `TEMPERATURE`, `TEMPERATURE_RANGE`, `PRECIPITATION`, `PRECIPITATION_SEASONALITY`, `PRECIPITATION_SPRING`/`SUMMER`/`AUTUMN`/`WINTER`, `DISTANCE_TO_OCEAN` | — |
| `erosion` | `ERODED_ELEVATION`, `SEDIMENT` | `TECTONIC_BASIN` |
| `hydrology` | `FLOW_DIRECTION`, `FLOW_ACCUMULATION`, `DISCHARGE`, `WATER_LEVEL`, `LAKE_ID` | `RIVER_CHANNEL`, `RIVER_CONFLUENCE` |
| `biomes` | `BIOME`, `BIOME_SECONDARY`, `BIOME_CONFIDENCE`, `SOIL_FERTILITY`, `SOIL_DEPTH` | — |
| `glacial` | `ELEVATION`, `ICE_THICKNESS` | `GLACIAL_TROUGH`, `FJORD`, `CIRQUE`, `MORAINE` |
| `resources` | `RESOURCE_VALUE` | `ORE_DEPOSIT` |
| `habitability` | `HABITABILITY`, `MOVEMENT_COST` | — |
| `settlements` | — | `SETTLEMENT`, `SETTLEMENT_GRADING`, `ROAD`, `BRIDGE`, `SEA_LANE` |
| `history` | — | `SETTLEMENT_HISTORY`, `RUIN`, `BATTLEFIELD`, `TOMB`, `MONUMENT`, `MINE`, `MONASTERY`, `FORT`, `LIGHTHOUSE`, **and the chronicle** |
| `towns` | — | `STREET`, `BUILDING`, `TOWN_WALL`, `GATE` |
| `economy` | — | `SETTLEMENT_ECONOMY`, `BUSINESS`, `ROADSIDE_INN` |

`history` is the only stage that declares `StageOutput.History`, and the pipeline requires there be at
most one — two stages writing a world's history is not a conflict a run could sensibly resolve.

Climate runs four times coarser than the heightfield, as the design calls for, except on worlds too
small for that to leave a usable grid. **A downstream stage must therefore read climate through world
coordinates and never by index** — `Grid.resampled`, not `layer[x, y]`. Indexing a coarser layer with a
kilometre-grid coordinate does not fail, it *clamps*, and the economy stage spent a while reading the
polar temperature at the corner of the climate grid for every catchment in the world.

### World size, wrapping, and the ocean margin

None of this is in the design above; all of it came out of running the pipeline at a size the design
never considered. `WorldConfig` is 4096×4096 km on paper and the stage thresholds were tuned against a
512 km demo world, but the first world the server actually boots is 128 km. At that size the world came
out as a plain with two rivers on it.

**Detail scale.** Most of what makes a world interesting is gated on absolute size — a river needs
roughly a hundred square kilometres of catchment before it cuts a channel, a glacier needs enough ice
flux to gouge a trough. Those numbers are right, and they are exactly why a small world is dull: it does
not *deserve* the features. `WorldConfig.detailScale` is the knob that hands them over anyway. At 4, every
gating threshold behaves as though each length were four times what it is — `scaleByLength` divides a
length, `scaleByArea` divides by the square. It is deliberately unphysical, and it is the difference
between a test world you can see something in and a plain with a stream on it.

The default is `512 km / shortEdge`, clamped to `[1, 8]`. The reference extent is 512 km rather than the
4096 the design sizes for, because 512 is the size every threshold was tuned at by eye — so it must come
out at a scale of exactly 1, or this scaling would silently rework the one world already known to be
right. The clamp at 8 is where river networks become a fractal mat.

`detailScale` is a **computed property, not a stored field.** Stored, it goes stale under `copy()`: a
config derived for 512 then copied to `widthCells = 128` keeps the 512 world's scale, which is how the
invariant sweep spent a while passing on a config nothing would ever ship.

**What scales and what does not.** Terrain thresholds scale; *settlement density does not*, and that is
a decision rather than an omission. `cityTarget` is `worldArea / areaPerCity`, so settlements are already
a density — measured, 512 km gives 292 and 1024 km gives 1171, which is 4.01× for 4× the area. Ore
deposits track area the same way (133 → 508). Putting `detailScale` on top of that takes a 128 km world
from 28 settlements to 216, one per 20 km² of land, and the map becomes a continuous suburb. Terrain
wants to be denser on a small world; the number of *places worth walking to* does not.

Build time is linear in area — 512 km takes 3.7 s and 1024 km takes 14.6 s. Extrapolated, the design's
4096 km world is about four minutes and, at ~16.7M cells across roughly twenty `DoubleArray` layers,
a few gigabytes of heap. That is the real ceiling on world size, and it is a memory ceiling rather than
an algorithmic one.

River *counts* are not a target and swing hard on the seed — 44, 79 and 207 channels at 512 km across
three seeds with identical parameters. They emerge from the terrain rather than being placed to a quota,
so read them as a range. Channel initiation is a threshold *catchment area* rather than an absolute
discharge, which is what makes it scale-free in principle — drainage density is roughly scale-invariant
in nature. The measurements are consistent with that but do not confirm it: going 512 → 1024 km, so 4×
the area, gave 7.2×, 3.5× and 6.1× the channels on three seeds. Two of the three sit well above 4.
Against a 44–207 spread at a single size, three samples cannot separate mild superlinearity from noise,
so this is **an open question, not a settled one** — if it matters later, it wants a dozen seeds per
size, not three.

**Continuity.** A player walking east off the eastern edge has to arrive from the west. Making the terrain
genuinely periodic would mean every stage running on a toroidal domain — wrapping Voronoi, noise, flow
routing, distance transforms — and, fatally, vector features whose geometry crosses the seam, which
breaks the single continuous polyline the entire seam-free argument rests on.

So the seam is *hidden* instead. `TectonicsStage` forces a margin around all four edges below sea level
with a smoothstep blend (`geo/OceanBorder.kt` — a blend, not a clamp, or the margin has a cliff at its
inner edge). The wrap then happens between two stretches of featureless deep ocean, which look alike
because there is nothing there to look at. This is honestly not continuity, and it only works while the
margin is wider than the client's view distance.

The margin is therefore **2.5 km flat, not a share of the world.** What it has to beat is view distance,
which is a few hundred metres and has nothing to do with world size; sizing it as a share made a big
world's margin enormous while hiding the seam no better. A share cap of 6% still applies, but only binds
below about a 42 km world — the margin goes as the perimeter while land goes as the area, so on a tiny
world a fixed margin would eat the place.

**The margin's guarantee is a rectangle; its shoreline is not.** It has to be a rectangle, because that is the
shape of the world — but `distanceToEdge` returning the distance to a rectangle means every contour of it is
also a rectangle, and since everything this class does is a function of that one number, the coastline it
produced could only ever be four straight lines meeting at square corners with a 45° crease along each
diagonal. On a small world, where the margin is a sixth of the short edge, that was most of the visible
coastline — and conspicuously so, because the plate boundaries beside it are domain-warped Voronoi and produce
nothing straight. So each edge now wanders up to `oceanBorderWobble` (2.5 km) further inland, from an fbm
sampled around a *closed circle* so it is periodic along the edge, and the four edges are combined with a
polynomial smooth minimum that rounds the corners.

The safety argument is worth stating because it is structural rather than empirical. `checkOceanBorderIsOcean`
measures the margin with `WorldWrap`'s own *unperturbed* distance, so the only thing the wobble may not do is
report a distance **greater** than the true one. Every term is a true distance minus a non-negative wobble, and
a smooth minimum is never greater than a hard one, so the property holds by construction — the wobble can only
push the drowning further *in*, never seaward. `heightAt` was extracted from `applyTo` for a related reason:
the land-fraction search below needs to ask what a cell's finished height *would* be, and a second copy of that
arithmetic is a copy that eventually disagrees.

`wrapX` is on by default and `wrapY` is off, and not merely for want of implementing it: temperature comes
from latitude, so wrapping north to south walks one pole straight into the other — `ClimateStage` makes
latitude a linear ramp in y, so the seam jumps the world's whole latitude span, the wind belts flip hemisphere
with it, and the orographic rain pattern `BiomeStage` classifies on reverses.

> **Genesis turns `wrapY` on regardless** (`zone-server`'s `worldgen.wrap-y`), because the alternative is a
> wall a player can walk into. The discontinuity lands inside the same 2.5 km ocean margin that hides the X
> seam — `OceanBorder` forces all four edges underwater — so what is reachable either side of it is
> featureless polar sea, where a climate that makes no sense has nothing to be inconsistent with. Both flags
> are stored on `PersistedWorld` rather than read from configuration each boot: the margin is baked into the
> terrain, so a world generated unwrapped stays unwrapped. Making the seam genuinely continuous means giving
> latitude a periodic profile, at which point the world has two equators and two poles — a bigger change than
> it sounds, and not needed to stop a player finding the edge.

`core/WorldWrap.kt` holds
the coordinate maths — floor-mod normalisation, signed shortest-path deltas, chunk normalisation (never on
z; up is not a loop). **Chunk streaming was its first caller** — every chunk address, computed or client-supplied,
goes through `normalise` before it reaches the generator, so walking off the eastern edge streams the western
terrain. `civ/SettlementSpawnPoints` is the second: it searches outward from a settlement for solid ground, and a
bearing that leaves the map has to come back in on the far side rather than be discarded. **The rest of the ECS
still does not call it**: movement, interest management and pathing use naive subtraction, so two players ten
metres apart across the seam read as a world apart.

### Making the world land-dominated broke the climate, and the climate was already broken

The world was a quarter land and mostly desert. Fixing the first exposed that the second had two bugs in it
that a mostly-ocean world had been hiding, and the chain of consequences is worth recording in order, because
almost every constant below moved for a reason that lives in a different stage from the constant.

**More land.** `oceanicShare` 0.6 → 0.45 and `targetLandFraction` 0.32 → 0.50. The first is the real lever
despite the name of the second: continental and oceanic crust sit in two well-separated elevation clusters,
about +300 m and about −3400 m, so `oceanicShare` decides what share of the world is *capable* of being land
and `targetLandFraction` only decides where in the gap between them the waterline falls. Ask for more land than
the continental share can supply and the waterline is forced down into the oceanic cluster, where what surfaces
is ex-seafloor: no uplift behind it, no orogenic relief, and a coastline that follows the plate diagram because
per-plate base elevation is the only thing varying there.

**`upliftDryLand`, so the new land is not a shelf.** Stream power is `U − K A^m S`; where `U` is zero the only
steady state is a plane, so the forty-five erosion timesteps *removed* whatever relief the noise had put on
ex-seafloor land instead of organising it into valleys. Every cell above sea level now gets at least an interior
plate's uplift, ramped in over 250 m — a step in uplift along a contour is a step in erosion rate along a
contour, and erosion turns that into an escarpment following the coast, which is a landform made by an `if`.

**The land-fraction target was measuring the wrong thing.** It used to put the target quantile of the
*interior* at sea level, and then the margin was applied on top and drowned some of what had just been counted.
Being a couple of points out is tolerable; being **world-size dependent** is not, and it was — the margin is a
sixth of a 128 km world's short edge and a fiftieth of a 512 km one's, so the same 0.32 meant something
different on every world, which defeats the point of a number that exists to make seeds comparable. It is now a
**bisection on the shift**, measured over the whole world *after* the margin, using the same `OceanBorder.heightAt`
that will actually be applied. Cells the margin cannot reach keep a histogram, so one `O(N)` pass serves every
iteration and only the margin band is re-evaluated.

**Mountains had to stop scaling with plate count.** A continent-continent collision raises 3400 m because that
is what one raises on Earth, and that is right for a world with Earth's ratio of plate boundary to area. A world
at detail scale 4 has four times that ratio — that is what detail scale *is* — so leaving the amplitude alone
gives every one of those many boundaries a Himalaya, and the result is not a dramatic world but a uniformly
vertical one: measured, 22% of the land classified as bare cliff and 6% as forest and grassland together. So
orogeny is scaled twice by `detailScale`: amplitude by `1/sqrt(detailScale)`, and *distance from the boundary*
by `× detailScale`, which is what puts lowland back between the ranges rather than shrinking the ranges
themselves. `orogenicRelief` came down 1750 → 820 alongside it. Both scalings are exactly 1.0 at 512 km and
above, so the reference world does not move.

**And the plate spacing floor was distorting small worlds.** `MIN_PLATE_SPACING` is a claim about real plates —
fifty kilometres is about the smallest thing that behaves like one — and on any decent world it never binds. On
a 128 km world it bound hard: 25.6 km clamped up to 50 km, leaving six to nine plates, a boundary cross-fade a
fifth of the map wide, and a continental swell whose wavelength was **1.7 lobes across the whole world**, which
is a tilt rather than a landscape. The floor is now `scaleByLength`d. Relatedly, hotspot *island size* was
decoupled from plate spacing: an island's size is set by how much magma one plume delivers and has nothing to do
with how big the plate above it is, so finer plates were silently producing smaller islands.

#### The climate had a step function in it, and no way to wet a continent

Two bugs, both invisible on a mostly-ocean world, both catastrophic on a land-dominated one.

**`Winds.zonalSign` was a step function, and it was visible from orbit.** The advection sweep runs one row at a
time in the direction the wind blows, so where the sign flipped between two adjacent rows the two rows were
built from opposite upwind histories — one row's air had crossed a continent, the next row's an ocean — and the
precipitation field acquired a **discontinuity running the entire width of the map**. It survived the mixing
blur, which averages over a few cells and cannot repair a jump that large, and `BiomeStage` then thresholded on
it and drew a perfectly straight stripe one climate cell tall across every continent it touched. On a world
spanning 68 degrees there were six of them. The physics says the same thing the picture did: the boundary
between the trades and the westerlies is the subtropical ridge, a broad belt of light and variable wind, not a
line where the wind reverses. `Winds.eastwardShare` returns a *share* instead, each row is swept both ways, and
the two are blended over `BELT_TRANSITION` degrees of latitude — degrees, so the blend spans the same slice of
the planet at any world size or resolution.

**Rain could not cross a mountain, and land could not give its water back.** `orographicCoefficient` at 0.0021
was not a coefficient so much as a switch: `rain = moisture · coefficient · rise` reaches the whole of the
moisture at a rise of 476 m, so on a world with 4 km climate cells and 4.5 km peaks the *first* range a wind met
stripped the air to nothing — and with `landEvaporationRate` effectively zero, nothing put any back for the rest
of the continent. Every interior was the rain shadow of one mountain, which is how a world ends up 29% desert and
21% cold desert with a green fringe. So the coefficient came down to 0.0009, where a kilometre of ascent takes
most of the moisture but not all of it, and **continental moisture recycling was added** at a third of the ocean
rate: rain landing on vegetated ground largely goes back up as evapotranspiration and falls again downwind — the
Amazon recycles something like half its rainfall that way, and it is the reason a continental interior is
habitable rather than a desert as a matter of arithmetic. Which is exactly what the model's interiors were.

`leeSuppression` 0.22 → 0.34 follows from the mountains: a rain shadow is a feature, but with plate boundaries
every twenty-five kilometres there is no longer such a thing as an interior that is not downwind of a range, so
the suppression compounded pass after pass. At 0.34 a single range still casts an unmistakable shadow without
the second and third behind it finishing the job.

`meanPrecipitation` 880 → 1850, and the interesting part is that **it only became a lever once the field stopped
being mostly zero.** Measured on the old model, raising it moved the biome mix by a single percentage point —
a true measurement of a broken distribution, because most of the land was at *exactly* zero rain and scaling
zero by 1.3 is still zero. Note it cancels in `ErosionStage` and `HydrologyStage`, which both normalise by the
field's own mean by design, so it moves biomes without moving the landscape.

One more, found the same way: the sweep starts with dry air, so on a wrapping world the column each row happens
to start at had no upwind fetch at all and always received exactly zero rain. Rows alternate direction, so the
result was a **dry stripe down both map edges.** `sweepRow` now runs the row twice and records only the second
lap, letting it find its equilibrium; both map edges are forced ocean, so the seam the spin-up lap crosses is
open water on both sides.

#### Channel initiation needed slope, not just area

The river network came out as combs of short parallel streams hanging off every shore, with the uplands they
should have risen in left blank. Two causes, and neither was the threshold's magnitude.

**The threshold was an area alone.** A hillside sheds its water into a defined channel after a few hectares; a
floodplain of the same catchment carries no channel at all, because there is no gradient to cut one. So the
coastal plain was the only place a purely area-based threshold was ever reached first, and that is where every
channel head sat. `channelSlopeExponent` adds the Montgomery–Dietrich slope-area law `A·S^n > constant`, as a
ratio against **this world's own mean land slope** rather than a constant — a fixed reference slope has to be
either above or below a given world's typical ground, and whichever it is it moves every threshold on the map in
that direction and silently becomes a second control on how many rivers there are. Measured: a reference of 0.03
on a world whose land averages nearly three times that took the river count from 93 to 270 while barely moving
where the heads sat, which is the wrong axis entirely. Clamped rather than tapered, because the tails are where
it misbehaves — a flat lake bed approaches zero slope and would demand an infinite catchment.

**And `Grid.gradient` was the wrong instrument for measuring that slope.** At kilometre cells the steepest ground
in the world is the *shoreline*: a cell of coast beside a cell of shelf at −400 m reads as a slope of 0.4,
steeper than any mountain front the erosion model produces. Fed that, the slope term did the exact opposite of
its purpose — it made the coast the easiest place in the world to start a channel. `landSlopes` clamps the
neighbours at sea level, which asks the question that was meant: how steep is the *land* here. A channel head is
a subaerial feature and what is under the water offshore has nothing to do with it. `meanOverLand` averages the
reference over land cells only, for the same reason: the sea floor is most of a half-water world and nearly flat
once the shoreline scarp is clamped out, so averaging it in would drag the reference below any real hillside and
turn a redistribution back into a discount.

`channelCatchmentArea` 93M → 420M is downstream of all of the above, and shows how far these couplings reach:
**flattening the absurd rain shadows took the world from mostly-bone-dry to broadly damp, which meant the
aridity multiplier stopped firing on hardly any cells, and the same 110 million that had given 106 channels gave
531.** At 420 million the count is back to 140 with 52 confluences rather than 14, which is the network being
genuinely dendritic rather than a set of separate coastal streams. `aridityExponent` 1.0 → 0.55 is the knob for
how *long* a river is rather than how many there are: at 1.0 an interior receiving a third of the mean rainfall
needs three times the catchment, which on a continental world pushes every channel head down towards the coast.

The general lesson, and it is the same one the ocean margin and `detailScale` teach in a different key: **these
constants are not independent, and most of them are not tunable in the stage they live in.** A change to plate
density moves mountain height, which moves rain shadow, which moves the biome mix three stages later. That is
why `viewer` now prints the land fraction and the biome mix on every run — see the tooling section — because a
number nobody can read off the output is a number that drifts.

#### The glacial stage was an orphan, and fixing it produced the world's first lakes

For most of this module's life **no stage declared `GlacialStage`, and nothing consumed `ICE_THICKNESS` or any
of `GLACIAL_TROUGH`, `FJORD`, `CIRQUE` or `MORAINE`.** Outside the stage itself the only references were its
construction in `pipeline/StandardWorld.kt`, four colours in `MapRenderer`, and one fjord-sill invariant.

So the four feature kinds reached the world *only* at chunk-materialisation time — and a trough carves
absolutely (`Profiles.glacialTrough` ignores the base height and `MIN` decides), by hundreds of metres. Which
meant hydrology, habitability, settlement placement and town layout all committed to a coarse elevation that
the finished chunks then cut away beneath them. `TownStageTest.the ground under a building is level` caught the
visible end of it: a town at 2463 m laid its buildings on the coarse raster and a trough carved the ground
under them to 1938 m, leaving a **525 m plinth**.

**The fix is one edge and one raster.** `ErosionStage` now emits `ERODED_ELEVATION` — the fluvial surface — and
`GlacialStage` emits `ELEVATION`, the fluvial surface with the ice's own work cut into it. Because dependency
scoping is transitive, adding `GlacialStage` to `HydrologyStage`'s dependencies gives *every* stage below it the
carved ground for free, and because `ELEVATION` changed owner, a stage that reads the ground without declaring
glacial now fails loudly instead of silently reading a surface that is not the final answer. The pipeline
enforcing "read only what you declare" was only ever half a guarantee; it cannot tell you about something you
*should* have declared and did not. Making the ground belong to the last stage that shapes it is what closes
that half.

The carve is evaluated through `FeatureEvaluator` itself rather than a second reading of the profiles, so the
coarse and fine tiers cannot disagree, and it is safe to apply twice because a trough imposes an absolute floor
under a `MIN` blend — `min(floor, floor)` is `floor`. Moraines are excluded for exactly that reason: they are
`ADD`-blended ridges, and carving one here and stamping it again at chunk time would build it twice as tall.
The filter is on the blend mode rather than the feature kind, so a new additive glacial feature is excluded
automatically.

**And the troughs turned out to be impossible.** This section's opening paragraph says real troughs are one to
three kilometres wide in total; measured, the corridor half-widths on the reference world ran to a *median of
8.7 km, a ninetieth percentile of 45 km and a maximum of 93 km* — the same unbounded cube root as the cirque
radius, one scale up. It survived because nothing but chunk generation ever read a trough, and at 32 m a
valley floor of impossible width looks like ordinary flat ground. The moment the carve reached the raster it
was unmissable: the troughs' bounding boxes summed to **thirty-three times the area of the world**, they
drowned four points of its land, and the stage went from 255 ms to six seconds. `maxFloorHalfWidth` caps it.

The results, on the 512 km reference world:

| | before | after |
|---|---|---|
| Lakes | **0** | **115**, 25 of them endorheic |
| Land fraction | 0.507 | 0.506 (0.462 with the uncapped troughs) |
| Glacial stage | 255 ms | 559 ms |
| Seam check | clean | clean |

**The lakes are the headline.** `hydro/Lakes.kt` was complete and unit-tested and had never once received a
basin, because `ErosionStage.incise` clamps every cell to at or above its receiver and hands hydrology a
depression-free surface. An overdeepened trough floor *is* a closed basin — the floor is a running minimum with
the overdeepening subtracted on top — so the carve gave priority-flood something to fill. Endorheic basins
appearing also turns on `ResourceStage`'s evaporite deposits and `Palette`'s salt-lake colour, both written
long ago and never once exercised.

One thing that was still true and is now not: a **128 km world had no lakes**, because it has almost no ice.
That wanted the second basin source, and it got one — see below. And
[deviation 6](#deliberate-deviations) is closed for rivers but the wider lesson is the one worth keeping: a
deviation that described the polite half of a defect made the defect look considered for a year.

#### The second lake source, for the worlds ice never reached

Glacial overdeepening gave the reference world 115 lakes and the 128 km world `zone-server` actually boots
**none** — it has 36 glacial features and every trough runs to the sea. Five of 120 seeds at 192 km were dry for
the same reason. So `geo/ClosedBasins.kt` supplies the other source real lakes come from: a graben dropped
between the shoulders of a divergent boundary, or an old continental interior that has sagged. Between them
those hold the Caspian, Baikal, the Dead Sea, Lake Eyre and every playa in the Great Basin.

It runs as a **pass over the finished erosion surface**, and the precedent is a dozen lines away in the same
file, where `OceanBorder` is reapplied because forty-five timesteps of uplift legitimately undo it. The same
argument with the sign flipped: a basin carved *before* the loop is a basin the loop fills in, because filling
depressions is what the loop does. `incise`'s clamp stays exactly as it was.

**Raster, not vector.** A basin is five to twenty kilometres across — an order of magnitude past the three coarse
cells that push a feature into the vector tier, and broad enough that a bicubic sample of the kilometre grid
reproduces it at chunk scale with nothing to stitch. `TectonicsStage.addHotspotChains` makes the same call in the
same package for the same reason. A `TECTONIC_BASIN` point marker records each one carrying no terrain effect,
which is not for the chunks but for the invariants and the viewer: the defect this pass finishes off survived as
long as it did because nothing counted lakes and nothing drew basins.

**The depression is arithmetic rather than tuning**, which is what lets lake existence finally be asserted per
world. Each basin is measured against a *sill ring* — the annulus just inside its radius, at least one and a half
cells thick so no eight-connected path can step across it. The floor goes a subsidence below the ring's lowest
cell, and the rim height is set to whichever is larger of "reaches the ring's tallest cell at the radius" and
"is still above the sill at the ring's inner edge". The second term is the load-bearing one: with the profile
`floor + (d/r)^n · rimHeight` it needs `rimHeight ≥ depth / q` where `q = ((r − ringThickness) / r)^n`, and
setting it so means nothing in the ring can end up below the sill while the centre is a full subsidence under it.
A point lower than a closed band around it is a closed depression.

Three things were got wrong on the way there, and each is now a test:

- **Judging a candidate on the whole disc rather than the ring** disqualified any site with a valley floor
  anywhere inside it, and left the 512 km world with exactly **one** viable site. On the ring, a valley that
  genuinely breaches the basin still counts — it *is* the sill — and one that merely passes nearby does not.
- **A product of three sub-unit preferences is small almost everywhere.** Interiority × quietness × structure
  scored a perfectly reasonable site at 0.07, so every world fell back to its single best candidate. Each factor
  now keeps a floor and the product is a ranking rather than a filter.
- **`quietUplift` was reasoned into place at twice the interior figure, and measurement moved it.** What the
  candidates on nine real worlds actually look like is a cluster at 2.0–2.5, a gap, then the crests at 7–9.5.
  Cutting at 2.4 scored that middle cluster at nothing — and the cluster is not orogen, it is orogen *flank*,
  where a foreland basin goes. 4.0 keeps the flanks and still scores the crests at zero.

A fourth was caught by a test asserting more than the design promised: the ring is not left *unchanged*, only
never cut below its sill, and a paraboloid left the deepest **cell** 13% short of the nominal floor because the
subsidence is defined at a world position half a cell-diagonal away. A quartic profile makes that shortfall a
millimetre per metre of ring relief, and gives the basin the flat floor a playa actually has.

**And a fifth was found by looking at the map, which is the only way it could have been.** Every test passed and
every sweep was clean, and the reference world carried six flawless blue discs while Genesis carried seven —
against terrain where nothing else is straight or round, they read as impact craters. This is the rectangular
coastline and the ruled hotspot chain in a third form, and it is worth naming as a recurring failure rather than
three separate ones: *a landform generated from one number is shaped like that number's level set.* The fix is
`OceanBorder`'s own trick, one dimension over — the bowl's reach is pulled **inward** by up to a third at an
angle-varying amount, sampled around a closed circle so it is periodic in the angle. Inward-only is what makes it
free: the sill ring is measured on the unwarped radius, so shrinking the profile can only raise it at a ring
cell, and the whole argument above survives with the same `q`.

Paying for that cosmetic warp by enlarging the minimum basin was tried and reverted, and the measurement is why:
a larger disc has a larger sill ring, more candidates have a ring dipping too near the sea, and Genesis went from
**seven basins to three**. A third of a world's lakes is not a reasonable price for an outline.

| | 512 km reference | 128 km Genesis |
|---|---|---|
| Basins | 8 | 7 |
| Lakes | 115 → **121** | 0 → **7** |
| Endorheic | 25 → 27 | 0 → **1** |
| Land fraction | 0.506 → 0.506 | 0.525 → 0.525 |
| Seam check | clean | clean |

`Invariants.checkTheWorldHasStandingWater` is registered as a result, and it is deliberately the one lake
property stated unconditionally — the *absence* of that check is what let a dead subsystem look healthy for a
year, and the tooling section above records how. The per-basin claim stays a disjunction, because
`GlacialStage` writes the same surface afterwards and a trough crossing a ring drains it. Which is a landform,
not a bug, and the two sources cover each other: a world where ice breached every basin is a world with
ice-carved basins of its own.

### The voxel grid has a resolution floor, and features have to respect it

Found by looking at the client rather than at a map, which is the point of the story. The ground was sand with
thin green streaks across it, dashed like a badly drawn line, and no view in `viewer/` could show why: every
view renders the whole world into a few hundred pixels, so the streaks were a pixel or were nothing.
`viewer/ProbeMain.kt` was written for this — it prints one 48 m window as text, one character per voxel column,
which is the scale the complaint lives at. `./gradlew :worldgen:probe -Pchannels=1` reports the numbers below.

The streaks were rivers. Hydraulic geometry is `width = 4.2 Q^0.5` and `depth = 0.36 Q^0.4`, which are correct
and which need `Q` of about 13 m³/s before a channel is one metre deep. Nothing in a world of a few hundred
kilometres carries that. Measured, at 512 km with `detailScale` exactly 1 — the reference world, nothing scaled
— **every channel station in the world was shallower than one voxel**: median 32 cm, deepest 91 cm. At 128 km,
a median of 15 cm.

The failure that produces is not "shallow rivers". `ChunkMaterializer` writes a water voxel only where the
water surface crosses a voxel boundary, so a channel 15 cm deep gets water in the columns where its bed happens
to sit just below a boundary and none in the columns where it does not — and the bed descends continuously
along the reach, so that alternates. A dashed line of water on sand.

`hydro/ChannelGauge.kt` floors width and depth at 3 and 2 voxels. Deliberately unphysical, in the same way
`detailScale` is: below the grid's resolution the choice is not between accurate and inaccurate, it is between
visible and absent. Two things worth knowing about the result:

- **The depth floor binds everywhere**, because the physical depth never reaches two metres in any world tested.
  Depth is now effectively constant and **width** carries river size — 3–9 m at 128 km, 3–13 m at 512 km. That
  is the honest outcome rather than a shortfall: real channels span 0.1–0.9 m of depth across this whole size
  range, which is less than one voxel of spread, so the grid cannot express river size vertically whatever
  formula is used.
- At a metre per voxel **there is no such thing as a shallow brook**. Every channel is deep enough to swim in
  and `AgentProfile.maxWadeDepth` of 1 m means agents wade none of them.

The general lesson is the one the ocean margin and `detailScale` also teach: a threshold expressed in metres is
a claim about how big the world is, and a cross-section expressed in metres is a claim about how fine the grid
is. Neither survives being pointed at a world it was not tuned for.

### Deliberate deviations

Each is also noted at the point in the code where it happens, because a deviation visible in only one
file is one somebody will later mistake for a bug.

1. ~~**Detail erosion is analytic noise, not particle droplet erosion.**~~ **Closed, and shipping off.**
   `geo/DropletHeightField.kt` is a `BaseHeightField` decorator that does the droplets seam-free, and
   `DropletParams.enabled` defaults to false.

   **Both halves of that are deliberate.** The blend is safe because the droplet tiles sit on a *fixed world
   lattice* rather than being keyed on the asking chunk: `heightAt` reads nothing but its two arguments, so two
   chunks asking about a shared column run the same arithmetic and get the same bits, which is what
   `ChunkSeamCheck`'s `epsilon = 0.0` demands. The tent weights over the lattice are a partition of unity by
   construction, and they are zero exactly at the edge of each tile's simulated square - where a droplet has
   nowhere to flow and is abandoned - so the region a tile is worst at is the region it contributes nothing to.
   `ChunkSeamTest` runs the check with erosion on and carries the wrong design as a negative control.

   **It ships off, and as of the calibration pass the reason is cost rather than seams.** The seam evidence, stated
   exactly: `DropletErosionTest` runs `ChunkSeamCheck` at zero tolerance over 16 chunks with erosion **on**, and
   guards against a vacuous pass by asserting the erosion moved more than 0.5 m; a second test shows thread count
   and generation order change nothing. That is unit scale. **The whole-world `SeamCheck` with it on has never
   completed** — not because it failed, but because the run below does not finish, which is a tidy illustration of
   the problem: the cost blocks the very verification that would justify the default.

   What blocks it is throughput, and the numbers are worth keeping because they are large enough to be surprising:

   | with droplets | `viewerExport` (128 cells) | `:worldgen:test` |
   | --- | --- | --- |
   | off | 114 s | ~2.5 min |
   | on | **> 20 min, did not finish** | **> 25 min** |

   **The cost is in the offline tools, not in chunk generation**, and the distinction is the whole of why this is
   a deferral rather than a rejection. `viewer/ScalarField.kt`'s base-height view calls `heightAt` **once per
   rendered pixel** over the whole world. Each call blends *four* tiles off the 128 m lattice, each tile miss
   costs a 129×129 grid of analytic samples plus its droplet simulation, and `cacheLimit` is 512 tiles — so a
   whole-world render asks for on the order of 10⁶ tiles and, worse, the cache *clears* rather than evicting when
   it fills, discarding a warm working set each time. Chunk streaming has the opposite access pattern: chunks are
   contiguous, so a handful of tiles serve hundreds of columns and the cache does its job.

   So the feature is affordable for the thing it is for and unaffordable for the tools that verify it — and those
   tools are how every phase of this module is checked, which makes the tax recursive. Turning it on would slow
   every later verification run for a metre-scale detail that is sub-pixel in a whole-world PNG.

   **Making it cheaper, in the order the effort is worth it.** The first item alone is probably enough to make the
   default free:

   - **Do not evaluate droplets per pixel in the viewer.** The base-height field is documented as "what features
     blend against", and for a whole-world overview the analytic field *is* that — the droplet delta is smaller
     than a pixel. Having `WorldScene` show the analytic field for overview extents (or only wrapping in droplets
     below some window size) removes the pathological access pattern without touching the generator. This is a
     `viewer/` change and the cheapest real fix.
   - **Evict instead of clearing.** `tiles.clear()` at the limit throws away the working set wholesale, which
     turns a near-miss into a cold start. An LRU of the same size is strictly better under contiguous access and
     no worse under thrash.
   - **Build the tile's base grid coarsely.** The 129×129 analytic samples dominate a miss, and the droplet
     simulation only needs a plausible *slope field* to route over — the fine detail is preserved regardless,
     because the tile contributes a *delta* that is added to the true analytic height. Sampling the base at 8 m
     and interpolating inside the tile is 16× fewer analytic calls for a routing surface that is smoother than
     the one being corrected. Measure the resulting gullies before believing it.
   - **The honest alternative to simulating at all**: keep analytic detail and make it *asymmetric* on curvature
     — cut on convex ground, fill on concave toes and slope breaks. That is O(1) per column, seam-free by
     construction, needs no tiles and no cache, and buys the one thing the deviation says analytic noise lacks
     ("gullies without the debris fans at the bottom of them"). It is not sediment transport and would not be
     claimed as such, but it is the shape transport produces, and it is the option to reach for if the tile
     machinery ever looks like more trouble than it is worth.

   Two things measurement changed on the way, both recorded in `DropletParams`: the droplet density had to come
   down twentyfold, because at the first figure 7% of cells were pinned at the delta clamp and the clamp was
   shaping the terrain; and a droplet that evaporated or ran out of steps was **deleting the sediment it
   carried**, which made the pass sediment *removal* rather than the transport that is the whole reason to
   prefer it over noise. (`geo/DropletHeightField.kt`)

   It is reachable meanwhile through `probe --droplets` or `droplets.enabled = true` in a params file — the
   chunk-tier tuning became loadable precisely so that this cost could be A/B'd on one build instead of two.
2. **Alluvial fans and deltas are raster deposition, not vector polygons.** The vector tier has no
   polygon type; adding one is a subsystem, not a stage. (`geo/ErosionStage.kt`)
3. **Lakes live in the raster tier**, as a water level plus a basin label, rather than as vector
   features. That follows the design's own rule — a feature narrower than about three coarse cells
   belongs in the vector layer, and lakes generally are not. Small ponds and oxbows *would* be, and
   are not generated.
4. **Edge biomes are raster distance transforms, not vector buffers.** Coastlines are not vector features
   at all, so a beach is a band around ocean cells rather than a strip inside a coastline polyline; and
   riparian corridors buffer high-discharge *cells* rather than the river polylines that already exist.
   Both are therefore quantised to the coarse cell instead of being crisp at any resolution.

   **The top-2 half is closed.** `BIOME_SECONDARY` stores the runner-up beside `BIOME`, and
   `voxel/SurfaceSampler` dithers between the pair so a boundary interpenetrates rather than being a line. An
   overridden cell gets a sentinel rather than the climatic winner it displaced, because this layer means "the
   biome that came second in the classification" and an overridden cell was not classified - storing it would
   make every shoreline read as an ecotone.

   What closing it *found* is worth more than the feature. **`BIOME_CONFIDENCE` is not calibrated to be a blend
   weight**, though the classifier's KDoc has always implied it was one "for free". Measured over the cells that
   have a runner-up at all: median 0.066, 95th percentile 0.343. With fourteen prototypes in seven dimensions the
   nearest and second-nearest distances concentrate, so `1 - sqrt(best/second)` sits near zero nearly everywhere
   and using it directly dithers the whole world at close to even odds - which is what the first implementation
   did, as a 50/50 checkerboard visible in `probe`. It ranks transitional cells correctly; its absolute scale is
   not a fraction. The dither therefore has a cutoff and a coherent noise field, and recalibrating the layer
   itself is left to a change that measures biomes, since seven stages read them. (`bio/BiomeStage.kt`)
5. **Settlement footprints are discs, not polygons** — a radial terrace rather than an outline with a
   street graph inside it. The polygon and everything it would contain is step 8.
6. ~~**Rivers do not follow glacial troughs.**~~ **Closed.** Hydrology routed over the raster while a trough
   existed only in the vector tier, so a post-glacial river did not know to run along the trough floor it
   should have inherited. `GlacialStage` now rasterises its carve into `ELEVATION` and `HydrologyStage`
   declares it, so flow routes over the ground ice actually left.

   **The entry understated the defect, and the understatement was itself the bug.** Framed as a river not
   inheriting a valley floor it reads as cosmetic, which is why it sat here for a year. In fact *nothing
   downstream saw glacial terrain at all* — no stage declared `GlacialStage`, so habitability, settlement
   placement and town layout were also deciding against an elevation the finished chunks carved hundreds of
   metres out of, and the troughs themselves were up to 93 km wide because nothing ever looked at one. See
   [The glacial stage was an orphan](#the-glacial-stage-was-an-orphan-and-fixing-it-produced-the-worlds-first-lakes).
   A deviation describing the polite half of a defect is worse than no entry at all, because it makes the
   defect look considered.
7. ~~**Roads do not connect across water.**~~ **Closed.** The rejected pair - a route that would cross the sea -
   is now collected rather than dropped and routed with the same `RouteFinder` over a water cost field, as a
   `SEA_LANE`. The trade network is one graph with two edge types, so `simulateTraffic` needs no idea some of its
   edges are wet.

   The margin is impassable in that cost field, which makes "no lane crosses the wrap seam" hold by construction
   rather than by a rejection test - a lane through the margin is a road across the seam by another name.

   Two things are worth knowing. **A* over a *uniform* cost field has no unique shortest path**, so the first
   lanes came out as right angles: at sea a straight run and any monotone staircase cost the same and the winner
   is decided by the order `D8` lists its neighbours. That is the ruled hotspot chain and the rectangular
   coastline in a third form, and string-pulling fixes it at the source. And **corner-cutting a taut lane put one
   on land**, because Chaikin moves vertices inward and a lane rounding a headland has its corner cut into the
   headland; a road can be smoothed freely because it stamps a corridor wherever it goes, and a lane cannot.
8. **Lots front the streets directly; there are no blocks.** The design goes street graph → *faces* →
   blocks → recursive subdivision, then asks for a check that every lot has street frontage. Plots are
   instead laid along both sides of every street by arc length and rejected where they would overlap
   another plot or reach across a street behind them.

   The first version did it the design's way, and the `town` tool found what was wrong with it. Faces
   exist only because the ring streets close them, so a river crossing a town removed a few ring
   segments, broke each ring's cycle, and a broken ring encloses nothing: **one channel took a city
   from 574 plots to 68**, and every plot it lost was on a block whose boundary the river had merely
   nicked. Both outcomes look like a town on a map. Fronting the streets has no such failure mode —
   losing a street costs exactly the plots on it — and frontage stays a property of the construction
   rather than something to verify. What is lost is a *block* as something to reason about: zoning a
   whole quarter as a craft district, or putting a market in the one open face at the centre.
   `StreetGraph.faces` and its half-edge traversal were deleted rather than left unused; the
   planarisation they needed stays, because the chains and the overlap tests need it too.
   (`civ/StreetNetwork.kt`)

Also unbuilt, and smaller: caves (the design's cave systems are vector features that nothing emits, and see
the note on subtraction under *Still missing*), navigable rivers as cheap trade-graph edges, and the
place → route → regrow → replace settlement iteration (single pass).

New with steps 8–10, and worth knowing before reading a town: **buildings are capped at 1 200 per
settlement**, and a city of twenty thousand wants four thousand. Lots are assigned in descending land
value, so what a cap keeps is the centre — the part a player walks through — and what it drops is the
outer residential ring. `./gradlew :worldgen:town` prints wanted against built and names which of the
two limits bound, because a silently truncated town reads as a small one. **Buildings have no interior
detail**: four walls, a doorway, a floor and a pitched roof, from a shape grammar that is a stored seed
and nothing that reads it yet. And **no NPC is ever instantiated** — the economy produces the
substrate they would be made of (occupations, kinship, wealth, a small-world social graph, the events a
household plausibly knows) as pure functions in `pop/Households.kt`, on the argument that a *runtime*
system should own a living NPC and worldgen should own what it is made of.

### Running it

```
./gradlew :worldgen:test                                    # unit tests
./gradlew :worldgen:viewer                                  # interactive layer/feature inspector
./gradlew :worldgen:viewer -Pgenesis                        # ...on the world zone-server boots
./gradlew :worldgen:viewerExport -Pout=build/viewer         # same, rendered to PNGs; works over SSH
./gradlew :worldgen:invariants -Pseeds=200 -Pcells=256      # seed sweep against the invariants
./gradlew :worldgen:probe -Pcells=128 -Px=32000 -Py=32000   # one 48 m window as text, a character per voxel
./gradlew :worldgen:probe -Pon=river_channel -Pnth=0        # ...centred on a feature too thin to find otherwise
./gradlew :worldgen:probe -Pchannels=1                      # channel width and depth against the voxel grid
./gradlew :worldgen:probe -Psurvey=12                       # the most mixed surface patches in the world
./gradlew :worldgen:probe -Pdroplets                        # ...with chunk-scale droplet erosion on (ships off: cost)
./gradlew :worldgen:town                                    # one settlement: layout, economy, history, a map
./gradlew :worldgen:town -Pcensus                           # ...every settlement in one table instead
./gradlew :worldgen:town -Pnth=3 -Pwhy                      # ...and why every trade is or is not there
./gradlew :worldgen:town -Pruin                             # ...somewhere history destroyed
./gradlew :worldgen:chronicle -Pall                         # the world's history, at length
./gradlew :worldgen:chronicle -Pyear=430                    # ...as it stood in one year
./gradlew :worldgen:chronicle -Pquests                      # ...the threads it left unresolved
```

### Two tools for steps 8 to 10, and the scale that needed them

`town` fills a gap that was structural rather than incidental. The viewer renders a whole world into a
few hundred pixels, so a town is one pixel; the probe prints 48 metres, so a town is four hundred
probes. **A town is 200 to 1 500 metres across — exactly between the two** — and everything step 8
produces lives there. A street network that came out as a tree with no plots on it is indistinguishable
from a correct one at world scale and from open ground at voxel scale.

So it renders one settlement at about a metre per pixel **from the materialised voxel surface**, not
from the plan, on the same argument the seam check rests on: a view drawn from the plan agrees with the
plan by construction and would happily show a correct town whose chunks contain nothing.

It earned itself immediately. Every one of these was found by running it and reading the output, and
none of them was visible on a world map:

| What the tool said | What was wrong |
|---|---|
| `1 standing, 27 never settled` | Expansion tested a civ's *aggregate* occupancy, so founding a settlement pushed the civ back under its own threshold. One city, twenty-seven empty sites, for a thousand years. |
| eight plagues in one timeline | Plague fired about once every 66 years for a city of five thousand, which kept it below the expansion threshold permanently. |
| `90 built, 882 wanted` | Lots were cut from street-graph faces, and one river through the town broke every ring's cycle. See deviation 8. |
| `farm 23 132 (100.0%)` and `food capacity 0` | The catchment indexed the temperature layer with kilometre-grid coordinates. Climate runs four times coarser, so the index *clamped* and every catchment in the world read the polar temperature at the grid's corner. |
| `craft 2 366 of 4 852` | Non-farm population was allocated in proportion to what the shop roster demanded, normalised to consume every spare hand. |
| `road traffic 0.00` for the largest city | Road tier was read from station zero's half-width, which is driven to zero over a bridged crossing — and this city's road crossed a river near its start. |
| a 2 400-pixel image of nothing | `ChunkSurfaceField` refuses a view over its chunk budget, correctly, and the tool wrote the blank PNG without printing the refusal. |

`chronicle` is text rather than a view, and deliberately: the questions asked of a history are "what
happened here", "who did this", "where did this sword come from", and every one is answered by
sentences in year order. What *is* spatial about history already appears in the world viewer, because
the exhaustive `when` over `FeatureKind` in `MapRenderer.colorOf` forced a colour for `RUIN`,
`BATTLEFIELD`, `TOMB` and `MONUMENT` the moment those kinds existed — which is the third time that
compiler check has paid for itself.

Its `-Pyear` view is the **history scrubbing** the tooling section asks for, in the form the data
supports. Every settlement carries a founding year and an abandonment year, so "did this place exist
then" is a comparison; rendering it as a map per year would be a fourth view of the same comparison.
`-Pquests` mines the log for unresolved threads — an artifact lost and never recovered, a razed
settlement nobody reclaimed, a figure with no known grave — which is the raw material the design asks
for and the part that has to come out of the simulation rather than out of a template.

Every tool takes `-Pseed` and `-Pcells`; `viewer`, `viewerExport` and `probe` also take `-Pgenesis`, which
reads `zone-server`'s `worldgen:` block and generates *that* world instead of the demo one. Combine them and
the explicit property wins — `-Pgenesis -Pseed=42` is the server's world with another seed.

`-Pgenesis` exists because matching the seed was not enough. The demo world wraps only X and Genesis wraps
both axes, and the wrap is read by every distance transform, flow route and vector feature the pipeline lays
down, so `-Pseed=11753242 -Pcells=128` produced a world with the server's seed, the server's size, and
different coastlines — with nothing on screen to say so. `viewer/WorldArgs.kt` therefore carries a flag for
every field in `WorldConfig.shapeVersion`, and `buildSrc/src/main/kotlin/WorldGenSettings.kt` fills them in
from the server's configuration file rather than from a second copy of it. A `worldgen` setting it does not
know how to forward fails the build, because a birth setting that silently does not reach the viewer puts it
back to showing a world that merely resembles the one being debugged.

The probe is the companion to the viewer and covers its blind spot: the viewer renders the whole world, so it
cannot show anything narrower than a kilometre, which is exactly the size of thing that looks wrong once the
client draws it at a metre per voxel. Rivers rendering as dashed lines were found this way.

The viewer is the primary debugging tool and has earned it — a road running dead straight across open
ocean and a mis-classification of inland troughs as fjords were both found by looking at an exported
PNG, not by a test.

The first field in the list is **`world map`**, and it is the one view that is not diagnostic. Every other
field shows one stage in isolation, which is what makes a wrong value in it obvious and is also why a field at
a time will tell you that precipitation is plausible and that biomes are plausible and never tell you that the
world does not read as a place. `viewer/WorldMapField.kt` composes them: biome colours on land, bathymetry at
sea, lakes shaded by depth, ice washed over what is under it, relief shading from the surface height, and the
vector overlay — rivers, roads, settlements, coastlines, faults — on top, which needed no new code because
features were always drawn over whichever field was showing. What was missing was a field worth drawing them
on. It is a `CompositeField`, the escape hatch from one-number-through-one-palette, and the mechanism that
makes it work is the split between colour and value: `rgbAt` decides what a place looks like, `valueAt` returns
the height of the surface being coloured, and hillshading reads the latter. A categorical *palette* cannot be
shaded — which is why the biome map is flat — but a categorical *colour over a real surface* can be.

`valueAt` returns the water surface where there is standing water and the ground where there is not, so the
sea shades flat and the coastline stays crisp instead of the seabed's relief showing through the water as
though it were land. Depth does not leave the picture; it moves into the colour, where it belongs.

The seam stress view runs on every export and prints, e.g.,
`SeamCheck: clean - 64 chunks, 3584 shared columns agree`. It generates a block of chunks
independently and fails if adjacent chunks disagree on any shared column's height.

Invariants currently asserted per seed: layers are finite; the land fraction is plausible; normalised
layers stay in range; the four seasonal precipitation fields sum to the annual one; discharge grows downstream;
river beds descend; the world has standing water; lakes stand above their beds; every closed basin can hold
water; water is where the biome says it is; feature bounds contain their geometry; no settlement is in the sea;
settlements respect their tier's separation; deposits are well formed; every fjord sill is shallower than its
landward basin; the ocean margin contains neither land nor settlements; every sea lane stays over open water and
clear of that margin; and every built site stands on dry land, names a real deposit if it is a mine, and keeps
clear of the towns it is defined by not being part of.

**Two of those are deliberately not existence claims**, and the distinction is the one the lake story below
teaches. A world can legitimately have no sea lane - a lane needs two cities a road cannot join - and no built
site, since whether a civilisation ever builds one depends on a thousand years of technology and war. Asserting
existence unconditionally would fail on most seeds; asserting it conditionally would be exactly the vacuous
check this section warns about. So existence is pinned to seeds that *do* have them, in `civ/SeaLaneTest` and
`history/SpecialSitesTest`, and the sweep asserts only the properties.

`landFraction` is a single shared function rather than a measurement each caller makes: it had been
reimplemented in the check, the pipeline test and the sweep, and the copies had drifted — one of them tested
`> 0f` instead of against the world's actual sea level. Two of those callers now also *report* it rather than
only asserting on it, which is what makes a bad seed visible rather than merely legal.

**Two of these used to be vacuous, and it is worth knowing how long that went unnoticed.** Nothing generated a
lake, so `lakes stand above their beds` inspected no cells and passed; and no world had an endorheic basin, so
the salt-lake half of the deposit rules never fired. A check that skips its subject and reports success is the
same failure the ocean-margin story below describes, one step further along — registered, but with nothing to
assert against. Both have subjects now that the glacial carve reaches the raster.

Lake *existence* is now asserted per seed, and how it got there is the more useful half. When glacial
overdeepening was the only basin source it could only be asserted across a sweep: a trough that runs to the sea
drains rather than impounding, so a small world with four of them honestly holds no water, and measured over 120
seeds at 192 km, **five of them had none**. Asserting existence per seed would have failed on honest worlds. What
made the unconditional statement available was not a stricter check but a second source with a construction
behind it — `geo/ClosedBasins` puts a closed depression on every continent by arithmetic rather than by tuning —
and the same 120 seeds now come out **0 of 120 dry, median 11 lakes against a previous median of 5**.

Re-measured after the seasonal, biome, sea lane, special site and droplet work — all five of which change the
generator upstream of hydrology or downstream of it — the spread holds: **200 seeds at 192 km give a median of 11
lakes over a range of 2 to 42, and 200 at 256 km a median of 21 over 3 to 85, with 0 of 400 worlds dry.** Land
fraction sits at a median of 0.502 in both, ranging 0.486 to 0.715.

The *count* is reported by both `viewer` and `invariants` on every run regardless, because what killed this for a
year was not a weak assertion but the absence of any number at all. The two lake sources are reported separately
for the same reason: ice gave the 512 km world 115 lakes while leaving the 128 km world with none, so a single
total would have read as "lakes are working" on whichever world it was being read on.

For steps 8–10: founding and abandonment years are ordered and a ruin has nobody in it; no event names
a settlement before it was founded or after it emptied; no surviving event cites a pruned cause; every
artifact's provenance runs forwards and ends somewhere that exists; every ruin marker matches a
settlement the log emptied, and there are as many of one as the other; no structural marker reaches
past the margin chunk generation queries with; every building names a standing settlement and stands
inside it; nothing is built in water; a walled settlement has a gate; every standing settlement's
catchment yields food; employment sums to the population; businesses name a real trade in a standing
place; and a roadside inn is beside a road and clear of any town.

> **The ocean-margin check had been written and never registered**, so the property this list claims is
> asserted was failing on every seed and nothing was looking. Registering it found two real bugs, both
> pre-existing and neither belonging to the stages added around it. Erosion adds uplift to *every* cell
> including the margin, which has nowhere to drain to and so keeps all of it — over two hundred
> timesteps that lifted a strip back above the waterline, so the margin is now reapplied after erosion.
> And `OceanBorder` blended *towards* deep water reaching the natural elevation exactly at the margin's
> inner boundary, which cannot guarantee water, because the natural elevation there is the interior and
> the interior is land: measured, a cell a kilometre inside the margin kept about two thirds of its
> height. The blend now runs out over a coastal shelf *beyond* the margin, and inside it a smoothly
> rising ceiling holds the ground under the waterline.
>
> The lesson is not about the ocean margin. **An invariant that is written but not registered is
> documentation, and this document was citing it as a guarantee.**

---

## Core Principle: Layered Deterministic Pipeline

Every stage is a pure function `f(seed, region, upstream_layers) → layer_data`. This gives you distributability for free: any node can compute any chunk if it can fetch its dependencies.

```
Tectonics → Heightfield → Climate → Hydrology → Erosion → Biomes
    → Resources → Habitability → Settlements → Roads → Buildings
    → NPCs → History/Story → Runtime Chunks
```

### Three representations, not two

The original two-scale model (coarse world grid + fine chunk grid) is insufficient. Anything whose characteristic width is under ~5 km — river channels, glacial troughs, fjords, canyons, cliffs, alluvial fan lobes, moraines, roads — cannot be represented on a 1 km grid without losing the features that make it recognizable. A 3-cell U-kernel at 1 km/cell produces a 3 km-wide gouge with a one-cell floor; real glacial troughs are 1–3 km wide *total*, and their diagnostic traits (flat floor, near-vertical walls, truncated spurs, hanging tributaries) are entirely sub-cell.

So the world is stored as **three complementary representations**:

| Representation | Content | Resolution | Storage |
|---|---|---|---|
| **Raster fields** | Elevation, temperature, precipitation, biome, soil, rock stratigraphy | ~1 km cells, global | Dense arrays, immutable after world creation |
| **Vector features** | Rivers, glacial troughs, fjords, roads, coastlines, faults, cave systems, settlement footprints | Polylines/polygons with per-station attributes, resolution-independent | Sparse geometry + spatial index |
| **Voxel chunks** | Materialized blocks | 32×32×256 voxels | Generated on demand, cached, RLE-encoded |

> **Implemented with one addition: a voxel is a material *and an occupancy fraction*,** not a material alone.
> `Occupancy` stores how much of the voxel its material fills, 0–255.
>
> The reason is that this table's own logic demands it. The whole point of the vector tier is that a feature's
> geometry is resolution-independent: a river channel is carved to sub-metre precision against one continuous
> polyline. Materialising that to a solid-or-empty voxel throws the precision away at the very last step and
> replaces it with metre stair-steps - which is a strange thing to do after three sections of argument about
> preserving it. A surface at 40.3 m is thirty percent of the voxel spanning 40 to 41, and the client can
> reconstruct the original height to a fifth of a centimetre. `StandardWorldTest` asserts exactly that,
> end to end against the real pipeline.
>
> It is also the same argument the [derived structures](#derived-structures--never-query-voxels-in-hot-paths)
> section makes about the opacity grid one level up, so the two tiers now agree.
>
> Consequences worth knowing: heights in `ColumnSummary` and surfaces in `WalkableTile` are continuous rather
> than voxel indices, `AgentProfile.maxStep` is fractional, and the opacity grid is occupancy-weighted. The
> last one matters most - an agent would otherwise refuse to walk up a one-in-five gradient, because rounded
> to voxel indices a gentle ramp is a sequence of one-voxel cliffs.

Raster fields carry the things that need global coherence and are genuinely smooth at kilometre scale. Vector features carry the things that are narrow, linear, and whose *shape* matters more than their exact grid alignment. Voxels are the final materialization, produced by evaluating both.

The rule of thumb: **if a feature is narrower than ~3 coarse cells, it belongs in the vector layer.** The coarse pass decides *where* it goes and *how big* it is; the fine pass decides *what it looks like*.

Two coordinate scales remain for the raster tier:

- **World scale** (global, coarse): 1 cell = 1–4 km. A 4096×4096 world map is ~16M cells — fits in RAM on one node, computed once at world-birth, then immutable and replicated.
- **Chunk scale** (local, fine): 32×32×256 voxels, generated lazily.

The expensive global-coherence work (plates, rivers, history) happens once at coarse scale. Chunk generation is then embarrassingly parallel because every chunk only needs a small neighborhood of the coarse map plus the vector features whose bounding boxes intersect it.

### Resolution is a per-stage property

The `Stage` interface must carry `resolution` separately from `scale`. Climate wants a coarser grid than 1 km (advection over a fine grid is wasted work). Nested-grid stages want finer. Forcing one resolution on everything is wasteful in both directions.

Where a nested grid is genuinely required (rare — vector features handle most cases), a stage declares a finer resolution and a region mask; the scheduler runs it only over masked regions, taking boundary conditions from the coarse parent. Keep this capability but reach for it last.

---

## Stage 1: Tectonics & Base Heightfield

> **Implemented** in `geo/TectonicsStage.kt`, `geo/Plates.kt`, `geo/BoundaryTracer.kt`. Rock hardness is
> a scalar raster; the *vertical* layer stack the last paragraph asks for is derived analytically at chunk
> time from elevation, hardness and plate id (`voxel/Stratigraphy.kt`) rather than stored per cell — a
> descriptor stack per cell over 16M cells is a large storage cost for something that is a pure function of
> three layers already present.
>
> Four things here are not in the design above and all of them came out of running it at 128 km — see
> [Making the world land-dominated broke the climate](#making-the-world-land-dominated-broke-the-climate-and-the-climate-was-already-broken)
> for the arguments. The land fraction is enforced by a **bisection over the finished heights** rather than a
> quantile over the interior; `upliftDryLand` gives every dry cell an uplift floor so erosion has something to
> carve rather than a plane to flatten; orogeny is scaled by `detailScale` in *both* amplitude and
> boundary-distance so a small world gets ranges with lowland between them rather than one continuous plateau;
> and the plate-spacing floor is scaled too, because at a flat 50 km it bound hard enough on a small world to
> leave it 1.7 continental lobes across.

Don't start from fractal noise — it gives you the "same everywhere" problem. Start from plates.

1. Poisson-disk sample ~60–200 plate seeds. Voronoi partition with jittered boundaries (add domain-warp noise to the Voronoi distance function so borders aren't polygonal).
2. Each plate: type (oceanic/continental), a drift vector, base elevation, age.
3. Boundary classification per pair: convergent (`dot(drift_a - drift_b, normal) < 0`), divergent, transform.
4. Boundary → orogeny: convergent continental-continental = fold mountains (high, wide); oceanic-continental = volcanic arc + trench; divergent = rift valley + mid-ocean ridge.
5. Diffuse boundary stress into the interior with a distance-weighted kernel — this gives natural foothill falloff. Use a jump-flood distance transform over the boundary set; O(n log n) on GPU or trivially tiled on CPU.
6. Add multi-octave ridged noise modulated by plate age (old plates = eroded, smooth; young = rough) and hotspot chains (volcanic island arcs from a few point sources drifting along plate vectors).

**Vector output:** plate boundaries are emitted as polylines with attributes (`type`, `convergence_rate`, `age`). Downstream stages use them for fault-line placement, volcanic vents, and ore genesis without re-deriving them from the raster.

**Raster output:** `elevation[]`, `plate_id[]`, `rock_hardness[]`, `crust_age[]`.

**Rock hardness matters** — it's what makes erosion produce interesting terrain rather than uniform mush. Stratify it: assign each cell a vertical stack of layer descriptors (thickness, hardness, type). Sedimentary basins get soft layers, shields get hard granite.

---

## Stage 2: Climate

> **Implemented** in `climate/ClimateStage.kt`, `climate/Winds.kt`, at four times the heightfield's cell
> size. Two seasonal passes are run — a summer and a winter wind field, which is the cheapest number that
> produces a monsoon — but only their annual sum and a scalar spread survive as layers
> (`PRECIPITATION`, `PRECIPITATION_SEASONALITY`). So the seasonal fields exist and are discarded, which is
> enough for biomes and would not be enough for agriculture-by-month.
>
> **Two bugs lived in this stage that a mostly-ocean world had hidden**, and both are argued at length in
> [Making the world land-dominated broke the climate](#making-the-world-land-dominated-broke-the-climate-and-the-climate-was-already-broken):
> the wind model's belt boundary was a *step function*, which put a full-width discontinuity in the
> precipitation field that `BiomeStage` then drew as six perfectly straight stripes across every continent; and
> the orographic coefficient was steep enough to strip the air completely at the first range while nothing
> returned any water to it, so every continental interior was the rain shadow of one mountain. The fixes are
> `Winds.eastwardShare` — a blended share rather than a direction, swept both ways per row — and
> `landEvaporationRate`, continental moisture recycling. `sweepRow` also gained a spin-up lap on a wrapping
> world, without which each row's start column got exactly zero rain and both map edges had a dry stripe.

Order matters: you need elevation before climate, and climate before hydrology.

**Temperature:** base = latitude curve − lapse_rate × elevation + noise. Add ocean thermal inertia (distance-to-ocean smooths the seasonal range).

**Wind:** simplified Hadley/Ferrel/Polar cells give you prevailing direction bands by latitude. Deflect with Coriolis. Optionally perturb around mountain masses.

**Precipitation via orographic advection** — this is the one that pays off visually:

```
for each cell in wind-direction sweep order:
    moisture = incoming_moisture_from_upwind
    if over water: moisture += evaporation(temperature)
    dh = elevation - upwind_elevation
    if dh > 0:
        rain = moisture * orographic_coefficient * dh
        moisture -= rain
    else:
        rain = moisture * base_rate  # plus rain-shadow recovery
    precipitation[cell] = rain
```

Sweeping in wind order over the whole map is a sequential scan, but it's cheap (O(n)) and only done once at world creation. Run 3–4 seasonal passes with different wind fields and store monthly precipitation — you'll want it for both biomes and agriculture-driven settlement scoring.

Rain shadows fall out of this automatically, and they're a huge believability win: deserts appear *behind* mountains, not randomly.

Climate is a legitimate candidate for a **coarser** grid than the heightfield — 4 km cells are plenty for advection. Downsample elevation into the climate grid, run there, upsample results with bicubic interpolation.

---

## Stage 3: Hydrology (Rivers & Lakes)

> **Implemented** in `hydro/`: priority-flood in `Lakes.kt`, D8 and accumulation in `FlowRouting.kt`, the
> river graph and its station tables in `RiverNetwork.kt`, vector-level smoothing and meandering in
> `Meander.kt`. Lakes stay in the raster tier and oxbows are not cut — see deviations 3 and 6.
>
> **Channel initiation reads slope as well as catchment area.** Point 4 below says "cells where
> `accumulation > threshold`", and an area-only threshold is first reached on the coastal plain, which is
> where it put every channel head: the map came out as combs of short parallel streams off every shore with
> the uplands blank. `channelSlopeExponent` adds the Montgomery–Dietrich `A·S^n > constant`, expressed as a
> ratio against this world's own mean land slope so it redistributes thresholds rather than discounting them
> globally. It needed its own slope field to do it — at kilometre cells the steepest ground in the world is
> the *shoreline*, so `Grid.gradient` made the coast the easiest place to start a channel, and `landSlopes`
> clamps the neighbours at sea level to ask how steep the *land* is.
>
> **Lakes exist, and for a long time none did.** `Lakes.kt` gates a lake on priority-flood having had to
> *raise* a cell by more than half a metre, and `ErosionStage.incise` clamps every cell to at or above its
> receiver — so the surface it hands over is depression-free by construction and there was nothing left to
> raise. `Lakes.kt` was complete, unit-tested, and had never once received a basin. What supplies one now is
> glacial overdeepening, since a trough floor is a running minimum with the overdeepening subtracted on top;
> the reference world gets 115 lakes where it had none, 25 of them endorheic. Ice alone still left a 128 km
> world dry, so `geo/ClosedBasins.kt` supplies the second source — a graben or an interior sag, carved back into
> the erosion surface after the loop that conditioned it flat. Genesis goes from 0 lakes to 7.

This is where most generators fail. The correct approach:

1. **Depression filling with Priority-Flood + epsilon.** Barnes' variant: push all boundary cells into a priority queue by elevation, pop lowest, flood neighbors raising them to `max(neighbor, current + ε)`. O(n log n), guarantees every cell drains to the ocean, and the ε keeps flow directions well-defined. Record which cells were raised and by how much — those are your **lake basins**, and the fill depth is the lake surface level.
2. **Flow direction:** D8 (steepest of 8 neighbors) is fine and cheap. D-infinity gives smoother alluvial fans if you care.
3. **Flow accumulation:** process cells in descending elevation order, pushing discharge downstream. Weight the source term by local precipitation, not uniform — so rivers in wet regions are genuinely bigger.
4. **River extraction:** cells where `accumulation > threshold` become rivers. Threshold should scale with precipitation so arid regions get sparse drainage.
5. **Lakes:** basin cells below fill level. Determine outlet (the saddle point where the fill escaped), check if endorheic (no outlet to ocean → salt lake, evaporation-limited; compute steady-state level from inflow vs. evaporation area).

### Rivers become vector features here

Do not stop at a raster river mask. **Trace the flow network into a river graph** and store it as the authoritative river representation:

```
RiverNetwork {
    nodes: [ { pos, elevation, discharge, is_confluence, is_mouth, lake_id? } ]
    reaches: [ Reach {
        id, upstream_node, downstream_node,
        centerline: Polyline,          // resampled to ~50–100 m stations
        stations: [ {
            s,                          // arc length from reach start
            pos, elevation,
            discharge,                  // m³/s
            width,                      // w ∝ Q^0.5, hydraulic geometry
            depth,                      // d ∝ Q^0.4
            bank_height, slope,
            meander_phase,              // for deterministic lateral offset
            substrate                   // gravel / sand / bedrock, from stratigraphy
        } ],
        strahler_order
    } ]
}
```

The centerline comes from the D8 flow path, then gets **smoothed and meandered** at vector level — not at chunk level. Chaikin or B-spline smoothing removes the D8 staircase; a curvature-driven meander model (or simply a multi-octave 1D noise offset perpendicular to the local tangent, with amplitude scaled by `width` and inversely by `slope`) adds sinuosity. Oxbows are cut where the meander model produces a loop whose neck falls below a threshold — record the abandoned loop as a separate feature (`oxbow_lake`) rather than discarding it.

Doing meandering at vector level rather than chunk level is the critical fix. Chunk-level meandering cannot produce a continuous channel across a chunk boundary, because each chunk would perturb the centerline independently. At vector level the centerline is one continuous object; chunks merely sample it.

Priority-Flood is inherently global (water doesn't respect your chunk boundaries), so run it once at world scale on a single node. It's a few seconds for 16M cells.

---

## Stage 4: Erosion

> **Implemented** in `geo/ErosionStage.kt` (stream power, thermal relaxation, sediment) and
> `geo/GlacialStage.kt` (coarse ice flow + vector extraction of troughs, fjords, cirques and moraines).
> Three deviations apply here: detail erosion is analytic rather than droplet-based (1), fans and deltas
> are raster (2), and rivers do not inherit trough floors (6).

Run **at world scale** for the large landforms, then **detail erosion per-chunk** at generation time for local realism.

### Hydraulic erosion — Stream Power Law

The physically-grounded choice:

```
dz/dt = U - K * A^m * S^n
```

where `A` = drainage area (you already have it), `S` = slope, `U` = uplift (from tectonics), `m ≈ 0.5`, `n ≈ 1`, `K` = erodibility (from your rock hardness stratigraphy).

Solve implicitly with the **Braun-Willett O(n) algorithm** — it's stable at large timesteps and doesn't need thousands of iterations. Process the drainage tree in stack order (upstream-to-downstream), solve the implicit equation per node. Run 50–200 timesteps of geological time. This produces genuinely realistic dendritic drainage networks and V-shaped valleys, and it's fast.

Because erosion changes elevation, hydrology and erosion should be **iterated 2–3 times**: flow → erode → refill depressions → reflow. Rivers cut their valleys, and valleys redirect rivers.

### Thermal erosion / mass wasting

Repeated relaxation: if slope between neighbors exceeds the talus angle for that material, move material downhill. This creates scree slopes and caps cliff angles. Cheap, parallelizes trivially with double-buffering, converges in ~20 iterations.

### Sediment deposition

Track sediment load along the flow network. Where slope drops sharply (mountain front → plain) deposit → alluvial fans. Where rivers meet the ocean → deltas. Deposit fine sediment in lake basins. **Emit fans and deltas as vector polygons** with a radial thickness profile, not as raster edits — they're 1–5 km features with sharp boundaries and internal lobe structure that a 1 km raster smears out.

### Glacial erosion — vector, not raster

The 1 km grid cannot represent glacial troughs. Handle it as a two-part process:

**Coarse pass (raster, 1 km):** compute where ice exists and how it flows. Accumulate ice where `temperature < 0` at elevation with sufficient precipitation; flow downhill by ice-surface gradient using a shallow-ice approximation; compute ice discharge and basal sliding velocity per cell. This is cheap and only needs to be roughly right — it decides *where* glaciers are, not what they look like.

**Vector extraction:** trace ice flowlines into a glacier network, structurally identical to the river network:

```
GlacialNetwork {
    troughs: [ Trough {
        centerline: Polyline,           // resampled to ~100 m stations
        stations: [ {
            s, pos,
            floor_elevation,            // overdeepened where erosion was strong
            width_top, width_floor,     // U-profile parameters
            wall_angle,
            ice_thickness_max,          // at glacial maximum, for trimline placement
            erosion_depth
        } ],
        tributaries: [ trough_id ],     // hanging valleys: floor above trunk floor
        terminus: { pos, type }         // moraine / calving / fjord mouth
    } ]
    cirques:  [ { pos, radius, headwall_angle, tarn? } ]
    moraines: [ Polyline + height profile ]
    fjords:   [ Fjord {
        trough_id,                      // fjords are drowned troughs
        sill: { s, depth },             // shallow bar at the mouth — the defining feature
        basin_depths: [ (s, depth) ],   // overdeepened basins behind the sill
        sea_level_at_flooding
    } ]
}
```

The U-profile is then applied **analytically at chunk scale**. For a sample point `p`:

1. Query the spatial index for troughs whose corridor bbox contains `p`.
2. For each hit, find the nearest point on the centerline → `(s, lateral_distance d)`.
3. Interpolate station attributes at `s`.
4. Evaluate the cross-section profile: a parabolic or power-law U-shape,
   `z_trough(d) = floor_elevation + (d / half_width)^p * (wall_height)`, with `p ≈ 2` for a classic U and higher `p` for steeper walls.
5. Blend: `z = min(z_base, z_trough)` inside the corridor, with a smooth falloff over the outer 10–20% of the corridor width to avoid a hard rim.

Truncated spurs fall out for free: the trough carves ridges that cross it. Hanging valleys fall out because tributary troughs have floor elevations set independently of the trunk. Fjord sills are placed exactly where the vector says, at whatever resolution the chunk wants.

The cost of this approach: the coarse ice-flow pass can't see the fine geometry it's implying, so the ice dynamics are approximate. For a game that's entirely fine. For science it wouldn't be.

**Glaciation is optional.** If you cut scope, cut this — but keep the vector machinery, because rivers need it anyway.

### Chunk-scale detail erosion

At chunk generation, run a few hundred **particle-based droplet erosion** steps on the local heightfield patch (seeded from the chunk hash, with the coarse map as boundary condition). Overlap chunks by a margin (e.g. 8 cells) and blend in the overlap to prevent seams. This is fully parallel.

Important ordering: detail erosion runs on the **base heightfield only**, before vector features are stamped. Otherwise droplets erode the river channel you just carved and it looks wrong.

---

## Stage 5: Biomes

> **Implemented** in `bio/BiomeStage.kt`, `bio/Biome.kt`: weighted-prototype classifier over nine axes,
> plus the edge biomes, soil fertility and soil depth.
>
> Two things this section asks for that are only half here. **Only the winning biome is stored, not the
> top-2 pair** — `BIOME_CONFIDENCE` is derived from the ratio of the best two prototype distances, so a
> consumer can dither or soften a boundary, but it cannot blend between the two specific biomes because the
> runner-up's identity is discarded. And **edge biomes are computed on the raster, not from the vector
> layer**: riparian strips come from a Euclidean distance transform over high-discharge cells and beaches
> from one over ocean cells, rather than from buffering the river polylines and a coastline polyline. Both
> are therefore kilometre-cell-quantised bands rather than the crisp resolution-free ones the design argues
> for — see deviation 4.

Don't use a naive Whittaker lookup — it produces obvious rectangular bands in parameter space.

Use a **weighted multi-attribute classifier**: each biome is a prototype in a feature space of `(temperature, precipitation, seasonality, elevation, slope, drainage/soil-wetness, soil_fertility, latitude, distance_to_water)`. Score each biome with a weighted distance and take the best. Store the top-2 with their blend weight so transitions are gradients, not hard lines.

Add **edge biomes** driven by adjacency rather than climate. These are naturally vector-derived: riparian corridors are a buffer around the river network (a mesic strip through a desert is a strong believability signal), beaches are a buffer inside the coastline polyline, marshes appear at low-slope river mouths, cliff/badlands on high-slope soft rock. Computing these from the vector layer rather than the raster gives you crisp, correctly-shaped bands at any resolution.

Soil fertility is worth computing explicitly: `f(sediment_deposition, rock_type_weathering, precipitation, slope, biome_litter)`. It feeds directly into settlement placement — civilizations grow where food grows.

---

## Stage 6: Resources

> **Implemented** in `resource/ResourceStage.kt`, `resource/Deposits.kt`. Fourteen resource types placed
> by **thinning a Poisson process** with a per-type geological suitability field: copper on the convergent
> arcs (read from the `FAULT` polylines tectonics emitted, not re-derived), tin in old hard crust, iron in
> ancient shields, coal in wet lowland sedimentary basins, salt on endorheic lake beds, and placer gold
> traced downstream of each lode along the flow network. Deposits are sparse `PointMarker` features;
> per-voxel ore is materialised at chunk time by hashing the *world* position (`voxel/ChunkStructures.kt`).
> Oil and gas are not generated — nothing downstream would use them.

Resources should be *causally* placed, not sprinkled. This makes the world feel discoverable and makes settlement placement meaningful.

- **Metal ores:** near convergent boundaries (use the vector fault polylines from Stage 1) and volcanic/intrusive rock. Porphyry copper at arc volcanism, tin/tungsten in granite plutons, banded iron in ancient sedimentary shields.
- **Coal:** ancient swamp basins — needs a paleo-climate pass, or simply sedimentary basins with historically-high precipitation.
- **Oil/gas:** deep sedimentary basins with a cap layer.
- **Placer gold:** in river gravels *downstream* of hard-rock gold — trace the river network from lode deposits. Players love this because it's a real prospecting mechanic, and the vector river graph makes the traversal trivial.
- **Stone/clay/salt:** stone at exposed hard rock, clay in floodplains, salt at endorheic lake beds and evaporite basins.
- **Timber:** biome-derived, with species and quality.
- **Fertile land / fresh water / fish:** biome + hydrology derived.

Model each deposit as a *node* in the world graph: `{position, type, quantity, richness, depth, discovered_by}` — generated from a Poisson process whose intensity is a function of the geological suitability field. Storing deposits as sparse entities rather than per-voxel fields is essential; per-voxel ore only materializes at chunk generation by sampling the deposit's spatial distribution. This is the vector approach again, applied to point features rather than linear ones.

---

## Stage 7: Settlement Placement

> **Implemented** in `civ/`. `HabitabilityStage.kt` + `Terms.kt` compute all the terms below; `Culture.kt`
> holds four cultures with different weight vectors and four settlement tiers; `SettlementStage.kt` places
> greedily tier by tier with per-tier separation, scores each site against all four cultures to decide
> whose town it is, prunes the trade graph to a **Gabriel graph**, routes with A* over `MOVEMENT_COST`
> (`RouteFinder.kt`), and tiers roads by gravity-model traffic over the pruned graph.
>
> Not done here: the place → route → regrow → replace iteration (single pass), navigable rivers as cheap
> boat edges, and the special sites — mines, monasteries, forts, lighthouses. Bridges *are* emitted, from
> a genuine polyline-polyline intersection (`vector/Intersections.kt`); see the note below the algorithm.

### Habitability field

Compute a scalar suitability map:

```
H = w1·fresh_water_access      (distance to river/lake vector, exponential falloff)
  + w2·soil_fertility
  + w3·arable_slope            (flat is good, but not swamp-flat)
  + w4·defensibility           (local elevation prominence, river bends, peninsulas, passes)
  + w5·resource_proximity      (weighted by resource value)
  + w6·climate_comfort         (temperature/precipitation habitability curve)
  + w7·coastal_harbor          (sheltered coastline: high coastline concavity + deep water nearby)
  - w8·hazard                  (floodplain, volcanic, avalanche)
```

Weights should differ **per culture** — this is the cheapest way to get civilizational variety. A seafaring culture heavily weights harbors; a steppe culture weights grazing land and discounts arable soil.

Note that several terms are best computed against vector features rather than rasters: water access from distance-to-river-polyline, harbor quality from coastline curvature, defensibility from river-bend geometry. Fjords in particular produce outstanding harbor scores — deep sheltered water with steep sides — which is exactly why real fjord regions are densely settled at the waterline despite terrible agriculture.

### Placement algorithm — dart-throwing with hierarchy

1. **Cities:** greedy selection over `H` with a minimum-separation radius (~40–80 km). Bias toward *network positions*: river confluences (you have these as explicit nodes in the river graph), river mouths, mountain passes, coastal harbors, the boundary between two different biomes (trade!). Confluences and passes are chokepoints and disproportionately produce real cities.
2. **Towns:** placed on the emerging trade network between cities, at roughly one day's travel spacing along routes.
3. **Villages:** dense fill in high-fertility regions around towns, spacing ~5–15 km (agricultural catchment radius).
4. **Hamlets/farmsteads:** fill remaining arable land.
5. **Special sites:** mines at ore deposits, monasteries in remote defensible spots, forts at borders and passes, lighthouses on headlands, bridges/fords at river crossings on major routes (query the river graph for crossable reaches — low discharge, shallow depth, gravel substrate).

### Trade network

Build a graph over settlements. Edges weighted by **movement cost** (slope-penalized, river-crossing-penalized, forest-penalized) via A* or Dijkstra over the cost field. Prune with a **Gabriel graph** or relative-neighborhood graph so you get a realistic sparse network rather than a complete graph. Rivers are cheap edges (boats) — navigability is a per-reach attribute in the river graph. Then run flow simulation over the network based on resource supply/demand to determine road tier — heavily-trafficked edges become paved highways, light edges are dirt tracks.

**Roads are vector features**, with the same structure as rivers: centerline polyline, per-station width, surface type, embankment/cutting depth. They are stamped at chunk scale exactly like river channels.

Roads should also *feed back* into settlement growth: settlements on major routes get a population bonus. Two or three iterations of (place → route → regrow → replace) produces a much more coherent network than a single pass.

---

## Stage 8: Town Layout & Buildings

> **Implemented** in `civ/TownStage.kt`, `civ/StreetNetwork.kt` and `civ/TownBuildings.kt`, with the blocks
> laid by `voxel/TownStructures.kt`. Both layouts are here — agent growth for an organic town, a clipped
> rotated grid for a planned one, chosen by `Culture.layout` — and both end in the same planarised graph, so
> the plots, the zoning and the buildings are written once. Zoning is one scalar and a sorted list: land value
> from centrality *and* street rank, then functions counted out by quota, which is what makes a village get one
> temple and a city six rather than a threshold giving the village none. The noxious trades really do go
> downwind and downstream, from `Winds.directionAt` and the D8 flow direction at the site.
>
> **Blocks are not implemented, and deliberately** — plots front the streets directly. See deviation 8, and the
> river that took a city from 574 plots to 68.
>
> **Three things about how this reaches the voxels are worth knowing.**
>
> A building is *one* feature, not a pad plus a marker. `vector/FootprintFeature.kt` is an oriented rectangle
> that both flattens the ground it covers and carries the attributes the materialiser needs — which is this
> section's "soft deformation applied to the heightfield before stratification" and makes it impossible for the
> pad and the walls to disagree about where the building is. It is also the geometry type the vector tier has
> been missing, in the ninety percent of a polygon that costs nothing: two dot products put a query point in
> local coordinates and the distance to the boundary is a max of two absolute values. Fans, deltas, lakes and
> coastlines still want a real polygon and still do not have one.
>
> Walls are geometry-only markers, because a wall stands *on* the ground and a heightfield has one height per
> column — the same reason a bridge deck is blocks. **Gates are the gaps between wall stretches** rather than
> features that punch through one, which is why nothing has to reconcile a wall with an opening at chunk time.
>
> Streets are `PolylineFeature`s using `LinearFeatures.road` with a narrower cross-section, so they added no
> geometry code — the same payoff roads got from rivers. What they did need was **paving**: a street is
> `REPLACE`-blended terrain, so it levels the ground and then leaves it *grass*, because the surface cap comes
> from the biome. On a rendered town that made every street invisible, readable only as the gap between two
> rows of buildings.
>
> Walls follow the population at the moment the town was threatened, not today's, so **later growth spills
> outside the circuit** — which is what every walled city that survived its wars ended up with, and is visible
> from a long way off. That is only possible because history runs first.
>
> **A plot is sized for a house, which it was not.** `lotFrontage` was 9 m, which after the setback, the
> inter-building gap and the footprint fill produced a house 6.35 m wide — a shed. At 12.5 m by 18 m of depth
> the same chain gives about 10.4 m by 16.2 m. `peoplePerHectare` was re-measured from 85 to 58 to match,
> because wider plots mean fewer of them per length of street and the density is the thing that has to give.
>
> **A building's footprint now depends on its function.** Every type — market, temple, cottage — was sized
> identically before, so a market hall was a house with a market in it. Each function gets frontage and depth
> multipliers, capped so a building can never outgrow its plot. That in turn needed a **roof-pitch cap**: a
> fixed 40° pitch on a 20 m market hall puts the ridge 8.8 m above the eaves, which is a spire, so wide spans
> shallow their pitch rather than having their rise clamped. Ordinary houses are untouched by both.
>
> **A lot the pad cannot level is skipped**, and the next-best lot takes the building. The site test a lot
> passes reads slope off the kilometre raster, and a building is eighteen metres long — a kilometre-averaged
> slope cannot see a scarp shorter than itself, so a lot could pass and still have metres of relief across its
> own footprint, leaving the materialiser to build a plinth under it. The check therefore has to run *after*
> the building exists and its floor is known, and it predicts the residual rather than measuring it afterwards
> so the caller can fall through instead of losing the building. It measures grading **faded the way the
> grading disc actually fades**, because `builtRadius` puts a great many buildings out in that taper and
> predicting full-strength grading there overstates how level the ground will be by metres.
>
> That prediction is a second copy of `PointFeature.falloff` and `FeatureEvaluator`'s `REPLACE` case, which is
> a poor thing, and it is pinned by `TownStageTest.the ground under a building is level` measuring the finished
> columns. That test has now caught two real faults, neither in the copy itself. The first was the orphaned
> glacial stage carving ground the town stage could not see — a 525 m plinth, see
> [The glacial stage was an orphan](#the-glacial-stage-was-an-orphan-and-fixing-it-produced-the-worlds-first-lakes).
> The second was subtler and had been latent from the start: `WorldGround` predicted the ground from the coarse
> elevation layer, which omits the **detail noise** the chunk tier adds, so the site check predicted a residual
> of zero for lots that finished two metres out and passed them. It now samples `WorldHeightField` — the same
> base surface the chunks sample — so what it levels is what a player stands on.
>
> A third came out of the same seam sweep: `builtRadiusFor` capped the street network at 95% of the graded
> footprint, and a plot hangs 22 m off its street. A city has 45 m of headroom for that and **a village has
> 9.5 m**, so a village whose streets reached the cap put its outermost buildings on ungraded hillside. A share
> was the wrong instrument, because what has to fit in the margin does not scale with the settlement; the cap
> now reserves a plot's full reach.

Per settlement, generated deterministically from `hash(settlement_id)`:

1. **Street network:**
   - Organic/medieval: start from the road entry points (the vector road network tells you exactly where these are), grow with an L-system or agent-based extension (roads extend, branch at intervals, snap to nearby roads within a tolerance, terminate on obstacles). This gives you the irregular radial pattern of real old towns.
   - Grid/planned: for colonial or imperial cultures, a rotated grid clipped to terrain, with the rotation aligned to the dominant approach road.
   - Terrain-following: on slopes, roads become switchbacks/contour-following.
2. **Blocks:** the street graph's faces are city blocks. Extract with a planar-graph face-traversal.
3. **Lots:** subdivide each block recursively (OBB split along the longest axis, alternating, until lot area is in range). Ensure every lot has street frontage.
4. **Zoning:** distance from the center + street importance determines land value → market square and temples at the center, shops on main streets, craft workshops in a district (noxious trades like tanning/smithing downwind and downstream — a nice detail players notice), residences filling in, farms outside.
5. **Buildings:** place a footprint on each lot, then generate the structure procedurally by *culture archetype × function × wealth*. Use a shape grammar (split grammar / CGA-style) so you get variety from a small ruleset rather than a fixed asset library.
6. **Walls:** if the settlement's history includes a threat, compute a convex-ish hull around the built area at the historically relevant time, snap to terrain advantages, add gates where roads cross. Later growth spills outside the walls — which is exactly what real cities do and it looks fantastic.

The entire settlement is stored as a **vector footprint**: a polygon boundary, a terrain-grading field, the street graph, and a list of building placements with their grammar seeds. Chunks intersecting the footprint expand it. This is the same pattern as rivers and troughs, applied to areal features.

---

## Stage 9: NPC Distribution

> **Implemented** in `pop/`. The chain is the one this section argues for: the agricultural catchment says
> how many people the land feeds, the shortfall says how many must farm, culture and wealth split the rest,
> and service ratios plus **preconditions** turn that into a roster. Nothing has a quota, which is why the
> results come out specific — a port with four fishmongers and a shipwright, a mining town with smiths and no
> baker, a crossroads village with more inns than its population implies.
>
> **Catchments are shared out between the settlements that claim them**, by proximity. Summing each
> settlement's own disc independently double-counts every field between two villages, and a world where
> everyone is comfortably fed has no reason for a good site to be worth having.
>
> **`Precondition` is an enum, not a lambda, and that is the point.** When a mining town has three smiths and
> no baker the question is "why is there no baker", and the answer has to be a thing that can be printed -
> `cereal is 4% of the catchment's yield, needs 35%` rather than a closure that returned false.
> `BusinessCatalogue.evaluate` returns a decision for *every* trade including the ones that produced nothing,
> the roster is `filter { it.exists }`, and `./gradlew :worldgen:town -Pwhy` prints the rest. That the tool
> calls the stage's own derivation through `WorldGenPipeline.contextFor` rather than reimplementing the
> reasoning is the only thing that keeps a "why" view honest.
>
> **Households are not stored.** A summary and a seed go in the `SETTLEMENT_ECONOMY` marker and
> `Households.expand` rebuilds them on demand — this section's agent LOD, and the difference between a few
> dozen bytes per settlement and four hundred thousand stored people. `Households.one` expands a *single*
> household without touching the others, which is what makes it usable for the one building a player walked
> into. The demographic pyramid, the kinship roles, the Watts-Strogatz social graph and the
> events-a-household-plausibly-knows filter are all there as pure functions.
>
> **Not implemented: living NPCs.** No daily schedules, no rumour propagation, no confidence values on
> knowledge, no expand-and-collapse lifecycle. What is here is the substrate they are made of, on the
> argument that a *runtime* system should own a living NPC. `pop/` says what a household is; nothing yet
> makes one walk to the market.
>
> One thing this section's numbers needed: the food model was **calibrated against placement**, not against a
> textbook. The tier population ranges are the one thing about a settlement already known to look right, and a
> divisor that said the largest city in the reference world could feed nine thousand of its twenty-three
> thousand people was a wrong divisor rather than a discovery about the city.

The rule: **NPCs are derived from economy, not sprinkled.**

### Economic model

Each settlement has a population `P` (from habitability, trade centrality, and simulated history) and a resource catchment. From these, derive employment by sector:

- Compute food surplus from the agricultural catchment → determines how many non-farmers the settlement can support.
- Non-farm population is allocated to crafts, trade, services, administration, and clergy in proportions driven by culture, wealth, and available resources.

Then instantiate businesses from **service ratios** — real historical/urban data gives you sane numbers. Roughly:

- 1 general store / ~150–300 residents
- 1 tavern or inn / ~200–400 residents (multiply by road traffic — a crossroads village has far more inns than its population implies)
- 1 blacksmith / ~200 residents (requires iron/charcoal access)
- 1 baker / ~250 residents (requires grain)
- 1 temple / ~500–1000, plus one always at any settlement over hamlet size
- 1 healer/apothecary / ~500
- Specialists (jeweler, bookbinder, alchemist, armorer, banker) only appear above population thresholds and with the right resource/wealth preconditions

Crucially: **gate each business type on preconditions.** A fishmonger requires water access. A vineyard requires the right climate. A weaponsmith requires iron *and* either a garrison or a trade route. This produces settlements that feel *specific* — a mining town with three smiths and no baker, a port with four fishmongers and a shipwright.

Inns specifically should be placed by traffic: `inn_count ≈ f(population) + g(road_traffic_through_settlement)`. Isolated roadside inns at one-day intervals along major routes are a great worldbuilding detail and give players natural rest points — and the road vector network gives you exact one-day-travel positions along the polyline by arc length.

### NPC instantiation

Generate a **household graph**, not a list of individuals. Households have: a dwelling (lot), an occupation, wealth, and members with ages drawn from a plausible demographic pyramid, relationships (spouse, children, apprentices, servants). Then:

- Each NPC gets a **daily schedule** derived from occupation + season + day-of-week: home → workplace → market → tavern → home, with variation.
- Each NPC gets a **social graph**: family, guild, employer/employee, friends, rivals. Keep it sparse (Watts-Strogatz small-world) so gossip propagates realistically.
- Knowledge/rumor: each NPC holds a set of facts with confidence values, acquired via the social graph and physical proximity. This is what makes NPCs feel like they live in a world — the innkeeper knows about the bandit attack because a merchant told him.

**Storage:** do *not* store full NPCs for the whole world. Store the settlement's economic summary + household seeds. Instantiate full NPC state only for settlements with players nearby (LOD for agents). A distant town is just `{population: 340, wealth: 0.6, businesses: [...], notable_npcs: [...]}`. When a player approaches, expand it deterministically. When they leave, collapse it back, persisting only diffs.

---

## Stage 10: History & Story Generation

> **Implemented** in `history/`, with the log itself in `core/Chronicle.kt` — see
> [The history log is a third world-tier product](#the-history-log-is-a-third-world-tier-product) for why it
> lives there and not in the feature store. Civilisations, settlements, wars, plagues, famines, floods,
> eruptions, a few hundred notable figures and the artifacts they make, at settlement granularity and
> five-year steps over a thousand years. It costs about thirty milliseconds for a 292-settlement world.
>
> **Every random decision is a keyed roll**, `hash(base, year, subject, salt)`, and not a draw from a stream.
> That is not stylistic: a stream makes each result depend on how many draws happened earlier, so adding one
> disaster type would silently rewrite every war in the world. Keyed rolls make each decision independent, so
> a new decision is additive — and it is what lets `HistoryStageTest` assert that two runs of a seed produce
> the same thousand years event for event.
>
> **Grudges cite an event, not a number.** `CivRecord.grudges` is `(other civ, event id)`, so an NPC can name
> the wrong — "they burned Ashford in 412" — where a scalar hostility could only be complained about in the
> abstract. Artifact provenance is the same idea: a chain of event ids ending at a *site*, which is a marker
> in the world, which is a place a player can dig.
>
> **Importance pruning keeps the causal graph whole.** Everything at or above a floor is kept, the rest is
> sampled one in twenty-four, and then the transitive closure over `causes` is added back — because a chain
> with a hole in it is worse than a shorter chain, and a dangling id is a tool throwing rather than a world
> looking wrong. `Chronicle.prunedEvents` reports what went, so the log is never silently truncated.
>
> **Not implemented:** deities and monsters as entities, technology as anything but a scalar, place names
> changing on conquest beyond keeping the old one (`oldNameSeed`), and event templates with pre- and
> postconditions — the causal chains here are valid because the simulation only logs what it did, not because
> a constraint system enforced it.

This is what gives you Dwarf Fortress-flavored depth. Run a **coarse-grained agent simulation** over N centuries at world scale, before the game starts.

### Entities

Civilizations, factions, settlements, notable figures, artifacts, deities, monsters/threats, sites (ruins, battlefields, tombs).

### Simulation loop (per year or per 5 years)

```
for year in range(start, present):
    for civ in civs:
        update_population(civ)          # logistic growth vs. carrying capacity
        expand_or_migrate(civ)          # found settlements at high-H unclaimed cells
        update_technology(civ)
        update_relations(civ)           # diplomacy from proximity, resource competition, ideology
    resolve_wars()                      # sieges, battles, conquests, razings
    resolve_disasters()                 # plagues, famines (from bad climate years), eruptions, floods
    update_figures()                    # births, deaths, rises to power, quests, murders, betrayals
    create_artifacts()                  # notable figures forge/commission items
    update_sites()                      # settlements founded, abandoned→ruins, monuments built
    log_events()
```

### Event log is the product

Every event is a structured record: `{year, type, actors[], location, causes[], effects[]}`. This log is *the* story database. Everything downstream reads from it:

- **Ruins** in the world are settlements the log says were destroyed — and the ruin's architecture matches the culture that built it, and its state of decay matches how long ago. Ruins are vector footprints, exactly like living settlements, with a decay parameter.
- **Battlefields** have bones, rusted weapons, and a memorial if the winners survived.
- **Artifacts** have provenance chains: forged by X in year Y, wielded by Z, lost in battle B, now in tomb T. Put it in tomb T. Players can actually find it.
- **Grudges** between factions come from specific logged wrongs, and NPCs of those factions cite them.
- **NPC knowledge** is seeded from events within their lifetime and geographic/social range.
- **Quest generation:** mine the log for unresolved threads — a stolen artifact never recovered, a missing heir, a sealed tomb, a monster that was driven off but not killed. These become quests with genuine backstory.
- **Place names** derive from events and founders, and change on conquest (the conquered name survives as an "old name" NPCs sometimes use).

### Keeping it tractable

- Simulate at **settlement granularity**, not individual granularity, except for a few hundred "notable figures" per century.
- Cap the log with **importance scoring** — keep everything for major events, sample minor ones, and prune events that no surviving entity remembers or that left no physical trace.
- Use a **template + constraint** approach for narrative coherence: events have preconditions and postconditions, so the causal chain is valid by construction. A "betrayal" event requires a prior "trusted relationship" event.

Budget: a few thousand settlements over 1000 years at 5-year steps is a few hundred thousand simulation ticks — seconds to a couple of minutes on one node. Run it once at world creation and store the result.

---

## The Vector Feature System

> **Implemented** in `vector/`, and it is the part of the design that paid off most. `Polyline.kt` has
> closest-point and arc-length reparameterisation; `StationTable.kt` is a struct-of-arrays station store
> with Catmull-Rom interpolation; `Profiles.kt` holds the profile functions; `FeatureEvaluator.kt` sorts by
> `(priority, id)` and blends; `FeatureIndex.kt` is the uniform-grid bucket index the section recommends
> over an R-tree. Feature types: `PolylineFeature`, `PointFeature` (radial profiles including the terrace
> used for settlement grading), `MarkerFeature` and `PointMarker` (geometry + attributes, no terrain
> effect). `Quantize.kt` exists solely to make the "quantize before branching on a float" rule mechanical
> rather than remembered.
>
> Added with step 8: `FootprintFeature`, an **oriented rectangle** that both imposes a height and carries
> attributes. It is deliberately not the polygon type — it is the ninety percent of one that costs nothing,
> since two dot products put a query point in its own axes and the signed distance to its boundary is a max of
> two absolute values. No point-in-polygon, no clipping, no offsetting, no index that copes with concavity.
> That is enough for the one areal thing step 8 produces, and for anything else shaped like a box at an angle:
> a market stall, a field, a quay, a wall tower.
>
> Missing: the general polygon geometry type. Everything else is a polyline, a point or a rectangle, which is why fans,
> deltas, lakes, coastlines and settlement footprints all deviate (2–5).

This is the load-bearing subsystem, so it gets its own section. Rivers, glacial troughs, fjords, roads, coastlines, moraines, alluvial fans, settlement footprints, and cave systems all share one representation and one evaluation path.

### Common structure

```
trait VectorFeature {
    id: FeatureId
    kind: FeatureKind
    bbox: Aabb                          // world-space, includes influence radius
    corridor_width_max: f32             // for spatial index conservative bounds
    geometry: Geometry                  // Polyline | Polygon | Point
    stations: [StationAttributes]       // per-arclength attributes, resampled uniformly
    priority: i32                       // stamp order when features overlap
    blend: BlendMode                    // Min | Max | Replace | Additive
}
```

Everything is stored **in world space at full precision**, independent of any grid. Chunks do not own features; features do not know about chunks. This is the whole point.

### Spatial index

An R-tree (or a uniform grid of feature-ID buckets, which is simpler and adequate given features are broadly uniformly distributed) over feature bounding boxes, expanded by `corridor_width_max`. Chunk generation queries `index.query(chunk_bbox.expanded(margin))` and gets back every feature that could possibly influence any voxel in the chunk. Typical result: 0–5 features. The index is built once after each vector-producing stage and stored alongside the world tier — immutable, replicated, tiny.

### Evaluation at chunk scale

For each column `(x, y)` in the chunk:

```
h = base_heightfield(x, y)              // raster sample + detail erosion
mods = []
for feature in features_near_chunk:
    (s, d, on_segment) = feature.geometry.closest_point(x, y)
    if d > feature.corridor_at(s): continue
    attrs = feature.stations.interpolate(s)        // Catmull-Rom over stations
    mods.push(feature.profile(d, attrs))
sort mods by priority
h = apply_blends(h, mods)
```

`closest_point` is the only nontrivial piece. For a polyline it's a per-segment point-to-segment projection; with 0–5 candidate features and maybe 20 relevant segments each, that's a few hundred distance computations per column, ~100k per chunk. Fast, vectorizable, and cache-friendly if you pre-extract the candidate segments into a flat array before the column loop.

**Profiles** are pure functions of `(d, attrs)`:

- River channel: trapezoidal or parabolic cut, depth from `attrs.depth`, with a floodplain shoulder that widens with discharge.
- Glacial trough: power-law U, `z(d) = floor + (|d|/half_width)^p * wall_height`.
- Fjord: same U-profile but with the floor below sea level, plus the sill as a local floor bump at the recorded `s`.
- Road: flat cut/fill to a target grade, with an embankment falloff.
- Moraine: a ridge, additive rather than subtractive.

### Chunk borders — why this works

**This is the property that makes the whole approach worth it.** Chunk boundaries are not visible to the feature system at all.

Consider a river crossing the boundary between chunk A and chunk B:

- The river's centerline is a single continuous polyline in world space.
- Chunk A generates column `(31, y)`; chunk B generates column `(0, y)` — adjacent in world space.
- Both columns run the identical `closest_point` query against the identical polyline object.
- Both get an `(s, d)` that varies continuously across the boundary, because the polyline is continuous and the query is a continuous function of position.
- Both interpolate station attributes at nearly-identical `s` values, so `width`, `depth`, and `floor_elevation` are nearly identical.
- Both apply the identical profile function.

Result: the channel is C⁰-continuous across the boundary **by construction**, not by blending, overlap margins, or seam-fixing. There is nothing to stitch. Two chunks generated on different cluster nodes, months apart, produce a matching channel because they evaluate the same deterministic function of world position.

The same holds for glacial trough walls, fjord sills, road embankments, and settlement grading. A fjord 40 km long spanning hundreds of chunks has one sill, at one exact world position, and every chunk agrees on where it is.

Contrast the alternative — carving features into the raster and letting chunks interpolate — where the coarse grid quantizes the feature to 1 km and bilinear interpolation smears the walls into slopes. Or the naive per-chunk-procedural approach, where each chunk perturbs the channel independently and the river visibly jumps at every boundary.

**The three requirements** for this to hold:

1. **The base heightfield must also be seam-free.** Vector features guarantee their own continuity, but they blend against `h_base`. If detail erosion produces seams, the river floor is continuous but the banks aren't. Hence the overlap margin and blend on the droplet-erosion pass — that margin exists for the raster tier only.
2. **Station interpolation must be a pure function of `s`.** Use Catmull-Rom or cubic Hermite over uniformly-resampled stations. Never interpolate using "the two stations nearest this chunk," which would be chunk-dependent.
3. **No feature may be mutated per-chunk.** If chunk generation wants variation along a river, it must come from a function of world position (noise sampled at `(x,y)` or at `(s,d)`), never from the chunk seed. Chunk-seeded randomness is fine for scattered vegetation and rocks; it is forbidden inside a feature profile.

### Feature-feature interaction

Where features overlap, `priority` decides. Sensible ordering, lowest first:

```
glacial_trough  →  river_channel  →  alluvial_fan  →  lake  →  road  →  settlement_grading
```

A river running along a glacial trough floor (which is what actually happens after deglaciation) works correctly: the trough carves the broad U, then the river cuts its narrow channel into the trough floor. A road crossing a river gets a bridge — detected at vector level as a geometric intersection between two polylines, emitted as a `bridge` point feature during the road stage, so both chunks either side agree the bridge exists and where its abutments are.

Junctions need explicit handling. At a river confluence the naive `min` of two channel profiles produces a Y with a hard crease. Emit an explicit `confluence` feature (a small polygon with its own blended profile) covering the junction area, at higher priority than either reach. Same for road intersections and trough tributary junctions.

> **Implemented as designed** for confluences, which are `PointFeature`s with a radial profile rather than
> polygons. Road junctions and trough tributary junctions are not smoothed.
>
> **Bridges needed a three-way split**, and the reason is worth recording: a bridge cannot be expressed in
> a heightfield at all. A road is `REPLACE`-blended terrain, so a road crossing a river dams it — the deck
> and the ground under it are the same number. So the crossing is: (a) the road feature drives its
> carriageway *and shoulder* half-widths to zero over the crossing, leaving the channel intact — a ford;
> (b) a `BRIDGE` `PointMarker` records deck elevation, span, half-width and bearing; (c) the materializer
> lays masonry decking from that marker after water, above the channel it spans. All three are pure
> functions of world position, so the deck is seam-free like everything else.
>
> Zeroing only the carriageway is a trap worth naming, because it looks correct and is not: the corridor
> half-width that decides whether a feature evaluates *at all* is carriageway + shoulder, so a road with no
> carriageway and a six-metre shoulder still stamps — and the shoulder profile eases from the road surface
> at the centreline, which is exactly the channel-filling the gap exists to prevent.

### Storage and cost

For a 4096×4096 km world: perhaps 50k river reaches, 5k roads, 2k glacial features, 20k settlement footprints. At ~100 stations each with ~64 bytes per station, that's a few hundred MB — comfortably in RAM on every node, replicated with the rest of the immutable world tier. Loading it is a startup cost, not a per-chunk cost.

---

## Software Architecture

### The pipeline framework

```
WorldGenPipeline
├── Stage (interface)
│   ├── id: StageId
│   ├── version: u32                  // bump → invalidates cache
│   ├── dependencies() -> [StageId]
│   ├── scale: World | Region | Chunk
│   ├── resolution: MetersPerCell     // independent of scale
│   ├── halo(): u32                   // neighbor cells needed (raster stages)
│   ├── outputs(): [Raster | Vector]
│   └── generate(ctx: &GenContext, region: Region) -> LayerData
│
├── GenContext
│   ├── seed: u64
│   ├── layer_store: &LayerStore      // fetch upstream rasters
│   ├── feature_store: &FeatureStore  // spatial-indexed vector query
│   ├── rng(salt) -> Rng              // deterministic sub-RNG
│   └── config: &WorldConfig
│
└── Scheduler  (topological sort → distributed execution)
```

Every stage declares its dependencies, its resolution, and its halo. The scheduler builds a DAG, topologically sorts it, and for each stage either runs it once globally or shards it across the cluster by region. **A stage must never read anything not declared as a dependency** — enforce this in the layer store API, and your determinism and cacheability are guaranteed by construction.

> **Implemented** in `core/`. The `Stage` interface carries `id`, `version`, `dependencies`, `scale`,
> `resolution`, `halo` and `outputs` exactly as above. The undeclared-read ban is enforced two ways:
> `LayerStore.scopedFor` and `FeatureStore.scopedFor` hand a stage a view containing only its transitive
> dependencies, and `WorldGenPipeline.verifyOutputs` rejects a stage that produced a layer or feature kind
> it did not declare. That second check matters more than it looks: an undeclared *output* is invisible to
> the dependency graph, so a downstream stage reading it would work by accident right up until the
> scheduler put the two on different nodes.
>
> Ties in the topological sort break by stage name, never by hash-map order, so two nodes building the same
> pipeline execute it in the same order and derive the same RNG streams.
>
> `halo` is declared and respected by the scoped views, but no stage currently needs a non-zero one — every
> raster stage here is world-tier and sees the whole grid. There is no `Region` tier and no distributed
> scheduler; `generateWorld` runs the world tier on one thread.

Vector-producing stages are almost always global-tier (they need global coherence — a river network isn't meaningful per-region), which conveniently means they run once and never need distributing.

### Determinism discipline (non-negotiable)

- **Hash-based RNG only.** `rng = Hash(world_seed, stage_id, stage_version, chunk_x, chunk_y, salt)`. Never a shared mutable global RNG — that makes results order-dependent and destroys distributability. Use a counter-based PRNG (PCG, xoshiro seeded by SplitMix, or Philox).
- **No floating-point nondeterminism across nodes.** Either pin to a fixed FP mode with no fast-math and identical instruction paths across the cluster, or use fixed-point integers for anything that feeds a discrete decision. The safest rule: floats are fine for continuous fields; any *branch* on a float must go through a quantization step first. This matters especially for vector features: `closest_point` returning slightly different `s` on two nodes is harmless for a continuous profile, but if you branch on `s` (e.g. "is this past the sill?") quantize first.
- **Stable iteration order everywhere.** No hash-map iteration, no thread-completion-order accumulation. Sort by a canonical key before any order-sensitive reduce. Feature query results must be sorted by `(priority, feature_id)` before blending, not returned in R-tree traversal order.
- **Version every stage.** Cache keys include stage version + all upstream versions, so changing erosion parameters invalidates erosion-and-downstream but not tectonics.

> **Implemented and load-bearing.** `core/GenRng.kt` is a counter-based hash RNG; there is no shared mutable
> generator anywhere in the module. `vector/Quantize.kt` gives the quantize-before-you-branch rule a name so
> it can be called rather than remembered. `WorldGenPipeline` computes a per-stage version vector folding
> every transitive upstream version, which is what makes an erosion parameter change invalidate erosion and
> downstream while leaving tectonics — the expensive part — alone.
>
> The one place this is checked rather than asserted is the seam view: it generates chunks independently and
> compares every shared column, which is the observable consequence of the whole discipline.

### Scale tiers and where work happens

| Tier | Extent | When | Where | Storage |
|---|---|---|---|---|
| World raster | whole map, ~1 km cells | once, at world creation | single node (or GPU) | immutable blob, replicated |
| World vector | whole map, resolution-free | once, after rasters | single node | immutable geometry + R-tree, replicated |
| History | whole map, event log | once, after world | single node | append-only event store, replicated |
| Region | 512×512 chunks | on demand, cached | any node | object store, LRU |
| Chunk | 32×32×256 voxels | on demand | region-owning node | object store + hot cache |
| Runtime | live entities | continuous | zone-owning node | authoritative DB + memory |

The key insight for the cluster: **world raster, world vector, and history tiers are computed once and are read-only forever**, so they can be replicated everywhere and need no coordination. All the distributed work is region/chunk generation, which is embarrassingly parallel because the halo is bounded and the upstream is immutable.

> **Implemented:** the world raster and world vector rows (`core/World`, produced once by
> `WorldGenPipeline.generateWorld` and immutable thereafter) and the chunk row (`store/ChunkCache`, generated
> on demand and cached in tiers). **Not implemented:** the history row, and the region tier — `StageScale`
> declares `REGION` and no stage uses it. Nothing so far has needed an intermediate tier: the world grid fits
> in RAM and chunks read it directly.

### Distribution

- **Sharding:** partition the world into regions by a space-filling curve (Hilbert). Assign region ownership to nodes via consistent hashing. Hilbert ordering means neighboring regions tend to land on the same node, which cuts halo fetches dramatically.
- **Work queue:** chunk generation requests go into a distributed queue keyed by region. The region owner is the preferred worker (locality), but any node can steal work since generation is a pure function — worst case it fetches upstream layers over the network. Vector features need no fetching at all: every node has the full set in RAM.
- **Halo exchange:** raster stages declare their halo width. The scheduler ensures the halo region of upstream layers is fetched (or recomputed — often cheaper than a network round-trip for cheap stages) before running. Vector stages need no halo, which is another reason to push features into the vector tier where possible.
- **Global stages** (priority-flood, stream-power erosion, ice flow, history sim) are inherently sequential-ish. Run them once on a designated node at world creation, write to the immutable store, and never think about them again. Don't try to distribute these; the complexity isn't worth it for a one-time cost.
- **Caching:** three tiers — in-process LRU (hot chunks), node-local disk (warm), shared object store (cold, authoritative). Cache key = `hash(world_seed, pipeline_version_vector, chunk_coord)`.

### Chunk generation at request time

```
generate_chunk(cx, cy, cz):
    # 1. Base terrain (raster tier)
    world  = WorldMap.sample_bicubic(cx, cy, halo=margin)
    detail = detail_droplet_erosion(seed, world, halo=margin)   # blended in overlap
    height = blend(world.elevation, detail)

    # 2. Vector features (resolution-free, seam-free)
    feats  = FeatureStore.query(chunk_bbox.expanded(corridor_max))
    height = apply_feature_profiles(height, feats)     # sorted by priority
    grading = settlement_grading(feats)                # flatten under buildings
    height = blend(height, grading)

    # 3. Materialize
    voxels = stratify(height, world.rock_layers, world.soil_depth)
    carve_caves(voxels, seed, feats.cave_systems)      # caves are vector too
    apply_water(voxels, world.water_level, feats.rivers, feats.lakes, feats.fjords)
    place_ore(voxels, deposits_intersecting(chunk))

    # 4. Scatter (chunk-seeded randomness is safe here, not in profiles)
    place_vegetation(voxels, biome, seed)              # Poisson disk, density from biome
    place_buildings(voxels, feats.settlement_footprints)
    apply_history_marks(voxels, feats.ruins, feats.battlefields)

    return RLE_encode(voxels)
```

Note the ordering: natural terrain, then vector features carved into it, then materialization, then scatter. Settlements flatten terrain via a soft deformation applied to the heightfield *before* stratification, so buildings sit on graded ground rather than floating or clipping. Anything chunk-seeded happens last and never influences geometry that crosses a chunk boundary.

> **Implemented** with this ordering, in `core/ChunkHeightSampler.kt` (steps 1–2) and
> `voxel/ChunkMaterializer.kt` (step 3), which reads `Stratigraphy`, `SurfaceSampler`, `RiverWater`,
> `OreVeins`, `BridgeDecks` and `TownStructures`. Grading is a `REPLACE`-blended radial terrace applied with
> the rest of the features, so it is in the heightfield before stratification as required, and a building's own
> pad is another one — which is why `place_buildings` is not a scatter step here: the footprint is a *feature*,
> so the ground under a house is level before any block is written and both halves are the same object.
>
> `place_buildings` and `apply_history_marks` are done, and neither is chunk-seeded: buildings and wall
> circuits come out of the features' own immutable attributes, and the one place randomness appears - rubble
> scatter in a ruin field - hashes the *world* position, exactly as `OreVeins` does and for the same reason.
>
> Still not present: `carve_caves` and `place_vegetation`. So the scatter pass proper does not exist, there is
> no vegetation in the block palette to place, and **nothing chunk-seeded exists anywhere** - which means the
> "chunk-seeded randomness is safe here, not in profiles" line is still a rule with no users.

### Persistence & player modification

The generated world is a **base layer**; player edits are a **sparse delta layer** on top. Store deltas as `(chunk, position, block)` in a KV store or as per-chunk RLE patches. A chunk read is `base(generated, cached) ⊕ delta(persistent)`. This means:

- You never store unmodified chunks — huge storage savings for a large world.
- Regeneration is possible if the base is evicted.

**Delta compaction is mandatory, not optional.** Deltas are unbounded: a player who terraforms a hillside over six months accumulates a chunk delta larger than the chunk itself. Past a threshold — edits exceeding ~30% of the chunk's voxels, or a raw delta larger than the RLE-encoded merged chunk — stop treating it as a delta and **bake** it: store the merged chunk as the new base for that coordinate, mark it `baked`, drop the delta. Reads then skip generation entirely, so heavily-built areas become *cheaper*, not just smaller.

Baking is also the migration path for pipeline changes. **Once you ship, freeze the pipeline version for existing worlds** — any change shifts the base under player deltas. To upgrade: bake every chunk that has a delta, then change the pipeline. Unmodified chunks regenerate against the new version harmlessly.

> **Implemented** in `store/`. `ChunkCache.kt` is the three-tier cache (hot LRU → an ordered list of blob
> stores, promoting into the nearer tiers on a hit → generate), keyed on
> `hash(seed, pipelineVersion, chunkKey)`. `derived/ChunkDelta.kt` is the sparse edit layer;
> `ChunkStore.kt` serves `base ⊕ delta`, compacts, and bakes at the two thresholds this section names —
> 30% coverage, or a raw delta larger than the RLE-encoded merged chunk. `bakeAll()` is the migration path
> and is tested as one.
>
> One detail the design implies but does not spell out: **baked blobs are keyed on seed and coordinate but
> deliberately *not* on pipeline version.** That is what makes baking a migration — a baked chunk has to
> survive the version bump it was baked to protect against, so it must not be keyed on the thing that is
> about to change. A missing baked blob therefore fails loudly rather than falling back to generation,
> because falling back would silently discard player work.
>
> Not present: an actual disk or object-store tier. `MemoryBlobStore` implements the `ChunkBlobStore`
> interface and the real tiers plug in behind it — this module holds no I/O by design.

### Module layout

```
worldgen-core/       Stage trait, GenContext, RNG, LayerStore, Scheduler
worldgen-fields/     Noise, domain warping, Voronoi, distance transforms, PDE solvers
worldgen-vector/     Polyline/polygon types, resampling, smoothing, closest-point,
                     station interpolation, profile functions, R-tree, blend/priority
worldgen-geo/        Tectonics, heightfield, erosion, stratigraphy, glacial flow
worldgen-climate/    Temperature, wind, precipitation, seasons
worldgen-hydro/      Priority-flood, flow accumulation, river graph, meandering, lakes
worldgen-bio/        Biomes, soil, vegetation, fauna
worldgen-resource/   Deposit generation
worldgen-civ/        Habitability, settlement placement, roads, town layout, buildings
worldgen-history/    Historical simulation, event log, artifacts, sites
worldgen-pop/        Household/NPC generation, schedules, social graph, knowledge
worldgen-voxel/      Chunk materialization, RLE codec, caves, scatter placement
worldgen-derived/    Navmesh tiles, opacity grid, and other server-side derived structures
worldgen-service/    gRPC service, work queue, sharding, cache tiers
worldgen-net/        Chunk wire format, delta protocol, base hashing, version gate
worldgen-tools/      Map viewer, layer inspector, feature inspector, seed browser,
                     regression harness
```

Keep everything above `worldgen-service` free of networking and I/O — pure functions over data. That's what makes it testable and lets you run the entire pipeline offline in a viewer. `worldgen-vector` in particular must be dependency-light, because it is linked into both server and (eventually) client.

> **Deviation.** This is one Gradle module with these divisions as packages — see
> [Actual module layout](#actual-module-layout). The no-I/O rule holds as stated, module-wide rather than
> per-module: the whole of `worldgen/` depends on the Kotlin stdlib and the JDK and nothing else, and
> `viewer/` is the single package permitted Swing and the filesystem. `worldgen-net` and `worldgen-service`
> do not exist; the pieces of them that are pure functions (base hashing, the version gate, cache tiering)
> live in `store/`, and the networking they would wrap belongs to the server.
>
> `history/` and `pop/` exist as packages, as planned. Town layout went into `civ/` rather than its own
> package, as this list has it. The one rule that constrains them is the sibling rule: stage packages may read
> another's *vocabulary* - its channel names and its enums, which is what `voxel/` has always done with
> `civ.BridgeChannels` - but may not call into its algorithms. That is why `HistorySim` duplicates the largest
> ruin radius as a constant with a tripwire invariant instead of reading `ChunkMaterializer.MARKER_MARGIN`.

### Data-driven configuration

Biome definitions, culture profiles, business types with their preconditions, building grammars, resource geology rules, feature profile parameters, and event templates all belong in **data files, not code**. Load and validate at startup. This is the difference between a system a designer can tune and one that needs an engineer for every change. Hot-reload them in the offline viewer.

> **Not implemented.** Every tunable is a Kotlin `data class` with defaults — `TectonicsParams`,
> `ClimateParams`, `GlacialParams`, `SettlementParams`, `TownParams`, `HistoryParams`, `EconomyParams`,
> `Culture`, the biome prototype table, and `BusinessCatalogue.ALL`. They are already grouped and named as
> this section wants, so extracting them to files is a serialisation layer rather than a redesign, but a
> designer cannot currently tune anything without a recompile. Worth doing when there is a designer; not
> before, because the shape of the parameters is still moving.
>
> The business catalogue is the most tempting one to extract and the clearest illustration of why not yet:
> its shape changed twice while step 9 was being written - a per-resident cereal figure became a share of the
> catchment's yield, and a business gained a traffic term - and a serialiser over a shape that moves is two
> things to change instead of one.

### Tooling (build this early, not late)

The single highest-leverage investment: a **standalone offline viewer** that runs the pipeline and lets you inspect any layer as a map, overlay the vector features on the raster, scrub through history years, click a settlement to see its economy and NPC roster, click a river reach to see its station attributes, and diff two seeds. Worldgen without visualization is unworkable — you'll spend weeks guessing at why rivers look wrong.

Add a **chunk-boundary stress view**: render a 4×4 block of chunks generated independently (ideally on separate threads with shuffled ordering) and highlight any column where adjacent chunks disagree on height by more than epsilon. This catches seam regressions immediately, and seam bugs are otherwise found by players months later.

> **Implemented**, and it was the right call to build early. `viewer/` is a Swing layer/feature inspector
> (`./gradlew :worldgen:viewer`) with a PNG export mode that works over SSH and in CI. It renders any raster
> layer with a per-layer palette, hillshades elevation, overlays the vector features, and drills into a voxel
> tier. Two product bugs in the civ stages were found by looking at exported PNGs rather than by a test.
>
> `MapRenderer.colorOf` is an exhaustive `when` over `FeatureKind` **on purpose**: a new feature kind cannot
> be added without the viewer being taught to draw it, because the compiler refuses. That has already caught
> three kinds that would otherwise have shipped invisible.
>
> **A caution the hard way.** The surface views originally read the top voxel of a single vertical chunk,
> anchored on the chunk's lowest column. Vertical chunks are grid aligned, so that anchor snaps *down* to a
> multiple of the chunk height and leaves only the headroom that happens to remain before the next boundary -
> sometimes one voxel. Any column above it read full to the ceiling, so the view reported deep bedrock as the
> ground for about one column in twenty, and rather more than that over a valley. It looked entirely plausible:
> broad clay-and-sand banks along a river, which is exactly what banks should look like. They were not there.
>
> `ChunkMaterializer.surfaceColumns` now resolves each column from whichever vertical chunk that column's
> surface is actually in, and the property is asserted two ways - that every land column resolves at all, and
> that the elevation it reports matches what the column source said to within a quantisation step. Both
> assertions were checked against the old behaviour to confirm they fail on it, which is worth doing for any
> regression test and was worth doing twice here: the first two attempts passed against the bug, because they
> sampled flat upland and a channel floor rather than the valley walls where the relief is.
>
> The general lesson for the tool, since it is the thing every judgement about the pipeline rests on: **a view
> that cannot answer must say so rather than return its nearest guess.** Both surface views now report no-data
> where the answer is outside what they looked at, and that change is what made the artefact visible.
>
> The chunk-boundary stress view is `core/ChunkSeamCheck.kt`; it runs on every viewer export and prints
> `SeamCheck: clean - 64 chunks, 3584 shared columns agree`.
>
> **The viewer now measures rather than only draws**, which the climate work forced. Climate is the most
> indirectly-tuned thing in the pipeline — a change to plate density moves mountain height, which moves rain
> shadow, which moves the biome mix three stages later — so "there is too much desert and not enough farmland"
> was a judgement that could only be argued about in front of a picture. Every run now prints the **land
> fraction in both forms**, bedrock and final, because printing only the final figure makes a normalisation
> that missed indistinguishable from a seed whose rivers built a lot of delta; and the **biome mix as a share
> of the land**, headed by a single "green" percentage. The one number is the point: seven shares take
> arithmetic to answer from, and a target nobody can read off the output is a target that gets tuned past in
> both directions. Steppe, shrubland and savanna are deliberately excluded from it — they are the semi-arid
> margin, and counting them is how a world talks itself into being green. `InvariantsMain` prints the same land
> fraction per seed plus its median and range, which is the only place seed-to-seed spread is visible, since
> the invariant's own bounds are deliberately loose.
>
> **Two viewer bugs worth recording, because both made the tool lie rather than fail.** Point features were
> drawn through `drawBounds`, which for a zero-extent marker is a zero-size rectangle at 38% alpha — so every
> settlement in the world was invisible, and the map of a populated continent looked like an empty one.
> Settlements are now dots sized by population, area-proportional with an outline so they read against any
> biome colour. And a **categorical layer was given a continuous colour bar**: biome, flow direction and lake
> id were rendered with a 1st/99th-percentile gradient legend, which is a meaningless scale over a set of
> labels. Those layers now get a real legend — named swatches with their share of the screen, from a census of
> the visible cells — and `Labels` supplies the vocabulary, so the readout says `temperate forest` and `NE`
> rather than `8` and `1`.
>
> The overlay also gained **per-kind filtering**, defaulting town-scale kinds off. A full pipeline emits about
> thirty feature kinds, and buildings, streets and businesses are dense enough at world scale to bury the
> rivers and roads underneath them; the attribute-record kinds pinned to a settlement's own coordinates
> (`SETTLEMENT_HISTORY`, `SETTLEMENT_ECONOMY`) are hidden for a different reason, which is that they paint over
> the settlement dot they describe.
>
> **History scrubbing and settlement economy inspection now exist**, as `chronicle -Pyear` and `town` — see
> [Two tools for steps 8 to 10](#two-tools-for-steps-8-to-10-and-the-scale-that-needed-them), which also lists
> the seven bugs they found on first use. Neither is a *view*: a scrub is a comparison of two stored years, and
> an economy is a table of reasons. Rendering either would have been a fourth picture of the same data.
>
> Still not implemented: **seed diffing**. And clicking a river reach to see its station attributes — the probe
> answers that from the command line (`-Pon=river_channel`) but the interactive viewer has no inspector.

Also: a **regression harness** that generates N seeds and asserts invariants (every river reaches the ocean or an endorheic basin; every reach's discharge is monotonically non-decreasing downstream; no settlement in the sea; every settlement has food access; every fjord sill is shallower than its landward basin; population totals within expected bounds; no NPC with a required-but-missing workplace) and reports statistical distributions. Worldgen bugs are usually rare-seed bugs — you need to generate thousands of worlds to find them.

---

## Client/Server Communication & Chunk Locality

### Who holds merged state

**The server must hold merged voxel state. This is not a design choice.**

Anything the server is authoritative over needs the server's own view of geometry: line of sight for attacks, projectile collision, movement validation, NPC pathing, occlusion for stealth. If the server doesn't have merged voxels it cannot answer "is there a wall between these two players" — and players will build walls specifically to find out what happens when it can't. Client-authoritative geometry is the single most reliably exploited thing in multiplayer games. This is a correctness requirement, not a performance tradeoff.

> **Implemented** as `store/ChunkStore.merged()`, which is the only way to read a chunk — there is no API
> that hands out a base without its delta, so the authoritative view is the default rather than the
> disciplined choice. **Now wired into the zone-server** by `world/stream/ChunkService`, which owns the one
> `ChunkStore` for the running world and is the only path to a voxel edit — so the state the client is shown
> and the state the server will answer line of sight from are the same object rather than two that agree.
> It lives on the `zone-tick` thread, because `ChunkStore`, `ChunkDelta` and `DerivedStore` each assume a
> single owner; inbound requests and edits are queued on the Netty threads and drained by the tick.

The delta-merge cost itself is a non-issue. A chunk is 32×32×256 ≈ 262k voxels; overlaying a few hundred or few thousand edits onto a decoded base is microseconds. Even 100k edits in one chunk is memcpy-scale. **The expensive part is regenerating the base** (erosion, feature stamping, scatter — milliseconds to tens of milliseconds), which is why the cache tiers exist. Optimize base regeneration and caching; ignore merge cost.

### What to send the client

Given the server holds merged state anyway, the wire format is a separate question with a separate answer.

**Ship merged RLE chunks first.** It is simpler, correct by construction, and RLE on voxel terrain compresses well enough to be viable up to a fairly large playerbase. Do this for launch.

**Add base-plus-delta later as a bandwidth optimization.** The client can generate the base deterministically — that is what all the purity discipline was for — so a chunk becomes `(pipeline_version, chunk_coord, delta_blob)`. An untouched chunk is a few dozen bytes instead of a full RLE payload. On a large world most chunks a player crosses are untouched, so this is a large win specifically on initial load and fast travel, which are the cases that hurt.

**The hard prerequisite:** the client must produce bit-identical base chunks to the server. Same pipeline version, same RNG, same float behaviour, forever, across every platform shipped. If the client is C++/Rust everywhere you can probably hold that line. A WASM or mobile client with a different FP path, or a client one patch behind, gives you **silent desyncs** — the player sees ground where the server sees air, walks into a wall that isn't there, and the bug reports are incomprehensible.

> **Merged RLE chunks ship, as advised.** `ChunkDataSMSG` carries `RleCodec`'s output verbatim, deflated only
> when that helps — a surface chunk is ~14.7 kB encoded and ~3.1 kB deflated, while a uniform underground one
> is thirteen bytes and *grows* to nineteen if compressed, so the compression choice is a per-payload flag
> rather than a policy. The `Encoding` field exists so the format can move without consuming a
> `PipelineVersion` component.
>
> **Base-plus-delta is still deferred**, as this section says it should be. There is no client-side generator,
> so `base_hash` rides along in every chunk message unused: it is there from the start so that shipping
> base-plus-delta later is a protocol addition rather than a correctness gamble. The Godot client is C#, which
> is exactly the "different FP path" case this section warns about, so the deferral is not merely about
> traffic numbers.
>
> `store/VersionGate.kt` and `BaseHash` are the two mitigations below.
>
> `PipelineVersion` is deliberately **three separately-diagnosable components** rather than one opaque
> number, because they fail for different reasons and have different remedies: a pipeline mismatch is
> different terrain, a palette mismatch is the same terrain made of the wrong rock, and a format mismatch
> cannot be decoded at all. One number could only ever say "incompatible".
>
> The palette version hashes block **ids and names**, not enum ordinals, so reordering the declaration is
> harmless while renumbering an id invalidates everything — the ids are what end up in stored data, so they
> are what the hash must be over.
>
> **The vector is server-side only.** It is a cache key and a boot gate, and both of those are decisions the
> server makes. What goes over the wire is a single hand-incremented `ChunkEngine.VERSION`, covering the two
> things a merged-chunk client actually does — decode the payload and name the materials in it. Such a client
> short-circuits to `ServerAuthoritativeOnly` and has no base to generate, so it cannot act on the
> distinction between the three; sending it the vector would only invite a decision it has no basis for. The
> auto-derived hashes stay where they are load bearing (`ChunkCache` keys on `pipelineVersion`, so forgetting
> to bump it there would silently serve terrain from another build) and the hand-incremented number covers
> the boundary where a hash cannot: the client is a separately released artefact, so the value has to be one
> a human can copy. `ChunkStoreTest` pins `paletteVersion()` to catch the forgetting, which is the failure
> mode a manual number reintroduces.
>
> **The block palette is not sent either.** It was, briefly — `BlockPaletteSMSG`, a few hundred bytes per
> login — on the argument that a renamed material should not be a client release. It bought nothing: a *new*
> material is a client release regardless, because nothing on the client can invent a colour for it that
> looks like rock rather than like a bug, and a renamed one changes nothing the player sees. The client holds
> a static table (`BlockAppearance.Palette`) keyed to the engine version instead.

Two mitigations, both cheap, neither optional:

1. **Per-chunk base hash.** Server sends the hash of its generated base alongside the delta. Client verifies after generating; on mismatch it discards and requests the full merged chunk. This turns a silent desync into a bandwidth blip.
2. **Hard version gate at login.** Client and server compare the full pipeline version vector. Mismatch → client must update. No partial compatibility.

Keep the option open from day one by keeping the pipeline deterministic and shipping generation code in a form the client *could* link — just don't depend on it until you have real chunk-traffic numbers.

### Derived structures — never query voxels in hot paths

The merged voxel state is the source of truth, but hot-path queries must not touch it. Maintain **derived structures alongside the voxels, incrementally updated on delta application.** This is `worldgen-derived`.

**Navmesh.** Do not derive it from voxels at request time — voxelized navmesh rebuilds are expensive and you would be doing them every time a player places a block. Keep a persistent tile-based navmesh per region (this is exactly what Recast's tiling exists for). On delta application, rebuild only the affected tiles. Budget the rebuilds: queue them, rate-limit per player, and accept that pathing is stale for a few hundred milliseconds. NPCs walking through a doorway that closed 200 ms ago is an acceptable artifact; a 40 ms hitch on the zone thread every time someone places a fence is not.

**Line of sight.** Raycasting through 3D voxels per attack, per frame, for every combat pair, gets expensive fast. Keep a coarser occlusion structure — a 2×2×2 or 4×4×4 downsampled opacity grid, or a per-chunk column-height field for the common case — updated on delta application. You lose precision at edges, which players will occasionally notice, but the alternative dominates your combat tick.

**Other candidates:** a per-chunk "has any solid above y" summary for fast sky/rain checks, a coarse water-volume field for swimming and drowning checks, and a settlement occupancy grid for NPC schedule resolution.

All follow the same pattern: cheap to query, incrementally updatable, rebuilt from voxels only on invalidation.

> **Implemented** in `derived/`, with two design choices worth recording because both cut against the obvious.
>
> **Walkability is stored as spans with no links.** `WalkableTile.kt` holds, per column, the set of walkable
> surface *heights* for an `AgentProfile` (height, max step, max wade depth) in CSR layout. It stores no edges.
> Two spans connect if their surfaces are within `maxStep`, which is a subtraction — cheaper to evaluate than
> to look up, and it means cross-chunk connectivity is answered at query time from two tiles
> (`DerivedStore.canStep`). The payoff is the invalidation blast radius: one block placement invalidates
> exactly one tile, never its neighbours. Recast-style stored links would have made it nine.
>
> Surfaces and `maxStep` are fractional, not voxel indices, and that turned out to be load bearing rather than
> cosmetic: rounded to indices a one-in-five gradient becomes a sequence of one-voxel cliffs, and a strict
> step limit then reads them as a wall, so agents would refuse to walk up a gentle slope.
>
> **The opacity grid stores a fraction, not a boolean.** `OpacityGrid.kt` downsamples 4× and records how full
> of opaque material each coarse cell is — occupancy-weighted, so a voxel thirty percent full of stone occludes
> thirty percent as much. A boolean forces a bad choice at exactly the resolution boundary the downsampling
> creates: round up and a fence post blocks four metres of sight; round down and players see through a
> one-voxel wall. The second is the one they will build specifically to exploit. Rays accumulate opacity
> against a threshold instead.
>
> `ColumnSummary.kt` answers the section's "other candidates" — surface height, water depth, and whether a
> column is sheltered (a floor exists below an air gap, i.e. indoors) — in one downward pass per column.
>
> **The rebuild budget is the whole point of `DerivedStore.kt`.** `invalidate(chunk)` marks a structure
> stale and queues it; queries keep serving the stale structure; `rebuild(budget)` does at most `budget`
> tiles per call. So the zone thread never takes a rebuild hitch, at the cost of pathing being briefly
> wrong — which is the trade this section argues for, made explicit and testable.
>
> `ChunkService` is now the `invalidate`/`rebuild` half of that loop for real: `ChunkStore`'s `onChanged` hook
> marks the edited chunk stale and `ChunkStreamSystem` spends `derived-rebuilds-per-tick` on the queue every
> tick, so the budget is a running property of the server rather than a tested capability.
>
> Not implemented: a settlement occupancy grid (nothing occupies settlements yet), navmesh
> *polygonisation* — walkable spans are the substrate a navmesh would be built from, not the mesh itself —
> and any *reader*. The structures are kept fresh but nothing consults them: movement validation, line of
> sight and pathing still do not query them, so they are maintained for a consumer that does not exist yet.

### Summary of the split

| Concern | Server | Client |
|---|---|---|
| Merged voxel state | Authoritative | Local copy for rendering/prediction |
| Base generation | Yes | Optionally (bandwidth optimization only) |
| Delta storage | Authoritative, persisted | Received, never trusted |
| Navmesh | Authoritative, tile-incremental | Not needed |
| LOS / occlusion grid | Authoritative | Approximate copy for UI hints only |
| Vector feature set | Full, in RAM | Full, in RAM if generating base |
| History / NPC state | Authoritative, LOD-expanded | Received as needed |

> The *server* column holds for merged state, base generation, delta storage (in memory — see below) and the
> vector feature set, plus walkable tiles and an opacity grid standing in for navmesh and LOS, and all of it
> is now connected to the running game through `world/stream/`.
>
> The *client* column holds only its first row: the Godot client keeps a local copy of merged voxels for the
> session, decoding `RleCodec` payloads in C# and applying patches against a per-chunk revision. It does not
> generate base terrain and holds no vector features. Delta storage is received and never trusted, which is free
> here because the client cannot write to it at all.
>
> **It now renders them.** `Game/World/Mesh/` meshes chunks with surface nets — one vertex per cell straddling
> the surface, quads across each lattice edge that crosses it — on the thread pool, and `TerrainRenderer` installs
> the results a couple per frame. Two points are worth recording because both cut against the obvious.
>
> **The scalar field is sampled at cell corners, each the average of the eight cells meeting there.** That is what
> reads `Occupancy` correctly, and it is not interchangeable with sampling at cell centres. A surface written at
> 40.3 m gives cells `1.0, 0.3, 0.0`, hence corners `0.65` and `0.15`, and the crossing at 0.5 lands on exactly
> 40.3 — the averaging is linear and the interpolation inverts it, so the reconstruction is exact for every value,
> tested to a tenth of a millimetre. Read as centred density samples the same cells put the surface at 40.21 and,
> worse, could never place it above `topCell + 0.5`. The corollary is the useful part: **nothing has to declare
> which way a fraction fills.** A half-full cell with rock below and one with rock above both cross at the same
> place with the normal reversed, because the neighbours decide. A cave ceiling therefore needs no convention that
> a hillside does not, which is why this generalises to caves where a heightfield mesher cannot.
>
> **Cost tracks surface area, not volume.** `ChunkBands` records, per column, which cells sit near an occupancy run
> boundary — found with a vectorised run walk over the contiguous vertical axis, three SIMD calls for open terrain
> rather than 256 comparisons, about 30 µs per chunk. A chunk with no interior boundary is solid rock, open air or
> open water and is skipped before anything is allocated for it, which is most of a view volume. What survives is
> ~2 ms per chunk and ~5 500 triangles; a 121-chunk view is 236 ms of single-threaded meshing and 655 k triangles.
>
> Two things the design did not anticipate. **Sea level is a chunk boundary**, so a coastal plain is a chunk of air
> over a chunk of rock with no interior boundary in either — the surface between them belongs to neither chunk's
> bands and rendered as nothing until `ChunkBands.SeamAtFloor` existed. Each chunk owns the lattice edges at its own
> floor and not its ceiling, which splits every shared face between exactly one of the two. And **the store is read
> from more than one thread** now, so `_held` is a `ConcurrentDictionary`; the voxels inside a chunk are left
> unsynchronised on purpose, since a mesh mixing two revisions is superseded by the re-mesh the patch already queues.
>
> Not implemented: textures (vertex colour from the palette stands in), LOD (unnecessary at this triangle count),
> and any blocky pass for player-placed voxels — corner averaging collapses one-cell-thick features, so structures
> want their own meshes rather than single voxels.
>
> The one row that does not hold as written is **delta storage being persisted**: `ChunkStore` keeps deltas in
> a `LinkedHashMap` and the database is in-memory with `ddl-auto: create`, so edits die with the process. That
> is consistent rather than broken for now — a restart resets the delta *and* the revision to zero together,
> which is what a cached client would be holding. It stops being consistent the moment deltas outlive a
> restart, so **whatever persists a delta must persist its revision with it.**

---

## Build Order

1. ✅ **Framework first** — Stage trait, deterministic RNG, layer store, feature store, the offline viewer. Boring, but everything else depends on it.
2. ✅ **Vector primitives** — `worldgen-vector` in isolation, with unit tests on closest-point, station interpolation, and profile continuity. Build the chunk-boundary stress view now, before there is anything to stress.
3. ✅ **Heightfield → climate → hydrology → biomes.** Get to a believable-looking world map. Rivers become a vector graph here, not a raster mask.
4. ✅ **Erosion.** Stream-power at world scale, droplet at chunk scale, iterated with hydrology.
5. ✅ **Chunk materialization + RLE + feature stamping.** Get a player standing in a river valley that is continuous across chunks. Shippable vertical slice.
6. ✅ **Derived structures.** Navmesh tiles and opacity grid, with incremental update on delta. Do this before you have players building, not after.
7. ✅ **Resources + habitability + settlement placement + roads.** Roads reuse the vector machinery from step 2 with no new code. *This held — roads and bridges added no geometry code, only a cost field and a route finder.*
8. ✅ **Town layout + buildings.** *Built third of the three, because a town's walls, wealth and size all
    come out of its history — see [Steps 8 to 10 run in the order 10, 8, 9](#steps-8-to-10-run-in-the-order-10-8-9).
    The design's blocks-then-lots did not survive a river running through a town; plots front the streets
    instead (deviation 8).*
9. ✅ **Economy + NPC distribution.** Settlements come alive. *Businesses, sectors and household seeds do;
    living NPCs are a runtime concern and are not here. The precondition trace behind every trade is the
    part that turned out to matter most, because it is what makes "why is there no baker" answerable.*
10. ✅ **History simulation.** Retrofit ruins, artifacts, and grudges into the existing world. *The retrofit
    framing held in the way that mattered - history does not place settlements - and inverted in the way that
    did not: it has to run before the towns it explains.*
11. ✅ **Glacial features.** Optional; the vector machinery already exists by now, so it's one stage rather than a subsystem. *Also held — one stage, and fjord sills fell out of flux-proportional overdeepening rather than needing their own rule.*
12. ◐ **Distribution & caching.** A single-node pipeline that's a pure function distributes almost mechanically once the purity discipline is in place from step 1. *Caching, delta and baking done and now driven by the zone-server; sharding, work queue and gRPC belong to the server. Delta persistence still missing.*
13. ◐ **Client-side base generation.** Bandwidth optimization only, with base hashing and version gating, once you have real traffic numbers. *Merged RLE chunks ship, which is what this step said to do first; base hashing and the version gate are built and ride along unused. A client-side generator waits on traffic numbers — and on the client not being C#, whose float path is the risk this step names.*

The one thing the ordering did not anticipate: **announcing before sending.** Step 13 frames bandwidth as a
choice between merged chunks and base-plus-delta, but most of the saving turned out to be available without
either — a manifest of `(position, revision)` pairs costs about 1.5 kB where the chunks it describes cost
375 kB, so a client re-entering somewhere it has been downloads nothing. That needs no client-side generator
and no bit-identical floats; it needs only that the server know what each client holds. It is also what makes
patches possible at all, which is the larger win: an edit seen by thirty players costs thirty copies of fifty
bytes instead of thirty re-sent chunks.

The ordering matters. Step 1's determinism discipline is what makes step 12 nearly free — defer purity and you'll rewrite everything. Step 2's vector primitives are what make rivers, roads, glaciers, and settlement grading all one problem instead of four, and what make chunk seams a non-issue rather than a permanent source of bugs.
