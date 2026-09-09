package net.bestia.zone.world

import net.bestia.zone.world.stream.ChunkStreamConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pins the numbers a client compiles in, because nothing else can.
 *
 * `bestia-client` is a separately released artefact built from a different toolchain, so there is no way to
 * compare the two declarations at compile time and no way to compare them at run time either - by the time a
 * client is running, disagreeing was already the bug. What is left is to make moving either number fail a
 * build, and to say in the failure which other file has to move with it. Same shape, and same reason, as
 * `worldgen`'s own `ChunkStoreTest` pinning the block palette hash.
 *
 * The second half is the more useful one: a default-configured server has to satisfy its own contract, or
 * every developer's first boot on this branch refuses.
 */
class ClientWorldContractTest {

  private val editTheClient =
    "then mirror it into bestia-client/src/Game/World/WorldLayout.cs in the same commit"

  @Test
  fun `the contract is the geometry a default server boots with`() {
    val config = WorldGenConfig().toWorldConfig(seed = 1)

    assertTrue(
      ClientWorldContract.disagreementsWith(config, ChunkStreamConfig().maxWalkSlopeDegrees).isEmpty(),
      "A server with no configuration at all must satisfy the client contract, or nobody can boot this " +
          "branch. Change the default back, or move ClientWorldContract to match it and $editTheClient."
    )
  }

  @Test
  fun `the contract values are pinned`() {
    assertEquals(32, ClientWorldContract.CHUNK_SIZE, "Update this pin, $editTheClient")
    assertEquals(256, ClientWorldContract.CHUNK_HEIGHT, "Update this pin, $editTheClient")
    assertEquals(1.0, ClientWorldContract.VOXEL_SIZE_METRES, "Update this pin, $editTheClient")
    assertEquals(1000.0, ClientWorldContract.CELL_SIZE_METRES, "Update this pin, $editTheClient")
    assertTrue(ClientWorldContract.WRAP_X, "Update this pin, $editTheClient")
    assertTrue(ClientWorldContract.WRAP_Y, "Update this pin, $editTheClient")
    assertEquals(45.0, ClientWorldContract.MAX_WALK_SLOPE_DEGREES, "Update this pin, $editTheClient")
  }

  @Test
  fun `a disagreement names the field and both values`() {
    val narrower = WorldGenConfig(chunkSize = 16).toWorldConfig(seed = 1)

    val found = ClientWorldContract.disagreementsWith(narrower, ChunkStreamConfig().maxWalkSlopeDegrees)

    assertEquals(listOf("chunk-size is 16, clients are built for 32"), found)
  }

  @Test
  fun `every disagreement is reported, not just the first`() {
    val other = WorldGenConfig(chunkSize = 16, chunkHeight = 128, wrapY = false).toWorldConfig(seed = 1)

    val found = ClientWorldContract.disagreementsWith(other, maxWalkSlopeDegrees = 30.0)

    assertEquals(4, found.size, "Reported: $found")
  }
}
