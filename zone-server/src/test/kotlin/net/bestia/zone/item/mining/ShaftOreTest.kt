package net.bestia.zone.item.mining

import io.mockk.every
import io.mockk.mockk
import net.bestia.worldgen.core.ChunkPos
import net.bestia.worldgen.core.WorldConfig
import net.bestia.worldgen.voxel.BlockType
import net.bestia.worldgen.voxel.VoxelChunk
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.world.stream.ChunkService
import net.bestia.zone.world.stream.ChunkStreamInbox
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ShaftOreTest {

  private val chunk = VoxelChunk(ChunkPos(0, 0, 0), 32, 256)

  private val chunks = mockk<ChunkService> {
    every { config } returns WorldConfig(seed = 1)
    every { normalise(any()) } answers { firstArg() }
    every { merged(any()) } returns chunk
  }

  private val carves = mutableListOf<ChunkStreamInbox.Carve>()
  private val inbox = mockk<ChunkStreamInbox> {
    every { offerCarve(capture(carves)) } returns Unit
  }

  private val oreYield = mockk<OreYield> {
    every { of(any()) } answers { if (firstArg<BlockType>() == GOLD) OreYield.Stack(GOLD_ORE_ID, 2) else null }
  }

  private val shaftOre = ShaftOre(chunks, inbox, oreYield)

  private val shaftAt = Vec3L(10, 10, 100)
  private val levelOne = ShaftOre.reachAt(1)!!

  private fun place(block: BlockType, at: Vec3L) {
    chunk[at.x.toInt(), at.y.toInt(), at.z.toInt()] = block
  }

  private fun dig() = shaftOre.dig(SHAFT_ID, shaftAt, levelOne, OWNER_ACCOUNT, DIGGER)

  @Test
  fun `digs the topmost ore first and pays the digger`() {
    place(GOLD, Vec3L(12, 10, 60))
    place(GOLD, Vec3L(10, 10, 90))

    assertEquals(1, dig())
    assertEquals(1, dig())
    assertEquals(0, dig())

    assertEquals(listOf(90.5, 60.5), carves.map { it.brush.fromZ })
    assertTrue(carves.all { it.yieldTo == DIGGER && it.accountId == OWNER_ACCOUNT })
  }

  @Test
  fun `counts every ore voxel one carve takes out`() {
    place(GOLD, Vec3L(10, 10, 90))
    place(GOLD, Vec3L(10, 10, 89))
    place(GOLD, Vec3L(10, 11, 90))

    assertEquals(3, dig())
    assertEquals(0, dig())
  }

  @Test
  fun `leaves ore out of reach and right under its own frame`() {
    place(GOLD, Vec3L(10, 10, 98))
    place(GOLD, Vec3L(10, 10, 59))
    place(GOLD, Vec3L(30, 10, 90))

    assertFalse(shaftOre.hasOre(SHAFT_ID, shaftAt, levelOne))
  }

  @Test
  fun `a higher Mining level reaches deeper`() {
    place(GOLD, Vec3L(10, 10, 59))

    assertTrue(shaftOre.hasOre(SHAFT_ID, shaftAt, ShaftOre.reachAt(2)!!))
  }

  @Test
  fun `skips ore that has no item to come out as`() {
    place(BlockType.ORE_COPPER_MEDIUM, Vec3L(10, 10, 90))

    assertFalse(shaftOre.hasOre(SHAFT_ID, shaftAt, levelOne))
  }

  @Test
  fun `skips ore somebody else dug out since it looked`() {
    place(GOLD, Vec3L(10, 10, 90))
    assertTrue(shaftOre.hasOre(SHAFT_ID, shaftAt, levelOne))

    place(BlockType.AIR, Vec3L(10, 10, 90))

    assertEquals(0, dig())
  }

  @Test
  fun `a master without Mining reaches nothing`() {
    assertNull(ShaftOre.reachAt(0))
  }

  private companion object {
    val GOLD = BlockType.ORE_GOLD_MEDIUM
    const val GOLD_ORE_ID = 5L
    const val SHAFT_ID = 1L
    const val OWNER_ACCOUNT = 3L
    const val DIGGER = 42L
  }
}
