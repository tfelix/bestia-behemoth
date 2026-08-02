# worldgen: the standalone-generator branch

What was built after the original eight-phase plan closed, why each decision went the way it did, what was
deliberately *not* built, and what the numbers say. Written at the end of the version-reset pass.

The scope was **the `worldgen/` module and its tooling only** — no zone-server integration, no ECS readers, no
delta persistence, no client rendering. The two places the client is touched at all are a version constant and a
palette entry, both of which have to move in the same commit as the server's by construction.

---

## The acceptance rule this branch worked under

Nothing is released. There is no world to stay compatible with, so no phase carried a byte-compatibility gate.
What replaced it was **the distributions the documents already record**: land fraction median ≈ 0.50, no world
without a lake, `SeamCheck: clean`. Those are statements about whether the world is *right*, not about whether it
is the *same*, and a phase that moves the terrain is expected to move it.

Three standing gates, applied to every phase:

- **(a) Distributions, not bytes.** Seed-by-seed identity was never required.
- **(b) Every regression test confirmed failing against the old code** before it was kept.
- **(c) Count the output.** A subsystem that is complete, tested and never *reached* looks exactly like one that
  works. Where existence could not be asserted per seed, it was pinned to a seed that has the thing — never to a
  conditional that passes vacuously.

Gate (b) earned its place twice on this branch by failing *my own* new code, and gate (c) is why four tests in
the suite exist only to assert that the world under another test still contains the ingredient it tests.

---

## What was built

### Params as data, with a hash that can tell two files apart

Every tunable in the module — around 220 of them across 17 classes — became a hashable, file-settable value.
`ParamsDigest` folds `(name, raw bits)` pairs sorted by name; each params class carries its own `digest()` next
to its fields; `Stage.paramsVersion` is **abstract with no default**, so a new stage whose tunables are unhashed
does not compile. `WorldGenPipeline` folds all of it, plus a `chunkTierVersion` covering the tier that decides
terrain and is not a stage.

The load-bearing part is not the file format. It is that **the digest never reaches `GenRng.derive`**: params
move the version vector and the cache key and nothing else, so "change one number and look at the same world"
still works. That is the module's first debugging habit, and folding params into the streams would have deleted
it. A pinned test asserts it directly.

Two rejected options are worth recording. `data class.hashCode()` is out because `DoubleArray.hashCode()` is
identity-based, so every JVM process would have computed a different `pipelineVersion` — a live development bug,
not a release one. `java.util.Properties` is out because it is a `Hashtable`: it discards line numbers, so
"unknown key at line 12" is inexpressible, and a duplicate key silently wins last, which is the exact
silent-merge failure the phase existed to remove. The hand-rolled parser is ~150 lines and reports both.

A `checkBoundaries` gradle task went in *first*, before the temptation: it asserts the compile classpath is
kotlin-stdlib only and that no `java.io`/`java.nio`/`java.awt`/`javax` import appears outside `viewer/`.

### The calibration batch

`BIOME_CONFIDENCE` became a percentile rank over the world's own distribution, and the `DITHER_CUTOFF`
compensation in `SurfaceSampler` — which existed only because the layer was miscalibrated — was deleted with it.
Precipitation seasonality became the concentration index. Droplet erosion is measured and still ships off, on
cost.

### Voxel subtraction

`StructureSpans` learned removal, with **`AIR` as the sentinel rather than an inverted span**, because
`ChunkDelta.set(x, y, z, AIR)` already spells removal that way and generation and player digging should share one
vocabulary. The `to <= from` drop *stays* a silent drop rather than meaning "remove": an accidentally inverted
`add` is a routine slip and it must build nothing rather than dig a hole in a hillside.

`ChunkMaterializer.carve` runs **last**, after the occupancy write, repairing both arrays as it goes. Carving
last is the only order with the right semantics — a hole is defined by the material it is a hole *in*, so a
shaft has to be able to pierce its own collar.

The asymmetry to know about: **a void's floor rounds by the fill rule and its ceiling by the centre rule.** Every
derived reader treats a fractional voxel as filled from below, so a fractional ceiling would appear as a phantom
standable surface *inside solid rock*. The cost is up to a voxel of head height, and it is the right trade.

### Caves

A cave passage is a **stored polyline feature**; the chunk tier does no cave randomness at all. A hashed 3D
density field was rejected: it gives disconnected blobs, and the carve is decided per column as a span, so there
is no third dimension to vary over anyway. A `MarkerFeature` with a station table gets connectivity by
construction and inherits the river seam theorem verbatim.

