package net.bestia.zone.world.sight

import net.bestia.worldgen.core.ChunkPos
import net.bestia.worldgen.derived.Edges
import net.bestia.worldgen.derived.SightMask
import net.bestia.worldgen.derived.StructureCells
import net.bestia.worldgen.voxel.BlockType
import net.bestia.worldgen.voxel.Occupancy
import net.bestia.worldgen.voxel.VoxelChunk
import org.junit.jupiter.api.Test
import java.util.Random
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SightRayTest {

  /** Flat stone floor filling z 0..9, so an entity stands at z = 10. */
  private fun floorChunk(pos: ChunkPos): VoxelChunk {
    val voxels = VoxelChunk(pos, SIZE, HEIGHT)
    for (x in 0 until SIZE) for (y in 0 until SIZE) for (z in 0 until FLOOR) voxels[x, y, z] = BlockType.STONE

    return voxels
  }

  private fun masksOf(vararg chunks: Pair<ChunkPos, SightMask>): SightMasks {
    val byPos = chunks.toMap()
    return SightMasks { x, y, z -> byPos[ChunkPos(x, y, z)] }
  }

  private fun clear(masks: SightMasks, ax: Long, ay: Long, bx: Long, by: Long, az: Long = FLOOR.toLong(), bz: Long = az) =
    SightRay.isClear(ax, ay, az, bx, by, bz, SIZE, HEIGHT, masks)

  @Test
  fun `open ground is clear`() {
    val masks = masksOf(ORIGIN to SightMask.of(floorChunk(ORIGIN)))

    assertTrue(clear(masks, 2, 2, 14, 9))
  }

  @Test
  fun `a stone wall between blocks`() {
    val voxels = floorChunk(ORIGIN)
    for (y in 0 until SIZE) for (z in FLOOR until FLOOR + 3) voxels[8, y, z] = BlockType.STONE

    val masks = masksOf(ORIGIN to SightMask.of(voxels))

    assertFalse(clear(masks, 2, 5, 14, 5))
  }

  @Test
  fun `a knee-high wall does not block`() {
    val voxels = floorChunk(ORIGIN)
    for (y in 0 until SIZE) voxels[8, y, FLOOR] = BlockType.STONE

    val masks = masksOf(ORIGIN to SightMask.of(voxels))

    assertTrue(clear(masks, 2, 5, 14, 5))
  }

  @Test
  fun `a partly filled surface voxel does not blind a viewer standing in it`() {
    val voxels = floorChunk(ORIGIN)
    for (x in 0 until SIZE) for (y in 0 until SIZE) voxels.set(x, y, FLOOR, BlockType.STONE, Occupancy.FULL / 3)

    val masks = masksOf(ORIGIN to SightMask.of(voxels))

    assertTrue(clear(masks, 2, 5, 14, 5))
  }

  @Test
  fun `a hill between blocks across a chunk border`() {
    val east = ChunkPos(1, 0, 0)
    val hill = floorChunk(east)
    for (y in 0 until SIZE) for (z in FLOOR until FLOOR + 4) hill[1, y, z] = BlockType.STONE

    val masks = masksOf(ORIGIN to SightMask.of(floorChunk(ORIGIN)), east to SightMask.of(hill))

    assertFalse(clear(masks, 10, 5, SIZE + 6L, 5))
    assertTrue(clear(masks, 10, 5, SIZE.toLong(), 5))
  }

  @Test
  fun `a built wall on a crossed border blocks`() {
    val cells = StructureCells.Builder(SIZE)
    for (z in FLOOR until FLOOR + 3) {
      cells.add(7, 5, z, Occupancy.EMPTY, Edges.X_PLUS)
      cells.add(8, 5, z, Occupancy.EMPTY, Edges.X_MINUS)
    }
    val masks = masksOf(ORIGIN to SightMask.of(floorChunk(ORIGIN), cells.build()))

    assertFalse(clear(masks, 2, 5, 14, 5))
    // Along the wall rather than through it.
    assertTrue(clear(masks, 7, 1, 7, 12))
  }

  @Test
  fun `an unbuilt chunk counts as clear`() {
    assertTrue(clear(SightMasks { _, _, _ -> null }, 2, 5, 14, 5))
  }

  @Test
  fun `neighbours see each other without a walk`() {
    var asked = 0
    val masks = SightMasks { _, _, _ -> asked++; null }

    assertTrue(clear(masks, 5, 5, 6, 6))
    kotlin.test.assertEquals(0, asked)
  }

  /**
   * Every attack asks this, so a regression to allocation or a rebuild must show. The ceiling is generous on
   * purpose - a busy CI box is not a benchmark - and still two orders of magnitude under what a slow walk costs.
   */
  @Test
  fun `a hundred thousand twelve-tile checks stay cheap`() {
    val voxels = floorChunk(ORIGIN)
    val random = Random(7)
    repeat(200) { voxels[random.nextInt(SIZE), random.nextInt(SIZE), FLOOR + random.nextInt(3)] = BlockType.STONE }
    val mask = SightMask.of(voxels)
    val masks = SightMasks { x, y, z -> if (x == 0 && y == 0 && z == 0) mask else null }

    fun run(): Int {
      var visible = 0
      for (i in 0 until 100_000) {
        val ax = random.nextInt(SIZE).toLong()
        val ay = random.nextInt(SIZE).toLong()
        if (clear(masks, ax, ay, (ax + 12).coerceAtMost(SIZE - 1L), (ay + 5).coerceAtMost(SIZE - 1L))) visible++
      }
      return visible
    }

    run() // warm-up
    val started = System.nanoTime()
    val visible = run()
    val millis = (System.nanoTime() - started) / 1_000_000

    assertTrue(visible in 1 until 100_000, "some lines blocked and some clear, got $visible")
    assertTrue(millis < 500, "100 000 checks took $millis ms")
  }

  private companion object {
    const val SIZE = 32
    const val HEIGHT = 32
    const val FLOOR = 10
    val ORIGIN = ChunkPos(0, 0, 0)
  }
}
