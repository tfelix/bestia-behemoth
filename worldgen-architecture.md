# World Generation Architecture for a Distributed MMORPG

**Status:** Design document, partly implemented — see [Implementation Status](#implementation-status)
**Scope:** World generation pipeline, vector feature system, chunk materialization, client/server split
**Code:** the `worldgen/` Gradle module

This document is the design, and it is deliberately kept as written rather than rewritten to match the
code. Where the implementation deviates, the deviation is recorded in the next section and at the point
in the code where it happens — the design is the argument, the status section is the ledger.

---

## Implementation Status

Build-order steps **1–7** and **11** are implemented, along with the parts of **12** and **13** that
belong in a module with no I/O in it. Steps **8–10** are not started. 227 unit tests, plus a seed-sweep
regression harness.

| # | Step | Status | Where |
|---|---|---|---|
| 1 | Framework + offline viewer | **done** | `core/`, `viewer/` |
| 2 | Vector primitives | **done** | `vector/` |
| 3 | Heightfield → climate → hydrology → biomes | **done** — deviations 3, 4 | `geo/`, `climate/`, `hydro/`, `bio/` |
| 4 | Erosion | **done** — deviations 1, 2, 6 | `geo/ErosionStage.kt`, `geo/WorldHeightField.kt` |
| 5 | Chunk materialization + RLE + feature stamping | **done**, plus occupancy — no scatter pass, no caves | `voxel/` |
| 6 | Derived structures | **done** | `derived/` |
| 7 | Resources + habitability + settlements + roads | **done** — deviations 5, 7 | `resource/`, `civ/` |
| 8 | Town layout + buildings | **not started** | — |
| 9 | Economy + NPC distribution | **not started** | — |
| 10 | History simulation | **not started** | — |
| 11 | Glacial features | **done** | `geo/GlacialStage.kt` |
| 12 | Distribution & caching | **partial** — cache tiers, delta, baking done and wired to the server; no delta persistence, no sharding/queue/gRPC | `store/`, zone-server `world/stream/` |
| 13 | Client-side base generation | **partial** — merged-RLE wire format, base hashing and version gate done; no client-side generator | `store/`, zone-server `world/stream/`, client `Game/World/` |

Steps 8, 9 and 10 are one subsystem in practice and are deferred as a unit: buildings need zoning,
zoning needs an economy, and the economy's shape comes out of the history. Doing any one of them
alone means inventing a placeholder for the other two.

The service half of step 12 — Hilbert sharding, consistent hashing, the distributed work queue, the
gRPC surface — is deliberately *not* here. It belongs to the server that owns those concerns rather
than to a module whose entire value is being a pure function over data. Likewise the wire format half
of step 13: `store/` provides the base hash and the version gate that make client-side generation
*safe*, and the protocol that would use them is the network layer's business.

### Actual module layout

The [Module layout](#module-layout) section below plans fifteen Gradle modules. The code is **one**
module, `worldgen/`, with those divisions expressed as packages. Fifteen modules over ~90 files buys
build-graph enforcement of a layering that a package convention and one review already enforce, at
the cost of fifteen build files. The split is worth revisiting if the module ever grows a second
consumer that needs only part of it.

Layers, each of which may use the ones above it and nothing below:

```
vector/     geometry, polylines, station tables, profiles, blending, spatial index
core/       Stage, GenContext, RNG, layer + feature stores, pipeline, chunk column sampling
fields/     noise, grids, distance transforms, Poisson disk, heaps

geo/        tectonics, plates, orogeny, stream-power erosion, glacial flow, base heightfield
climate/    temperature, wind belts, orographic precipitation
hydro/      priority-flood, flow routing, lakes, river graph, meandering
bio/        biome classification, soil
resource/   deposit generation
civ/        habitability, cultures, settlement placement, route finding, roads
voxel/      block palette, stratigraphy, chunk materialisation, RLE codec

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

**Chunk streaming is wired.** `net.bestia.zone.world.stream` owns the `ChunkCache` → `ChunkStore` →
`DerivedStore` chain that this module had never been asked for, and streams merged RLE chunks to the client
over seven new `bnet-messages` in the MAP range. The protocol is *announce, then serve on request*: a
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
tectonics → climate → erosion → glacial
                          \→ hydrology → biomes → resources → habitability → settlements
```

Nine world-tier stages, in `pipeline/StandardWorld.kt`. Each declares only what it reads and the
scheduler enforces that, so the stage list is the entire wiring — there is no order to get right
beyond the dependencies the stages already state. Emitted layers and feature kinds:

| Stage | Raster layers | Vector features |
|---|---|---|
| `tectonics` | `BEDROCK_ELEVATION`, `PLATE_ID`, `ROCK_HARDNESS`, `CRUST_AGE`, `UPLIFT` | `FAULT` |
| `climate` | `TEMPERATURE`, `TEMPERATURE_RANGE`, `PRECIPITATION`, `PRECIPITATION_SEASONALITY`, `DISTANCE_TO_OCEAN` | — |
| `erosion` | `ELEVATION`, `SEDIMENT` | — |
| `hydrology` | `FLOW_DIRECTION`, `FLOW_ACCUMULATION`, `DISCHARGE`, `WATER_LEVEL`, `LAKE_ID` | `RIVER_CHANNEL`, `RIVER_CONFLUENCE` |
| `biomes` | `BIOME`, `BIOME_CONFIDENCE`, `SOIL_FERTILITY`, `SOIL_DEPTH` | — |
| `glacial` | `ICE_THICKNESS` | `GLACIAL_TROUGH`, `FJORD`, `CIRQUE`, `MORAINE` |
| `resources` | `RESOURCE_VALUE` | `ORE_DEPOSIT` |
| `habitability` | `HABITABILITY`, `MOVEMENT_COST` | — |
| `settlements` | — | `SETTLEMENT`, `SETTLEMENT_GRADING`, `ROAD`, `BRIDGE` |

Climate runs four times coarser than the heightfield, as the design calls for, except on worlds too
small for that to leave a usable grid.

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
inner edge), and `normaliseLandFraction` counts interior cells only so the margin does not eat into the
land budget. The wrap then happens between two stretches of featureless deep ocean, which look alike
because there is nothing there to look at. This is honestly not continuity, and it only works while the
margin is wider than the client's view distance.

The margin is therefore **2.5 km flat, not a share of the world.** What it has to beat is view distance,
which is a few hundred metres and has nothing to do with world size; sizing it as a share made a big
world's margin enormous while hiding the seam no better. A share cap of 6% still applies, but only binds
below about a 42 km world — the margin goes as the perimeter while land goes as the area, so on a tiny
world a fixed margin would eat the place.

`wrapX` is on by default and `wrapY` is off, and not merely for want of implementing it: temperature comes
from latitude, so wrapping north to south walks one pole straight into the other. `core/WorldWrap.kt` holds
the coordinate maths — floor-mod normalisation, signed shortest-path deltas, chunk normalisation (never on
z; up is not a loop). **Chunk streaming is its first caller** — every chunk address, computed or client-supplied,
goes through `normalise` before it reaches the generator, so walking off the eastern edge streams the western
terrain. **The rest of the ECS still does not call it**: movement, interest management and pathing use naive
subtraction, so two players ten metres apart across the seam read as a world apart.

### Deliberate deviations

Each is also noted at the point in the code where it happens, because a deviation visible in only one
file is one somebody will later mistake for a bug.

1. **Detail erosion is analytic noise, not particle droplet erosion.** Droplets are stateful and
   non-local; doing them seam-free needs the overlap-and-blend machinery of the design's step 4, and
   any error in that blend puts back exactly the seams the vector tier exists to remove. The analytic
   field is seam-free by construction because it is a pure function of world position.
   (`geo/WorldHeightField.kt`)
2. **Alluvial fans and deltas are raster deposition, not vector polygons.** The vector tier has no
   polygon type; adding one is a subsystem, not a stage. (`geo/ErosionStage.kt`)
3. **Lakes live in the raster tier**, as a water level plus a basin label, rather than as vector
   features. That follows the design's own rule — a feature narrower than about three coarse cells
   belongs in the vector layer, and lakes generally are not. Small ponds and oxbows *would* be, and
   are not generated.
4. **Edge biomes are raster distance transforms, not vector buffers.** Coastlines are not vector features
   at all, so a beach is a band around ocean cells rather than a strip inside a coastline polyline; and
   riparian corridors buffer high-discharge *cells* rather than the river polylines that already exist.
   Both are therefore quantised to the coarse cell instead of being crisp at any resolution. Related:
   only the winning biome is stored, not the top-2 pair with its blend weight. (`bio/BiomeStage.kt`)
5. **Settlement footprints are discs, not polygons** — a radial terrace rather than an outline with a
   street graph inside it. The polygon and everything it would contain is step 8.
6. **Rivers do not follow glacial troughs.** Hydrology routes over the raster and a trough exists only
   in the vector tier, so a post-glacial river does not know to run along the trough floor it should
   have inherited. Fixing it means either rasterising troughs before hydrology or routing flow against
   the vector tier; both are larger than the artefact.
7. **Roads do not connect across water.** A route that would cross the sea is rejected rather than
   bridged, so two settlements on different landmasses are not road-linked. They would be linked by a
   sea lane, which is a feature kind that does not exist yet.

Also unbuilt, and smaller: caves (the design's cave systems are vector features that nothing emits),
seasonal precipitation (one annual pass, not 3–4 monthly ones), navigable rivers as cheap trade-graph
edges, and the place → route → regrow → replace settlement iteration (single pass).

### Running it

```
./gradlew :worldgen:test                                    # unit tests
./gradlew :worldgen:viewer                                  # interactive layer/feature inspector
./gradlew :worldgen:viewerExport -Pout=build/viewer         # same, rendered to PNGs; works over SSH
./gradlew :worldgen:invariants -Pseeds=200 -Pcells=256      # seed sweep against the invariants
```

The viewer is the primary debugging tool and has earned it — a road running dead straight across open
ocean and a mis-classification of inland troughs as fjords were both found by looking at an exported
PNG, not by a test.

The seam stress view runs on every export and prints, e.g.,
`SeamCheck: clean - 64 chunks, 3584 shared columns agree`. It generates a block of chunks
independently and fails if adjacent chunks disagree on any shared column's height.

Invariants currently asserted per seed: layers are finite; the land fraction is plausible; normalised
layers stay in range; discharge grows downstream; river beds descend; lakes stand above their beds;
water is where the biome says it is; feature bounds contain their geometry; no settlement is in the
sea; settlements respect their tier's separation; deposits are well formed; every fjord sill is
shallower than its landward basin; and the ocean margin contains neither land nor settlements.

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
> size. One annual orographic pass rather than 3–4 seasonal ones; seasonality is emitted as a scalar
> (`PRECIPITATION_SEASONALITY`) instead of monthly fields, which is enough for biomes but would not be
> enough for agriculture-by-month.

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

> **Not implemented.** Settlements exist as point features with a tier, culture and population, and the
> ground under them is graded, but there is no street graph, no lots, no zoning and no buildings.

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

> **Not implemented.** Settlements carry a population figure derived from tier and habitability, and
> nothing else from this section exists.

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

> **Not implemented.** The world tier has no history log, so `World` carries rasters and features only.

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
> Missing: the polygon geometry type. Everything shipped is a polyline or a point, which is why fans,
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
> `OreVeins` and `BridgeDecks`. Grading is a `REPLACE`-blended radial terrace applied with the rest of the
> features, so it is in the heightfield before stratification as required.
>
> Not present: `carve_caves`, `place_vegetation`, `place_buildings`, `apply_history_marks` — the scatter
> pass in its entirety, plus caves. Nothing chunk-seeded exists yet, which means the "chunk-seeded
> randomness is safe here, not in profiles" line is currently a rule with no users.

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

### Data-driven configuration

Biome definitions, culture profiles, business types with their preconditions, building grammars, resource geology rules, feature profile parameters, and event templates all belong in **data files, not code**. Load and validate at startup. This is the difference between a system a designer can tune and one that needs an engineer for every change. Hot-reload them in the offline viewer.

> **Not implemented.** Every tunable is a Kotlin `data class` with defaults — `TectonicsParams`,
> `ClimateParams`, `GlacialParams`, `SettlementParams`, `Culture`, the biome prototype table. They are
> already grouped and named as this section wants, so extracting them to files is a serialisation layer
> rather than a redesign, but a designer cannot currently tune anything without a recompile. Worth doing
> when there is a designer; not before, because the shape of the parameters is still moving.

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
> Not implemented: history scrubbing, settlement economy/NPC inspection, and seed diffing — the first two
> because steps 9 and 10 do not exist.

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
> are what the hash must be over. A client that does not generate base chunks short-circuits to
> `ServerAuthoritativeOnly` and needs no version agreement at all; it is always sent merged chunks, so none
> of the three can hurt it.

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
8. ⬜ **Town layout + buildings.**
9. ⬜ **Economy + NPC distribution.** Settlements come alive.
10. ⬜ **History simulation.** Retrofit ruins, artifacts, and grudges into the existing world.
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
