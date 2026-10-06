package net.bestia.zone.item.mining

import io.mockk.every
import io.mockk.mockk
import net.bestia.worldgen.core.ChunkPos
import net.bestia.worldgen.voxel.BlockType
import net.bestia.worldgen.voxel.Occupancy
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.item.ecs.ItemTemplateRegistry
import net.bestia.zone.item.ecs.ObtainItemIntent.CreateItemIntent
import net.bestia.zone.world.stream.ChunkService.CarveResult
import net.bestia.zone.world.stream.ChunkService.CarvedVoxel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class OreHandOutTest {

  private val items = mockk<ItemTemplateRegistry>().also {
    every { it.idOf("gold_ore") } returns GOLD_ORE
    every { it.idOf("iron_ore") } returns IRON_ORE
  }

  private val sut = OreHandOut(OreYield(items))
  private val world = testWorld()
  private val miner = world.createEntity { }

  @Test
  fun `an emptied ore voxel is handed out as an item intent`() {
    sut.bank(carve(voxel(BlockType.ORE_GOLD_RICH, Occupancy.EMPTY)), miner)

    handOut()

    assertEquals(GOLD_ORE to 7, held())
  }

  @Test
  fun `a voxel with ore left in it pays nothing`() {
    sut.bank(carve(voxel(BlockType.ORE_GOLD_RICH, Occupancy.FULL / 2)), miner)

    handOut()

    assertNull(held())
  }

  @Test
  fun `a second stack waits until the first is picked up`() {
    val gold = voxel(BlockType.ORE_GOLD_SMALL, Occupancy.EMPTY)
    val iron = voxel(BlockType.ORE_IRON_SMALL, Occupancy.EMPTY)
    sut.bank(carve(gold, iron), miner)

    handOut()
    val first = held()
    handOut()
    val unchanged = held()
    world.modify(miner) { id -> remove(id, CreateItemIntent::class) }
    handOut()
    val second = held()

    assertEquals(first, unchanged)
    assertEquals(setOf(GOLD_ORE to 1, IRON_ORE to 1), setOf(first, second))
  }

  private fun handOut() {
    world.modify(miner) { sut.handOut(this) }
  }

  private fun held(): Pair<Long, Int>? {
    return world.get(miner, CreateItemIntent::class)?.let { it.itemId to it.amount }
  }

  private fun carve(vararg voxels: CarvedVoxel): CarveResult {
    return CarveResult(voxels.toList(), setOf(CHUNK))
  }

  private fun voxel(block: BlockType, remaining: Int): CarvedVoxel {
    return CarvedVoxel(CHUNK, 1, 2, 3, block, priorOccupancy = Occupancy.FULL, remainingOccupancy = remaining)
  }

  private companion object {
    const val GOLD_ORE = 31L
    const val IRON_ORE = 8L
    val CHUNK = ChunkPos(0, 0, 0)
  }
}
