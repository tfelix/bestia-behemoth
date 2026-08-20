package net.bestia.zone.world.stream

import net.bestia.worldgen.core.ChunkPos
import net.bestia.worldgen.pipeline.StandardWorld
import net.bestia.worldgen.voxel.VoxelChunk
import net.bestia.zone.geometry.Vec3L
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ChunkCoordsTest {

  private val config = StandardWorld.demoConfig()

  @Test
  fun `a position maps to the chunk containing it`() {
    assertEquals(ChunkPos(0, 0, 0), ChunkCoords.chunkOf(config, Vec3L(0, 0, 0)))
    assertEquals(ChunkPos(0, 0, 0), ChunkCoords.chunkOf(config, Vec3L(31, 31, 255)))
    assertEquals(ChunkPos(1, 1, 1), ChunkCoords.chunkOf(config, Vec3L(32, 32, 256)))
  }

  @Test
  fun `negative positions floor rather than truncate`() {
    // Truncation towards zero would put -1 and 0 in the same chunk and leave chunk -1 one voxel short. Every
    // coordinate west or south of the origin, and every voxel below sea level, depends on this.
    assertEquals(ChunkPos(-1, -1, -1), ChunkCoords.chunkOf(config, Vec3L(-1, -1, -1)))
    assertEquals(ChunkPos(-1, -1, -1), ChunkCoords.chunkOf(config, Vec3L(-32, -32, -256)))
    assertEquals(ChunkPos(-2, -2, -2), ChunkCoords.chunkOf(config, Vec3L(-33, -33, -257)))
  }

  @Test
  fun `localising gives coordinates inside the chunk it names`() {
    val localised = assertNotNull(ChunkCoords.localise(config, -33, 70, -257))

    assertEquals(ChunkPos(-2, 2, -2), localised.chunk)
    assertEquals(31, localised.localX, "-33 is the last column of chunk -2, not the first of chunk -1")
    assertEquals(6, localised.localY)
    assertEquals(255, localised.localZ)
  }

  @Test
  fun `the voxel index matches the chunk layout it is written against`() {
    // The patch format carries this index and the client decodes with it, so a divergence from VoxelChunk's
    // own arithmetic would put every edit in the wrong place - and only in chunks where the mistake happens
    // to matter, which is the worst way for it to show up.
    val chunk = VoxelChunk(ChunkPos(0, 0, 0), config.chunkSize, config.chunkHeight)

    for ((x, y, z) in listOf(Triple(0, 0, 0), Triple(31, 31, 255), Triple(5, 17, 200))) {
      assertEquals(
        chunk.index(x, y, z),
        ChunkCoords.voxelIndex(config, x, y, z),
        "index disagreed at ($x,$y,$z)"
      )
    }
  }

  @Test
  fun `standing height is never more than half a voxel from the ground`() {
    // The bug this replaces: floor(h) + 1, which put a player between zero and a whole voxel *above* the
    // surface and never below it, so on a continuous heightfield they always floated. What matters here is not
    // any single value but that the error is bounded by half a voxel and takes both signs.
    val half = config.voxelSize / 2.0

    for (thousandths in 0..4000) {
      val ground = 400.0 + thousandths / 1000.0
      val z = ChunkCoords.standingZ(config, ground)
      val error = z * config.voxelSize - ground

      assertTrue(
        kotlin.math.abs(error) <= half + 1e-9,
        "ground $ground gave z $z, which is $error m out"
      )
    }

    // Both directions, so this is a rounding rather than a shifted floor.
    assertEquals(409, ChunkCoords.standingZ(config, 409.3), "rounds down, sinking 0.3 m")
    assertEquals(410, ChunkCoords.standingZ(config, 409.8), "rounds up, floating 0.2 m")
  }

  @Test
  fun `a seabed is answered with the waterline rather than the bottom`() {
    // Otherwise "take me to the sea" drowns the player on the floor of the ocean margin, several hundred
    // metres down, which is where every edge of the world is.
    assertEquals(0, ChunkCoords.standingZ(config, -412.7))
    assertEquals(0, ChunkCoords.standingZ(config, config.seaLevel))
  }

  @Test
  fun `the sea surface slabs include the one the water is actually in`() {
    // The bug: chunkZOf(seaLevel) is 0, but water fills up to sea level so its topmost voxel is -1, which is in
    // chunk -1. Subscribing to slab 0 alone offers the client a chunk of pure air over every ocean - twelve bytes
    // that mesh to nothing - and open water renders as an empty screen.
    assertEquals(0, config.chunkZOf(config.seaLevel), "the premise this exists to correct")

    val slabs = ChunkCoords.seaSurfaceSlabs(config)

    assertTrue(-1 in slabs, "the slab holding the topmost water voxel; without it the sea is invisible")
    assertTrue(0 in slabs, "and the slab holding the air above it, or the mesher has no sign change to find")
  }

  @Test
  fun `a sea level away from a chunk boundary needs only one slab`() {
    // The two-slab case is an artefact of sea level sitting exactly on a boundary. Anywhere else the interface is
    // interior to one slab and there is nothing to widen.
    val raised = config.copy(seaLevel = 100.0)

    assertEquals(listOf(0), ChunkCoords.seaSurfaceSlabs(raised))
  }

  @Test
  fun `the waterline pair survives a clip that would otherwise split it`() {
    // The regression test for a sea that vanishes when you climb above it. Open ocean is the pair {-1, 0}, and
    // a player one slab up clips to [0, 2] - which keeps the air and drops the water it is drawn against, so
    // the client meshed nothing at all and the sea disappeared from a cliff top.
    val sea = intArrayOf(-1, 0)

    assertEquals(listOf(-1, 0), ChunkCoords.offeredSlabs(sea, anchorZ = 0, vertical = 1).toList())
    assertEquals(listOf(-1, 0, 1), ChunkCoords.offeredSlabs(sea, anchorZ = 1, vertical = 1).toList())

    // Two slabs up the sea is dropped outright rather than half-offered, which is right: it is over 512 m below
    // a camera that draws about 200. Half-offered is the only wrong answer.
    assertEquals(listOf(2), ChunkCoords.offeredSlabs(sea, anchorZ = 2, vertical = 1).toList())
  }

  @Test
  fun `only a floor partner escapes the clip, and only by one slab`() {
    // A slab pulled in as somebody's floor is a source of voxels, not a thing that draws, so it needs no floor
    // of its own. Without that bound the rule would walk a deep column down slab by slab - the 726-chunk
    // regression by another route.
    val deep = intArrayOf(-4, -3, -2, -1, 0)

    // -1 and 0 clear the clip, -2 comes in as -1's floor, and -3 does not come in as -2's: the rule reads the
    // kept set once and does not chase what it adds.
    assertEquals(listOf(-2, -1, 0), ChunkCoords.offeredSlabs(deep, anchorZ = 0, vertical = 1).toList())
  }

  @Test
  fun `the anchor slab is offered even where the surface rule says nothing`() {
    // Standing on a built platform, or in a cave, or at an elevation the heightfield knows nothing about.
    assertEquals(listOf(7), ChunkCoords.offeredSlabs(intArrayOf(), anchorZ = 7, vertical = 1).toList())
  }

  @Test
  fun `a chunk address survives the proto round trip including negatives`() {
    val chunk = ChunkPos(-123_456, 987_654, -3)

    assertEquals(chunk, ChunkCoords.fromProto(ChunkCoords.toProto(chunk)))
  }
}