Placement is lithology-driven with no extra code — suitability is `limestoneShare × ramp(precipitation)`, and
`limestoneShare` walks the same shared `Stratigraphy` the materialiser uses. A candidate is **rejected outright
unless a gallery roof reaches a dry surface**, so every emitted system has an entrance by construction and
existence is assertable per seed rather than needing a pinned one.

`ColumnSummary.isSheltered` was fixed on the way past: it kept the *shallowest* gap, so a column of open grass
with a cave thirty metres below it reported sheltered, and "is it raining on this NPC" answered wrongly.

### Vegetation

The whole answer to the checkerboard is that **the decision unit has to be larger than a voxel**. A smooth
probability sampled per column is still an independent coin flip per column; what works is a smooth field, a
clearing cutoff, *and* a hash on a four-metre lattice of quantised world coordinates. Trees are implicit — a
512 km world holds around a billion — so there is no feature and no per-tree storage anywhere.

`LEAVES(solid = false)` does three jobs with zero derived-tier changes: `highestSolid` reports the ground under a
tree so nothing spawns twelve metres up, `WalkableTile` lets agents walk *under* a canopy, and `highestNonAir`
still counts it so `probe` draws a tree.

`BlockType.opacity` was added because neither boolean is right for foliage: `opaque = true` means one voxel of
leaves in four stops a sight line, so **no archer can shoot through any forest**, and `opaque = false` means a
hundred metres of canopy blocks nothing.

The verification is a **negative control**: measure canopy run length along a scanline, then build the
per-column-hash variant *inside the test* and assert it fails the same bound.

### The polygon type, and four landforms

`vector/Ring.kt` is a simple closed polygon that deliberately never enters `Polyline`'s contract — `Polyline` is
documented as *open*, and a ring violates it six ways. Its `contains` is a crossing count **entirely in fixed
point**: quantise once at construction into `Long`, then branch in integers, with the half-open edge rule
resolving vertex-on-ray and point-on-edge totally. No epsilon anywhere, so every chunk decides a lake's shoreline
the same way. `init` rejects self-intersection, which is what makes `contains` well-defined at all.

`StationTable` learned `periodic`, which fixes the *slope* at the seam as well as the value. The default
reproduces the old arithmetic exactly, and the guard is that the whole of `StationTableTest` stayed green
untouched.

Four producers: `LAKE` and `OXBOW_LAKE` from `PondStage`, `ALLUVIAL_FAN` and `DELTA` from `AlluviumStage`, plus a
third water surface in the chunk tier.

The lake had to be **the moraine-dammed pond specifically**. A cirque tarn is already a raster lake, because
`GlacialStage` rasterises its `MIN` carve and priority-flood finds every bowl — so a polygon there would have to
*remove* raster water, and a feature cannot subtract. The water body the raster lacks by construction rather than
by resolution is the pond behind a moraine, because `carveInto` filters to `MIN` blend precisely so the additive
moraine is not rasterised.

`WorldGround.gradingFaded` was deleted on the way past: it re-implemented `PointFeature.falloff` and the
`REPLACE` blend by hand, with its own copy of `edgeFraction`, on the stated grounds that the grading feature did
not exist yet. It did.

### Town quarters, and the block idea that comes back inverted

`civ/Districts.kt` is the revival of deviation 8, and it is deliberately **not** `StreetGraph.faces` again.
Blocks were built once the way the design describes — as the faces of the street graph — and removed, because a
face exists only when ring streets *close*, so one river through a town broke every ring's cycle and the
reference city went from 574 plots to 68. Making the existence of a plot depend on a graph cycle is the mistake,
and better face-finding does not fix it.

A district instead grows **from the plots that exist**: cluster the placed buildings of one trade by proximity,
take the convex hull of their corners, push it outward past the street they front onto, and store it as an
`AreaFeature` with **no profile**. Nothing about a plot depends on it, so a river through a town now costs the
town a district rather than its buildings.

Convex is the load-bearing choice. A flood-filled block boundary fits the ground better and can be
self-intersecting, non-manifold or annular, none of which `Ring` accepts; a convex hull is simple by
construction, which is exactly the precondition. The cost is that a district around an L of streets claims the
corner between the arms — and that corner is the street, which is the thing a district is around.

The hull is then *simplified* to fit `Ring.MAX_VERTICES`, and simplification cuts corners off — each cut moving
the boundary inward across ground that had a building on it. That is what
`checkDistrictsHoldTheirBuildings` exists for, and it was confirmed failing by pulling the margin inward before
it was kept.

### Two worlds, one difference field

