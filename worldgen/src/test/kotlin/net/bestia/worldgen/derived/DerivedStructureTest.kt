package net.bestia.worldgen.derived

import net.bestia.worldgen.core.ChunkPos
import net.bestia.worldgen.voxel.BlockType
import net.bestia.worldgen.voxel.Occupancy
import net.bestia.worldgen.voxel.RleCodec
import net.bestia.worldgen.voxel.VoxelChunk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DerivedStructureTest {

  private val pos = ChunkPos(0, 0, 0)
  private val size = 8
  private val height = 24

  /** Flat ground at z 0..3, air above. The baseline every case here perturbs. */
  private fun flatGround(): VoxelChunk {
    val chunk = VoxelChunk(pos, size, height)
    for (y in 0 until size) {
      for (x in 0 until size) {
        for (z in 0..3) chunk[x, y, z] = BlockType.GRANITE
        chunk[x, y, 3] = BlockType.GRASS
      }
    }
    return chunk
  }

  // --- Column summaries ------------------------------------------------------------------------------

  @Test
  fun `a column summary reports the surface, the water and what is sheltered`() {
    val chunk = flatGround()

    // A roof two blocks above the ground at one column: a building, or a cave mouth.
    chunk[2, 2, 6] = BlockType.MASONRY
    // Standing water at another.
    for (z in 4..5) chunk[5, 5, z] = BlockType.WATER

    val summary = ColumnSummary.of(chunk)

    assertEquals(3, summary.surfaceAt(0, 0))
    assertEquals(-1, summary.waterAt(0, 0))
    assertFalse(summary.hasShelter(0, 0), "open ground is not sheltered")

    // Under the roof: the top of the column is the roof, and the floor beneath the gap is the ground.
    assertEquals(6, summary.surfaceAt(2, 2))
    assertTrue(summary.hasShelter(2, 2))
    assertEquals(3, summary.shelteredFloorAt(2, 2))

    assertEquals(5, summary.waterAt(5, 5))
    assertEquals(2.0, summary.waterDepthAt(5, 5), 1e-9)
    assertFalse(summary.hasShelter(5, 5), "water is not a roof")
  }

  @Test
  fun `shelter is a place in a column, not a property of the whole column`() {
    // The failure this is written against: a column of open grass with a cave under it reported *sheltered*,
    // because the old test was "does this column have a gap anywhere in it". That is the wrong question, and
    // it answers "is it raining on this NPC" wrongly for every column over a cave in the world.
    val chunk = flatGround()

    // Bore a gallery out of the rock below, leaving the grass on top untouched.
    for (z in 1..2) chunk[4, 4, z] = BlockType.AIR

    val summary = ColumnSummary.of(chunk)

    assertTrue(summary.hasShelter(4, 4), "there is a gap in this column")
    assertTrue(summary.isShelteredAt(4, 4, 1.5), "standing inside the gallery")
    assertFalse(
      summary.isShelteredAt(4, 4, 4.0),
      "standing on the grass above a cave, in the rain"
    )

    // The bounds of the gap, which is what makes the answer possible at all.
    assertEquals(1.0, summary.shelteredFloorHeightAt(4, 4), 1e-9)
    assertEquals(3.0, summary.voidCeilingHeightAt(4, 4), 1e-9)
  }

  @Test
  fun `a column of nothing reports nothing rather than zero`() {
    val summary = ColumnSummary.of(VoxelChunk(pos, size, height))

    assertEquals(-1, summary.surfaceAt(0, 0))
    assertEquals(-1, summary.waterAt(0, 0))
    assertEquals(0.0, summary.waterDepthAt(0, 0), 1e-9)
  }

  // --- Opacity ---------------------------------------------------------------------------------------

  @Test
  fun `the opacity grid reports a fraction rather than a boolean`() {
    // The reason it is a fraction: a boolean forces a bad choice at the resolution boundary. "Opaque if any
    // voxel is" blocks four metres of sight with a fence post; "opaque if most are" lets players see through a
    // one-voxel wall, which is the failure they will find and exploit.
    val chunk = VoxelChunk(pos, size, height)
    // One voxel of solid in the lowest cell of the first column block.
    chunk[0, 0, 0] = BlockType.GRANITE

    val grid = OpacityGrid.of(chunk, factor = 4)

    val partial = grid.opacityAt(0, 0, 0)
    assertTrue(partial > 0.0 && partial < 0.1, "one voxel in sixty-four should be a small fraction, was $partial")
    assertEquals(0.0, grid.opacityAt(1, 1, 1), 1e-9)
  }

  @Test
  fun `a solid cell blocks sight and an empty one does not`() {
    val chunk = flatGround()
    val grid = OpacityGrid.of(chunk, factor = 4)

    assertTrue(grid.blocksSight(0, 0, 0), "solid ground should block sight")
    assertFalse(grid.blocksSight(0, 0, 2), "open air should not")
    // Off the grid reads as transparent, so a ray simply leaves the chunk rather than stopping at its edge.
    assertFalse(grid.blocksSight(-1, 0, 0))
    assertEquals(0.0, grid.opacityAt(99, 99, 99), 1e-9)
  }

  @Test
  fun `water is transparent to sight`() {
    val chunk = VoxelChunk(pos, size, height)
    for (y in 0 until size) {
      for (x in 0 until size) {
        for (z in 0..3) chunk[x, y, z] = BlockType.WATER
      }
    }

    val grid = OpacityGrid.of(chunk, factor = 4)
    assertEquals(0.0, grid.opacityAt(0, 0, 0), 1e-9)
  }

  // --- Walkable tiles --------------------------------------------------------------------------------

  @Test
  fun `flat ground is walkable everywhere with one span per column`() {
    val tile = WalkableTile.of(flatGround())

    assertEquals(size * size, tile.walkableColumns)
    assertEquals(size * size, tile.spanCount)
    assertEquals(3, tile.floorAt(0, 0, 0))
    assertTrue(tile.isWalkable(4, 4, 3))
    assertFalse(tile.isWalkable(4, 4, 2), "inside the rock is not walkable")
  }

  @Test
  fun `a building floor is a second span in the same column`() {
    val chunk = flatGround()
    // A storey: a floor at z 7 with headroom above it, over the ground at z 3.
    for (y in 2..5) {
      for (x in 2..5) chunk[x, y, 7] = BlockType.MASONRY
    }

    val tile = WalkableTile.of(chunk)

    // Surfaces are the *tops* of the floor voxels: standing on a full voxel 3 puts your feet at 4.0.
    val surfaces = tile.surfacesAt(3, 3)
    assertEquals(listOf(4.0, 8.0), surfaces.toList(), "both the ground and the storey should be standable")
  }

  @Test
  fun `a gap too tight for the agent is not walkable`() {
    val chunk = flatGround()
    // A ceiling one block above the ground leaves a single voxel of headroom, which a two-voxel agent cannot
    // use. The floor below it therefore has no span at all.
    for (z in 5..8) chunk[1, 1, z] = BlockType.MASONRY

    val tile = WalkableTile.of(chunk, AgentProfile(height = 2))

    assertFalse(tile.isWalkable(1, 1, 3), "one voxel of headroom is not enough for a two-voxel agent")
    // The top of the obstruction is standable, because there is open air above it.
    assertTrue(tile.isWalkable(1, 1, 8))
  }

  @Test
  fun `shallow water is waded and deep water is not`() {
    val chunk = flatGround()
    for (z in 4..4) chunk[6, 6, z] = BlockType.WATER
    for (z in 4..6) chunk[7, 7, z] = BlockType.WATER

    val tile = WalkableTile.of(chunk, AgentProfile(height = 2, maxWadeDepth = 1.0))

    assertTrue(tile.isWalkable(6, 6, 3), "one voxel of water should be wadeable")
    assertFalse(tile.isWalkable(7, 7, 3), "three voxels of water should not be")
  }

  @Test
  fun `lava is never a floor and never headroom`() {
    val chunk = flatGround()
    for (z in 4..5) chunk[4, 4, z] = BlockType.LAVA

    // The wading limit is set generously on purpose: it must not help. Lava is BLOCKED rather than WADEABLE, so
    // there is no depth of it an agent may stand in, and a profile that wades a metre of water still cannot.
    val tile = WalkableTile.of(chunk, AgentProfile(height = 2, maxWadeDepth = 4.0))

    assertFalse(tile.isWalkable(4, 4, 3), "the ground under lava is not standable")
    assertFalse(tile.isWalkable(4, 4, 5), "the surface of a lava pool is not standable either")
    assertEquals(0, tile.spanCountAt(4, 4), "a flooded column has no span at all")

    // The rim is untouched, so an agent walks up to the edge and stops rather than the whole area going dark.
    assertTrue(tile.isWalkable(3, 4, 3), "the column beside the pool is unaffected")
  }

  @Test
  fun `lava is not water`() {
    // ColumnSummary reports waterHeight for swimming and drowning checks. Lava is deliberately absent from it:
    // telling a swim check that a lava pool is water it can be in is worse than telling it nothing at all.
    val chunk = flatGround()
    for (z in 4..5) chunk[4, 4, z] = BlockType.LAVA

    val summary = ColumnSummary.of(chunk)

    assertEquals(0.0, summary.waterDepthAt(4, 4), 1e-9, "lava must not read as water depth")
    assertFalse(summary.isShelteredAt(4, 4, 1.0), "a lava pool is not a roof")
  }

  /*
   * `a canopy overhead is still free headroom` was here, and it is gone because its subject is.
   *
   * It wrote `LEAVES` above the ground and asserted the column stayed walkable, which exercised
   * `Passability.OPEN` on a real material and guarded a fall-through: `hasClearance` once had arms for AIR,
   * WATER and solid, and leaves - the only other case at the time - became free headroom by accident.
   *
   * Trees are entities now and the leaf blocks are deleted, so **`OPEN` has exactly one member left, `AIR`**.
   * No material can be written overhead to make the assertion mean anything: `WATER` is `WADEABLE` and `LAVA`
   * is `BLOCKED`, both by explicit declaration.
   *
   * The guarantee moved to `VoxelTest.every non-solid material declares what it does to an agent`, which pins
   * the passability of each non-solid material by name. That is the stronger home for it: what this test could
   * really only catch was a *default* being wrong, and an assertion that names every value cannot be satisfied
   * by a default at all.
   */

  @Test
  fun `a step within reach connects and a cliff does not`() {
    val chunk = flatGround()
    // A one-voxel step up at one column, and a four-voxel wall at another.
    for (z in 0..4) chunk[4, 0, z] = BlockType.GRANITE
    for (z in 0..7) chunk[5, 0, z] = BlockType.GRANITE

    val tile = WalkableTile.of(chunk, AgentProfile(height = 2, maxStep = 1.0))

    assertEquals(5.0, tile.stepTarget(4, 0, fromSurface = 4.0), 1e-9, "a one-voxel step should connect")
    assertEquals(-1.0, tile.stepTarget(5, 0, fromSurface = 4.0), 1e-9, "a four-voxel wall should not")
  }

  // --- Occupancy -------------------------------------------------------------------------------------

  @Test
  fun `a partly filled surface voxel gives a fractional column height`() {
    val chunk = flatGround()
    chunk.set(1, 1, 4, BlockType.DIRT, Occupancy.of(0.25))

    val summary = ColumnSummary.of(chunk)

    // The voxel index is unchanged - it is still voxel 4 - but the height is a quarter of the way up it.
    assertEquals(4, summary.surfaceAt(1, 1))
    assertEquals(4.25, summary.surfaceHeightAt(1, 1), 0.01)
    // A full voxel's top is its upper face: voxel 3 completely full reads 4.0, not 3.0.
    assertEquals(4.0, summary.surfaceHeightAt(0, 0), 1e-9)
  }

  @Test
  fun `a gentle slope is walkable where integer steps would have walled it off`() {
    // The payoff, and the reason occupancy had to reach the derived structures rather than stopping at the wire
    // format. A slope rising a fifth of a voxel per column is a ramp. Rounded to voxel indices it is either
    // dead flat or a sequence of one-voxel cliffs, and with a strict step limit the second reads as a wall - so
    // an agent would refuse to walk up a gradient of one in five.
    val chunk = flatGround()
    for (x in 0 until size) {
      chunk.set(x, 0, 4, BlockType.DIRT, Occupancy.of(0.1 + 0.1 * x))
    }
    // A ledge a full voxel above the ramp's foot, to check the step limit still refuses something.
    for (z in 4..5) chunk[0, 1, z] = BlockType.GRANITE

    val tile = WalkableTile.of(chunk, AgentProfile(height = 2, maxStep = 0.25))

    var previous = tile.surfaceAt(0, 0, 0)
    for (x in 1 until size) {
      val here = tile.stepTarget(x, 0, previous)
      assertTrue(here > 0.0, "column $x should be reachable from ${"%.2f".format(previous)}")
      previous = here
    }

    // And the limit still bites.
    assertEquals(-1.0, tile.stepTarget(0, 1, tile.surfaceAt(0, 0, 0)), 1e-9, "a full voxel is still a wall")
  }

  @Test
  fun `opacity is weighted by how full a voxel is`() {
    val half = VoxelChunk(pos, size, height)
    val full = VoxelChunk(pos, size, height)
    for (z in 0..3) {
      for (y in 0 until size) {
        for (x in 0 until size) {
          half.set(x, y, z, BlockType.GRANITE, Occupancy.of(0.5))
          full[x, y, z] = BlockType.GRANITE
        }
      }
    }

    val halfOpacity = OpacityGrid.of(half, factor = 4).opacityAt(0, 0, 0)
    val fullOpacity = OpacityGrid.of(full, factor = 4).opacityAt(0, 0, 0)

    // A cell half full of stone occludes half as much. Rounding occupancy back to solid-or-empty here would put
    // the resolution cliff straight back after paying a byte per voxel to remove it.
    assertEquals(fullOpacity * 0.5, halfOpacity, 0.01)
  }

  // --- Deltas ----------------------------------------------------------------------------------------

  /** A removal of everything in one voxel, packed as the delta stores it. */
  private fun gone(x: Int, y: Int, z: Int) = ChunkDelta.pack(indexOf(x, y, z), Occupancy.EMPTY)

  private fun indexOf(x: Int, y: Int, z: Int) = (y * size + x) * height + z

  private fun deltaOf(vararg removals: Int) = ChunkDelta(pos, size, height).apply {
    carveAll(removals.sorted().toIntArray())
  }

  /**
   * A partial removal keeps the rock and reduces how much of it is left.
   *
   * The reason a removal carries an occupancy rather than being a bare index: a brush is a sphere, so the voxels
   * around its edge are partly taken. Rounding those to solid-or-empty would put the resolution cliff straight
   * back after paying a byte per voxel to remove it.
   */
  @Test
  fun `a partial removal keeps the block and merges the fraction`() {
    val base = flatGround()
    val delta = deltaOf(ChunkDelta.pack(indexOf(2, 2, 2), Occupancy.of(0.4)))

    val merged = delta.mergedOnto(base)

    assertEquals(BlockType.GRANITE, merged[2, 2, 2], "partly carved rock is still rock")
    assertEquals(0.4, merged.fillAt(2, 2, 2), 0.01)
    // And the merged chunk still satisfies the invariant every derived structure relies on.
    merged.validate()
  }

  @Test
  fun `a delta overlays the base without changing it`() {
    val base = flatGround()
    val delta = deltaOf(gone(1, 1, 3), gone(1, 1, 2))

    val merged = delta.mergedOnto(base)

    assertEquals(BlockType.AIR, merged[1, 1, 3])
    assertEquals(BlockType.AIR, merged[1, 1, 2])
    // The base is untouched: it is shared, cached, and regenerable, so mutating it would corrupt every other
    // reader of the same chunk.
    assertEquals(BlockType.GRASS, base[1, 1, 3])
    assertEquals(BlockType.GRANITE, base[1, 1, 2])
  }

  /**
   * Working the same voxel over several swings costs one entry, and the lowest offer wins.
   *
   * Removal is monotone, so a batch that offers more material than is already left is the no-op it looks like.
   * `ChunkStore` refuses an increase outright when it has a base to compare against; here, where two removals
   * meet inside one delta, keeping the smaller is the same rule expressed as a merge.
   */
  @Test
  fun `repeatedly carving the same voxel costs one entry and keeps the lowest`() {
    val delta = ChunkDelta(pos, size, height)

    delta.carveAll(intArrayOf(ChunkDelta.pack(indexOf(2, 2, 2), 200)))
    delta.carveAll(intArrayOf(ChunkDelta.pack(indexOf(2, 2, 2), 60)))
    delta.carveAll(intArrayOf(ChunkDelta.pack(indexOf(2, 2, 2), 180)))

    assertEquals(1, delta.removalCount)
    assertEquals(60, delta.remainingAt(2, 2, 2))
  }

  /** A batch that changes nothing reports nothing, so a caller knows not to announce it. */
  @Test
  fun `a batch that lowers nothing is not a change`() {
    val delta = deltaOf(ChunkDelta.pack(indexOf(2, 2, 2), 60))

    assertEquals(0, delta.carveAll(intArrayOf(ChunkDelta.pack(indexOf(2, 2, 2), 200))))
    assertEquals(1, delta.removalCount)
  }

  /**
   * Removals come back in index order however they went in.
   *
   * The wire codec delta-codes against the previous index and persistence will do the same, so the order is a
   * contract rather than an implementation detail - and it is what makes the merge in [ChunkDelta.carveAll] a
   * single pass.
   */
  @Test
  fun `removals are held in index order`() {
    val delta = ChunkDelta(pos, size, height)

    delta.carveAll(intArrayOf(gone(4, 4, 5)))
    delta.carveAll(intArrayOf(gone(1, 2, 6)))
    delta.carveAll(intArrayOf(gone(1, 2, 5)))

    val indices = delta.packedRemovals().map { ChunkDelta.indexOf(it) }

    assertEquals(indices.sorted(), indices, "the delta must hand its removals back sorted")
    assertEquals(3, indices.size)
  }

  @Test
  fun `a delta reports which columns it touched`() {
    val delta = deltaOf(gone(1, 2, 5), gone(1, 2, 6), gone(4, 4, 5))

    // Two columns, three removals: a derived structure rebuilds per column, not per removal.
    assertEquals(2, delta.touchedColumns().size)
  }

  /**
   * The size test is what fires, and the coverage threshold is a backstop behind it.
   *
   * A delta stops being cheaper than the chunk it modifies well before it covers thirty percent of it, so if the
   * coverage test were the trigger a chunk would store many times its own size as a delta first.
   * `StorageBudgetTest` asserts the ordering on real terrain; this asserts that both tests exist and that an
   * empty delta trips neither.
   */
  @Test
  fun `a delta past the coverage threshold asks to be baked`() {
    val base = flatGround()

    assertFalse(ChunkDelta(pos, size, height).shouldBake(RleCodec.encode(base).size), "an empty delta")

    val target = (size * size * height * (ChunkDelta.BAKE_COVERAGE + 0.02)).toInt()
    val removals = ArrayList<Int>()
    outer@ for (y in 0 until size) {
      for (x in 0 until size) {
        for (z in 0 until height) {
          removals.add(gone(x, y, z))
          if (removals.size >= target) break@outer
        }
      }
    }

    val delta = ChunkDelta(pos, size, height).apply { carveAll(removals.sorted().toIntArray()) }

    assertTrue(delta.coverage >= ChunkDelta.BAKE_COVERAGE)
    // Deliberately given an absurdly generous reference size, so it is the coverage arm being tested.
    assertTrue(delta.shouldBake(Int.MAX_VALUE))
  }

  @Test
  fun `a baked chunk decodes back to the merged result`() {
    val base = flatGround()
    val delta = deltaOf(gone(3, 3, 2))

    val decoded = RleCodec.decode(pos, ChunkDelta.bake(base, delta))

    assertEquals(BlockType.AIR, decoded[3, 3, 2])
    assertEquals(BlockType.GRASS, decoded[0, 0, 3])
  }

  // --- What removal-only guarantees, and what it does not --------------------------------------------

  /**
   * Carving can only ever lower the ground and open sight lines. Nothing can raise either.
   *
   * The one bug class removal-only is supposed to make impossible is a mutation that *adds* material, and these
   * are the assertions that would catch one: a brush with a sign error, a merge that took the wrong side of a
   * `min`, an occupancy written rather than reduced. All three would show up here as a derived value moving the
   * wrong way, on any carve, rather than as a strange-looking hillside months later.
   *
   * Note what is deliberately **not** asserted - see [carvingCreatesShelterSoThatIsNotMonotone] and
   * [WalkableTile]. Monotonicity holds for two of the six derived quantities, and writing a test for the other
   * four would pin behaviour that is genuinely allowed to go both ways.
   */
  @Test
  fun `carving never raises the surface or the opacity`() {
    val base = flatGround()

    // A rough gallery: a couple of columns taken out entirely, and their neighbours partly.
    val delta = deltaOf(
      gone(2, 2, 2), gone(2, 2, 3), gone(3, 2, 2), gone(3, 2, 3),
      ChunkDelta.pack(indexOf(4, 2, 3), 64),
      ChunkDelta.pack(indexOf(2, 3, 3), 128)
    )
    val carved = delta.mergedOnto(base)

    val summaryBefore = ColumnSummary.of(base)
    val summaryAfter = ColumnSummary.of(carved)

    for (y in 0 until size) {
      for (x in 0 until size) {
        assertTrue(
          summaryAfter.surfaceHeightAt(x, y) <= summaryBefore.surfaceHeightAt(x, y),
          "column ($x,$y) rose from ${summaryBefore.surfaceHeightAt(x, y)} to " +
              "${summaryAfter.surfaceHeightAt(x, y)}; carving cannot add ground"
        )
      }
    }

    val opacityBefore = OpacityGrid.of(base)
    val opacityAfter = OpacityGrid.of(carved)

    for (z in 0 until opacityBefore.height) {
      for (y in 0 until opacityBefore.depth) {
        for (x in 0 until opacityBefore.width) {
          assertTrue(
            opacityAfter.opacityAt(x, y, z) <= opacityBefore.opacityAt(x, y, z),
            "cell ($x,$y,$z) got more opaque; carving cannot block a sight line"
          )
        }
      }
    }
  }

  /**
   * Shelter is *created* by carving, so it is not monotone - and a mine is the obvious case.
   *
   * Worth an explicit test rather than a comment, because "removal only" invites the assumption that every
   * derived quantity can only fall. Two of `ColumnSummary`'s cannot: dig into a hillside and a column that had
   * no roof over it now has one, so `shelteredFloorHeight` goes from -1.0 to a real height and
   * `voidCeilingHeight` with it. `WalkableTile` is the third non-monotone one, for the opposite reason - remove
   * the floor and a column that was walkable stops being so.
   */
  @Test
  fun carvingCreatesShelterSoThatIsNotMonotone() {
    val base = flatGround()

    // Ground runs 0..3. Take the middle out of one column and leave the top on: a gallery under a roof.
    val carved = deltaOf(gone(2, 2, 1), gone(2, 2, 2)).mergedOnto(base)

    assertFalse(ColumnSummary.of(base).hasShelter(2, 2), "flat ground shelters nothing")
    assertTrue(
      ColumnSummary.of(carved).hasShelter(2, 2),
      "a gallery cut under intact ground is exactly what shelter means"
    )
  }

  // --- The store -------------------------------------------------------------------------------------

  @Test
  fun `the store serves stale structures until the rebuild budget is spent`() {
    // The whole design of this class. A forty millisecond hitch on the zone thread every time somebody swings a
    // pick is unacceptable; an NPC walking over a pit that opened two hundred milliseconds ago is not. So
    // invalidation queues, and queries keep answering from the stale structure meanwhile.
    var chunk = flatGround()
    var builds = 0
    val store = DerivedStore(voxels = {
      builds++
      chunk
    })

    assertEquals(3, store.summaryOf(pos).surfaceAt(0, 0))
    assertEquals(1, builds)
    assertEquals(0, store.pendingRebuilds)

    // Somebody digs a pit. The surface can only ever fall, which is the one thing removal-only guarantees.
    val dug = chunk.copy()
    for (z in 2..3) dug[0, 0, z] = BlockType.AIR
    chunk = dug
    store.invalidate(pos)

    assertTrue(store.isStale(pos))
    assertEquals(1, store.pendingRebuilds)
    assertEquals(3, store.summaryOf(pos).surfaceAt(0, 0), "the stale answer is still served")

    assertEquals(1, store.rebuild(budget = 4))
    assertFalse(store.isStale(pos))
    assertEquals(1, store.summaryOf(pos).surfaceAt(0, 0))
  }

  @Test
  fun `tracking a chunk queues a first build and pays for it out of the budget`() {
    // The entry point the store went without, and the reason every walkability query in zone-server answered
    // "unknown" for the life of the process: everything else builds on demand, and the callers that matter all
    // guard on `isTracked` first, so nothing ever put a chunk in. Residency has to be pushed in from outside -
    // and out of the same budget as a rebuild, because a login subscribes a whole view volume at once.
    var builds = 0
    val store = DerivedStore(voxels = {
      builds++
      flatGround()
    })

    store.track(pos)

    assertFalse(store.isTracked(pos), "tracking asks for a build, it does not perform one")
    assertTrue(store.isStale(pos), "an unbuilt chunk has no answer a caller can trust yet")
    assertEquals(0, builds)
    assertEquals(1, store.pendingRebuilds)

    assertEquals(1, store.rebuild(budget = 1))
    assertTrue(store.isTracked(pos))
    assertFalse(store.isStale(pos))
    assertEquals(1, builds)
    assertEquals(3, store.summaryOf(pos).surfaceAt(0, 0), "the build is a real one, not a placeholder")

    // Idempotent: a chunk somebody is already holding must not be queued for a pointless rebuild when a second
    // player walks into it, which at a view volume apiece would be most of what the budget ever did.
    store.track(pos)
    assertEquals(0, store.pendingRebuilds)
    assertEquals(1, builds)
  }

  @Test
  fun `forgetting a tracked chunk drops it, and it can be tracked again`() {
    val store = DerivedStore(voxels = { flatGround() })

    store.track(pos)
    store.rebuild(budget = 1)
    assertEquals(1, store.trackedChunks)

    // The last subscriber leaving. Nothing is holding the chunk, so nothing needs its structures.
    store.forget(pos)
    assertFalse(store.isTracked(pos))
    assertEquals(0, store.trackedChunks)
    assertEquals(0, store.pendingRebuilds)

    store.track(pos)
    assertEquals(1, store.pendingRebuilds, "a forgotten chunk is tracked again when somebody comes back")
  }

  @Test
  fun `a zero budget rebuilds nothing and keeps the work queued`() {
    val store = DerivedStore(voxels = { flatGround() })
    store.summaryOf(pos)
    store.invalidate(pos)

    assertEquals(0, store.rebuild(budget = 0))
    assertEquals(1, store.pendingRebuilds)
  }

  @Test
  fun `invalidating one chunk does not invalidate its neighbour`() {
    // The consequence of not storing walkability links: connectivity across a border is resolved at query time
    // from two tiles, so a carve in one chunk never touches the other. That keeps the blast radius of one swing
    // to exactly one tile - which matters more now that a brush is a sphere and can straddle a border.
    val east = ChunkPos(1, 0, 0)
    val store = DerivedStore(voxels = { at -> flatGround().let { VoxelChunk(at, size, height, it.blocks, it.occupancy) } })

    store.walkableOf(pos)
    store.walkableOf(east)
    store.invalidate(pos)

    assertEquals(setOf(pos), store.staleChunks())
    assertFalse(store.isStale(east))
  }

  @Test
  fun `a step across a chunk border is resolved from both tiles`() {
    val east = ChunkPos(1, 0, 0)
    val store = DerivedStore(voxels = { at -> flatGround().let { VoxelChunk(at, size, height, it.blocks, it.occupancy) } })

    assertTrue(
      store.canStep(pos, size - 1, 0, 4.0, east, 0, 0),
      "flat ground either side of a border should connect"
    )
  }
}
