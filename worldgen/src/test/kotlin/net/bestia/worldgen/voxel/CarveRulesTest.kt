package net.bestia.worldgen.voxel

import net.bestia.worldgen.core.ChunkPos
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * What the world itself refuses to let a player dig, as opposed to what a permission check refuses.
 *
 * Both rules here exist *because* there is no building system. A player who is refused cannot work around it by
 * placing something, so a rule that merely made a mess would make a permanent one - which is the whole argument
 * for refusing a fluid breach rather than letting one happen and calling it emergent.
 */
class CarveRulesTest {

  private val pos = ChunkPos(0, 0, 0)
  private val size = 8
  private val height = 16

  /** Solid rock throughout, so anything a test refuses is refused for the reason the test set up. */
  private fun rock(): VoxelChunk {
    val chunk = VoxelChunk(pos, size, height)
    for (y in 0 until size) {
      for (x in 0 until size) {
        for (z in 0 until height) chunk[x, y, z] = BlockType.GRANITE
      }
    }
    return chunk
  }

  private fun index(x: Int, y: Int, z: Int) = (y * size + x) * height + z

  @Test
  fun `ordinary rock can be carved`() {
    assertTrue(CarveRules.mayCarve(rock(), index(4, 4, 8)))
  }

  @Test
  fun `the fluids and air cannot be carved`() {
    val chunk = rock()
    chunk[4, 4, 8] = BlockType.WATER
    chunk[4, 4, 10] = BlockType.LAVA
    chunk[4, 4, 12] = BlockType.AIR

    assertFalse(CarveRules.mayCarve(chunk, index(4, 4, 8)), "water is not diggable")
    assertFalse(CarveRules.mayCarve(chunk, index(4, 4, 10)), "lava is not diggable")
    assertFalse(CarveRules.mayCarve(chunk, index(4, 4, 12)), "there is nothing in air to take")
  }

  /**
   * The wall between a gallery and a lake stays put, on every one of the six faces.
   *
   * A breach would not flood - there is no runtime fluid state to flood with - it would leave a dry void under
   * standing water, permanently, with no way for the player to seal it. Each face is checked separately because
   * the index arithmetic differs per axis and a stride mistake would leave one direction quietly diggable.
   */
  @Test
  fun `rock touching a fluid on any face cannot be carved`() {
    val faces = listOf(
      Triple(4, 4, 9) to "above",
      Triple(4, 4, 7) to "below",
      Triple(5, 4, 8) to "east",
      Triple(3, 4, 8) to "west",
      Triple(4, 5, 8) to "north",
      Triple(4, 3, 8) to "south"
    )

    for ((where, name) in faces) {
      val chunk = rock()
      val (x, y, z) = where
      chunk[x, y, z] = BlockType.WATER

      assertFalse(
        CarveRules.mayCarve(chunk, index(4, 4, 8)),
        "rock with water $name it should not be carvable"
      )
    }
  }

  /** A diagonal neighbour shares no face, so nothing could run through it even in a world that simulated flow. */
  @Test
  fun `rock touching a fluid only diagonally can still be carved`() {
    val chunk = rock()
    chunk[5, 5, 9] = BlockType.WATER

    assertTrue(CarveRules.mayCarve(chunk, index(4, 4, 8)))
  }

  /**
   * Ice is not a fluid for this purpose, and that is deliberate.
   *
   * It is solid, and it is the *surface* of water rather than water. A hole mined in an ice sheet is a hole in a
   * solid, not a breached reservoir - and treating it as one would make every frozen lake shore undiggable.
   */
  @Test
  fun `ice does not count as a fluid to be sealed away from`() {
    val chunk = rock()
    chunk[4, 4, 9] = BlockType.ICE

    assertTrue(CarveRules.mayCarve(chunk, index(4, 4, 8)))
  }

  /**
   * A voxel on the chunk boundary is judged on the faces this chunk can see, and is not refused for the rest.
   *
   * The limitation is accepted rather than overlooked - see `CarveRules.wouldBreachFluid`. Checking across the
   * border would mean holding six neighbouring chunks on the path of every carved voxel, and the cost of not
   * doing so is a wall left standing at a seam, not a hole in a lake. This pins that choice so it cannot change
   * by accident in either direction.
   */
  @Test
  fun `a voxel on the chunk edge is judged only on the faces inside the chunk`() {
    val chunk = rock()

    assertTrue(CarveRules.mayCarve(chunk, index(0, 0, 0)), "the low corner has three faces and no fluid")

    chunk[1, 0, 0] = BlockType.WATER
    assertFalse(CarveRules.mayCarve(chunk, index(0, 0, 0)), "the faces it does have are still checked")
  }
}