`:worldgen:diff` is the other half of "change one number and look at the same world". Making the change was
solved by putting params in a file; seeing the *consequence* was not. Two exports side by side answer "these
look different" and not where or by how much — on a 1400 px picture of a 512 km world, twenty metres off every
valley floor is invisible beside a coastline that moved one pixel.

So the output is a **table of mean and worst absolute difference per layer, loudest first**, in each layer's own
units, and difference PNGs on request. The table is the half that matters and the only half that works in a
terminal. It compares two seeds *or* two params files, never both, and refuses the combination rather than
picking one — a difference with two causes in it says nothing about either.

Two things it says out loud rather than silently. Chunk-backed fields are **skipped**, because a field that
generates on demand would cost a chunk per probe, and "the voxel tier did not move" and "the voxel tier was not
looked at" are otherwise the same output. And when nothing moved at all it says so as a *warning*: two worlds
built from different seeds that agree everywhere mean the seed did not reach the generator.

### The version reset and the cleanup pass

Every stage `version` reset to 1 in one commit, with the accumulated per-version changelog comments stripped into
git history. `ChunkEngine.VERSION` went with them on both sides. `Biome.of` was deleted. Baked blobs are re-keyed
on `pipelineVersion`. Ten `else ->` fallbacks over domain enums were made exhaustive. Details below.

---

## The numbers

Measured at the end of the branch. 545 unit tests, `checkBoundaries` green, 200-seed sweeps clean at both sizes,
both exports `SeamCheck: clean` **and** `VoxelSeamCheck: clean`.

### 200-seed sweeps

| | 192 cells | 256 cells | guideline |
|---|---|---|---|
| land fraction, median | 0.504 | 0.504 | ≈ 0.50 |
| land fraction, range | 0.484 – 0.646 | 0.488 – 0.646 | 0.486 – 0.715 |
| lakes, median | 11 | 20 | **0 of 200 worlds with none** |
| caves, median | 11 | 21 | 0 of 200 with none |
| canopy over land, median | 0.134 | 0.139 | 0 of 200 all but bare |
| vector ponds | 313 in all | 590 in all | — |
| oxbow lakes | 140 in all | 299 in all | — |
| deltas | 9 623 in all | 9 463 in all | — |
| alluvial fans | 130 in all | 127 in all | — |
| town districts, median | 168 | 323 | 0 of 200 with none |
| feature index cell size | median 5 149 m | median 5 014 m | — |
| index oversized | median 0, max 3 | median 0, max 6 | — |

The land-fraction *range* narrowed at the top (0.715 → 0.646) purely because the reset drew a different 200
worlds; the median did not move.

### The reference world, 512 km

Land 0.515 of the world, elevation −4 711 .. 3 836 m. Green 73%. Canopy 0.161 mean over land, 42.1% of it wooded.
77 cave systems, 308 passages, 200 km of gallery, 77 entrances, 2.8% of land within 4 km of a way in. 26 662
buildings, 3 674 streets, 292 settlements, **1 512 districts**. 3 lakes, 10 oxbow lakes, 160 deltas, 364 moraines.

Adding districts moved **no other figure at all** — land, lakes, caves, canopy and pond counts are identical to
the run before them, at both sizes. That is the intended shape and it is what `a district is a query surface and
never touches the ground` asserts directly: a quarter with a profile would be a town-sized terrace.

### What a chunk costs

Measured over 196 chunks: **19 383 bytes** encoded, **2 263 bytes** deflated. That is 3.7% of the raw voxels
before compression and 0.4% after.

---

## Decisions, and the reasons

### Two plan premises were overturned by measuring them

**The feature index's overflow list is empty.** The polygon phase opened by measuring `FeatureIndex`, because the
extent cap on areal features was supposed to be set from that number. It turned out the oversized list is empty
on every world sampled: a cell is about 5 km and the overflow threshold is 256 cells, so nothing short of an
80 km feature can land in it, and the `SEA_LANE` KDoc claiming otherwise was wrong. Designing around it would
have been designing around nothing. The real index cost is a *town*: one bucket holds 1 500–1 800 features, 37%
of a world's total in one cell. Recorded with the number and deliberately not fixed — the mean is 8.1, at target,
and no export timing has ever shown it.

**The tighter RLE format is worth a fifth, not a factor.** `RleCodec`'s KDoc recorded a format the zone-server's
earlier writer used — one flag bit per run, an occupancy byte only where occupancy is not derivable from material
— described as *"several times tighter"* and said to be blocked because it caps the palette at 64 blocks. Both
halves were written without measuring. Implemented against 196 real chunks:

