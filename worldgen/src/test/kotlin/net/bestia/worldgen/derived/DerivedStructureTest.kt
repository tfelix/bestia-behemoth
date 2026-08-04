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

  @Test
  fun `a canopy overhead is still free headroom`() {
    // The regression guard for the fall-through this replaced. `hasClearance` used to have arms for AIR,
    // WATER and solid, and anything else - leaves being the only case at the time - fell through every arm
    // and became free headroom by accident. It is now OPEN by declaration, and this is what says so.
    //
    // It is also the one test that catches a wrong Passability default: were the default BLOCKED, an agent
    // could no longer walk under a tree anywhere in the world, and nothing else here would notice.
    val chunk = flatGround()
    for (z in 4..6) chunk[3, 3, z] = BlockType.LEAVES

    val tile = WalkableTile.of(chunk, AgentProfile(height = 2, maxWadeDepth = 0.0))

    assertTrue(tile.isWalkable(3, 3, 3), "a leaf canopy should not obstruct the ground under it")
  }

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

  @Test
  fun `a delta carries occupancy and merges it`() {
    val base = flatGround()
    val delta = ChunkDelta(pos, size, height)

    delta.set(2, 2, 4, BlockType.DIRT, Occupancy.of(0.4))
    val merged = delta.mergedOnto(base)

    assertEquals(BlockType.DIRT, merged[2, 2, 4])
    assertEquals(0.4, merged.fillAt(2, 2, 4), 0.01)
    // And the merged chunk still satisfies the invariant every derived structure relies on.
    merged.validate()
  }

  // --- Deltas ----------------------------------------------------------------------------------------

  @Test
  fun `a delta overlays the base without changing it`() {
    val base = flatGround()
    val delta = ChunkDelta(pos, size, height)

    delta.set(1, 1, 4, BlockType.MASONRY)
    delta.set(1, 1, 3, BlockType.AIR)

    val merged = delta.mergedOnto(base)

    assertEquals(BlockType.MASONRY, merged[1, 1, 4])
    assertEquals(BlockType.AIR, merged[1, 1, 3])
    // The base is untouched: it is shared, cached, and regenerable, so mutating it would corrupt every other
    // reader of the same chunk.
    assertEquals(BlockType.GRASS, base[1, 1, 3])
    assertEquals(BlockType.AIR, base[1, 1, 4])
  }

  @Test
  fun `repeatedly editing the same voxel costs one entry`() {
    val delta = ChunkDelta(pos, size, height)

    repeat(50) { delta.set(2, 2, 5, BlockType.MASONRY) }
    delta.set(2, 2, 5, BlockType.AIR)

    assertEquals(1, delta.editCount)
    assertEquals(BlockType.AIR, delta.get(2, 2, 5))
  }

  @Test
  fun `a delta reports which columns it touched`() {
    val delta = ChunkDelta(pos, size, height)
    delta.set(1, 2, 5, BlockType.MASONRY)
    delta.set(1, 2, 6, BlockType.MASONRY)
    delta.set(4, 4, 5, BlockType.MASONRY)

    // Two columns, three edits: a derived structure rebuilds per column, not per edit.
    assertEquals(2, delta.touchedColumns().size)
  }

  @Test
  fun `a delta past the coverage threshold asks to be baked`() {
    // Compaction is mandatory rather than an optimisation: a player who terraforms a hillside over months
    // accumulates a delta larger than the chunk it modifies.
    val base = flatGround()
    val delta = ChunkDelta(pos, size, height)

    val encoded = RleCodec.encode(base).size
    assertFalse(delta.shouldBake(encoded), "an empty delta should not be baked")

    var placed = 0
    val target = (delta.volume * (ChunkDelta.BAKE_COVERAGE + 0.02)).toInt()
    outer@ for (y in 0 until size) {
      for (x in 0 until size) {
        for (z in 0 until height) {
          delta.set(x, y, z, BlockType.MASONRY)
          if (++placed >= target) break@outer
        }
      }
    }

    assertTrue(delta.coverage >= ChunkDelta.BAKE_COVERAGE)
    assertTrue(delta.shouldBake(RleCodec.encode(delta.mergedOnto(base)).size))
  }

  @Test
  fun `a baked chunk decodes back to the merged result`() {
    val base = flatGround()
    val delta = ChunkDelta(pos, size, height)
    delta.set(3, 3, 8, BlockType.MASONRY)

    val decoded = RleCodec.decode(pos, ChunkDelta.bake(base, delta))

    assertEquals(BlockType.MASONRY, decoded[3, 3, 8])
    assertEquals(BlockType.GRASS, decoded[0, 0, 3])
  }

  // --- The store -------------------------------------------------------------------------------------

  @Test
  fun `the store serves stale structures until the rebuild budget is spent`() {
    // The whole design of this class. A forty millisecond hitch on the zone thread every time somebody places
    // a fence is unacceptable; an NPC walking through a doorway that closed two hundred milliseconds ago is
    // not. So invalidation queues, and queries keep answering from the stale structure meanwhile.
    var chunk = flatGround()
    var builds = 0
    val store = DerivedStore(voxels = {
      builds++
      chunk
    })

    assertEquals(3, store.summaryOf(pos).surfaceAt(0, 0))
    assertEquals(1, builds)
    assertEquals(0, store.pendingRebuilds)

    // Somebody builds a tower.
    val taller = chunk.copy()
    for (z in 4..9) taller[0, 0, z] = BlockType.MASONRY
    chunk = taller
    store.invalidate(pos)

    assertTrue(store.isStale(pos))
    assertEquals(1, store.pendingRebuilds)
    assertEquals(3, store.summaryOf(pos).surfaceAt(0, 0), "the stale answer is still served")

    assertEquals(1, store.rebuild(budget = 4))
    assertFalse(store.isStale(pos))
    assertEquals(9, store.summaryOf(pos).surfaceAt(0, 0))
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
    // from two tiles, so an edit in one chunk never touches the other. That keeps the blast radius of placing
    // one block to exactly one tile.
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