| format | bytes/chunk | deflated |
|---|---|---|
| two streams, as shipped | 19 383 | 2 263 |
| merged runs, sparse ids | 14 629 (−24.5%) | 1 838 (−18.8%) |
| merged runs, dense ids | 14 629 (−24.5%) | — |

So it is 425 bytes a chunk on the path that actually stores them, and it would cost a wire format that three
modules and two languages have to agree on. **Declined**, with the numbers in the KDoc where the claim used to
be, and the architecture document's own instruction — ship merged RLE first, optimise once there are traffic
numbers — followed rather than apologised for.

The palette was never the blocker either. That came from the original scheme packing material into the low six
bits of a flag byte; writing the flag as the low bit of a *varint* id has no ceiling at all. The third row is the
consequence: **densely renumbering `BlockType` saves exactly zero bytes**, because the only ids above 64 are ore
and ore is rare. So the renumber the plan called for was **not done**, and the grouping the gaps encode —
basement 10-11, sedimentary 20-23, unconsolidated 30-35, worked 60-67, ore 100-120 — is kept, because it is worth
more than a saving that measured as nothing.

### `RleCodec.VERSION` was not reset, and it is the only one

Every other version number in the module is a development counter. This one is a byte written into every payload
and named in the wire protocol — `CHUNK_ENCODING_RLE_V2` in `chunk.proto`, `RleCodec.Version` in the client.
Resetting it would rename an enum across three modules and force a protobuf regeneration to say the same thing
the format already says about itself, and the format did not change.

### Rivers here do not meander, so two landforms are rare

The plan assumed a `Meander.offset` to read oxbows off. There is none, and `hydro/` has no meander model at all —
these are smoothed D8 flow paths. So an oxbow has to be detected as a 92° accumulated bend rather than as a real
cut-off, and an alluvial fan needs a slope break that a smoothed path rarely presents: fans hold at roughly 15
per 20 worlds no matter how far the gradient bracket is loosened, measured across four settings. Deltas, needing
only a river mouth, come out in their hundreds. Both findings are recorded as tables in the KDocs rather than
tuned around.

### Areal features stay out of the coarse raster carve

`GlacialStage.carveInto` walks `outline()` stamping a `corridorWidthMax` band, which on a ring stamps the rim and
misses the interior. Inflating `corridorWidthMax` to the inradius would be a lie about reach and would undo the
21× speedup that path bought. `isRasterisable` states it as a predicate and a test asserts both directions — that
no `AreaFeature` ever reaches it, *and* that a corridor still does, so the predicate is not too broad.

---

## Three defects the discipline found

**A centroid computed with the pre-normalisation winding is reflected through the origin.** Every `RingTest`
fixture was counter-clockwise so all of them passed. The first clockwise producer reported its centroid 150 km
outside the world, and since `contains` pre-rejects against a disc centred on it, containment answered false
everywhere. It surfaced as *no ponds on any world* — which is habit 6, one layer down.

**A guard in front of an exact decision has to be exact too.** `Ring.contains` pre-rejected on a raw-double bbox
test, directly beneath a paragraph promising there were no float branches. Found by deliberately breaking the
*edge rule* and watching the *quantisation* test go red instead.

**A prediction of the ground that reads the raster is wrong wherever a feature is additive.** `TownStage` decides
a building's floor from `WorldGround`, whose base is the `ELEVATION` layer, and stamped only the settlement
grading discs on top. But `carveInto` rasterises only the `MIN` carves — so a moraine, an alluvial fan and a delta
exist in the *chunk* and not in the layer. A town on a moraine had ten metres of ridge under it that nothing at
the world tier could see; `standsLevel` approved the lot and the house came out buried 7.7 m.

That last one is the argument for doing the version reset at all. The bug had been latent for as long as moraines
have existed, and no seed in a 200-seed sweep had put a settlement on one. Reshuffling every RNG stream at once
landed a town on one immediately — in a *unit test's* world, not a sweep's. The fix evaluates every
`affectsHeight` feature, which meant declaring glacial, alluvium and pond as `TownStage` dependencies: they are
read for their features, not their layers.

---

## Legacy workarounds dropped, and what each was protecting

| dropped | what it was protecting |
|---|---|
| Stage `version` counters up to 5, and their changelog comments | Upgrades from worlds that only this repository's own tests ever generated. Reset to 1; append-only again from the first release. |
| `ChunkEngine.VERSION = 4`, both sides | A compatibility statement to a client that does not exist yet. Reset to 1. |
| `Biome.of(ordinal)` | Nothing. It *coerced*, so the `NO_SECONDARY = -1` sentinel read back as a real biome and a cell with no runner-up claimed to be one. Two files warned about it in prose; deleting it is the version of that warning the compiler keeps. |
| `bakedKeyOf` omitting `pipelineVersion` | A migration path for released worlds. During development it meant a baked chunk outlived a generator change and read back indistinguishable from a fresh one. When there is something to migrate, the answer is a re-key step, not a hash that is incomplete on purpose. |
| Ten `else ->` fallbacks over domain enums | A plausible answer given on behalf of a case nobody had considered. `SurfaceCover.soil`/`.cap` over `Biome`, two tables in `civ/Terms`, three in `EconomyStage`, three in `TownBuildings`, two in `ResourceStage` — the last two now *throw* on an ore, because an ore never reaches them and the `else` was quietly ready to bury a new surface resource under 150 m of rock. |
| `BlockType.PLANK` | Nothing placed it since the mine head became an open shaft. Its own KDoc said to delete it at the palette pass if neither building interiors nor a headframe had arrived. Neither had. |
| `RleCodec`'s "the palette is already past 60" | A constraint that was chosen rather than imposed, and that measurement showed does not exist. |
| `WorldGround.gradingFaded` and `gradedGround` | A hand-rolled copy of `PointFeature.falloff` plus the `REPLACE` blend, written when the grading feature did not exist. It did. |
| `StreetParamsPublic`, and `minRadials = 4` | A mirror class and a KDoc'd rationale for a value that `toInternal()` always overwrote, so it had never run. |
| `DITHER_CUTOFF` | A per-consumer compensation for a miscalibrated `BIOME_CONFIDENCE` layer. Fixed at the source. |

What was **kept deliberately**: `else -> Unit` in loops that dispatch over `FeatureKind` and handle three of
forty, because a new kind genuinely should be ignored there; the `to <= from` silent drop in `StructureSpans`;
and `RleCodec.VERSION`.

---

## Deliberately not built

- **Oil and gas.** Dropped outright, not deferred. Not required for this game.
- **The export codec** (layers, features, chronicle, params as a serialisable surface). Specified rather than
  implemented, but every phase obeyed eight rules that keep it a codec later rather than a retrofit — most
  importantly that **every profile parameter lives in a station channel, not a captured constructor variable**,
  so a codec will need only a profile *name* plus a table it already has.
- **History depth.** Deities and monsters as `Chronicle` actor types, technology as a vector rather than a
  scalar, and event templates with pre- and postconditions. Not started. It is the item most entangled with
  the town-simulation work queued next, and the one where what to build is a question about the game rather
  than about the generator.
- **place → route → regrow → replace.** Settlement placement is one pass; iterating it means splitting
  `SettlementStage` into placement and routing, and the payoff stays speculative until something reads the
  second iteration. Not started.
- **Building interiors and the shape grammar.** Explicitly deferred to the town-simulation pass.
  `Building.grammarSeed` is stored and still read by nothing.
- **A click handler on the Swing viewer.** `probe -Pon=` answers "what is here" and `diff` answers "what
  changed"; clicking a reach in the window still does nothing.
- **Settlement outlines.** The one producer of the four that did not land. `AreaFeature` exists and the change is
  a shape swap on `SettlementStage.gradingFor`; it stopped on budget, not design.
- **Operations over two areas** — clipping, offsetting, boolean union. No producer has wanted one and each is its
  own subsystem.
- **Ring holes**, with the extension point noted.
- **`AreaFeature.tiled(...)`**, the escape hatch for an area wider than the extent cap. Specified in the `require`
  message and not built until a producer needs it.
- **A `mayInfluence` coarse mask** on the feature index. Not in the first slice, because a wrong one is a
  silently missing feature, which reads exactly like a chunk seam and sends you looking in the wrong place.
- **The region tier.** `StageScale.REGION` exists and wants a real consumer before it wants an implementation.
- **Anything that turns a tree into an entity a player can fell.**

---

## Where the remaining risk is

The sweep is the only proof that `Ring.init`'s preconditions do not throw on a world nobody has generated — the
direct analogue of the `Polyline` precondition that threw 113 worlds into a 200-seed run. Every emit site wraps
construction in `runCatching`, and 400 worlds across two sizes are clean.

`ChunkService.computeSurfaceSlabs` still subscribes only the slabs holding a heightfield surface, so **a cave in
the slab below is generated and never streamed**. `GeneratedWorld.contentSlabsOf` answers which slabs hold
anything; the server-side half is separate work.
