package net.bestia.zone.item.mining

import io.mockk.every
import io.mockk.mockk
import net.bestia.worldgen.resource.ResourceType
import net.bestia.worldgen.voxel.BlockType
import net.bestia.worldgen.voxel.OreBlocks
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.item.ecs.ItemTemplateRegistry
import net.bestia.zone.skill.skillIdsOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class OreCompanionsTest {

  /** Every companion and the sand, as items with the levels the catalogue gives them. */
  private val levels = mapOf(
    "sand" to 1, "palladium_ore" to 70, "mercury_ore" to 60, "adamantium_ore" to 100, "rough_sapphire" to 45,
    "rough_garnet" to 30, "rough_tigers_eye" to 37, "rough_onyx" to 65, "rough_opal" to 66, "rough_jade" to 33, "lodestone" to 45, "obsidian" to 50,
  )
  private val ids = levels.keys.withIndex().associate { (i, name) -> name to 1000L + i }

  private val items = mockk<ItemTemplateRegistry> {
    every { idOf(any()) } answers { ids[firstArg()] }
    every { baseLevelOf(any()) } answers { levels[ids.entries.first { it.value == firstArg<Long>() }.key] }
  }

  /** No Mining skill in the catalogue: an untrained miner, who may break ore up to level 10 and no higher. */
  private val sut = OreCompanions(items, skillIdsOf(emptyMap()))
  private val world = testWorld()
  private val miner = world.createEntity { }

  private fun extras(block: BlockType, count: Int): Map<String, Int> {
    val byId = ids.entries.associate { it.value to it.key }
    val found = HashMap<String, Int>()
    for (i in 0 until count) {
      sut.extrasFor(world, miner, block, i.toLong(), (i * 7L) % 311, (i * 13L) % 17).forEach { found.merge(byId.getValue(it.itemId), it.amount, Int::plus) }
    }

    return found
  }

  @Test
  fun `plain rock holds a few grains of sand`() {
    val sand = extras(BlockType.GRANITE, 100_000).getValue("sand")

    assertTrue(sand in 380..620, "about 0.5% of rock voxels, was $sand of 100000")
  }

  @Test
  fun `the same voxel always answers the same`() {
    val first = (0 until 500).map { sut.extrasFor(world, miner, BlockType.STONE, it.toLong(), 3, 4).size }
    val again = (0 until 500).map { sut.extrasFor(world, miner, BlockType.STONE, it.toLong(), 3, 4).size }

    assertEquals(first, again)
  }

  @Test
  fun `a miner who could not break a companion directly does not get it`() {
    // An untrained miner reaches ore of level 10. Palladium is 70, so a copper voxel gives them nothing extra.
    assertEquals(emptyMap(), extras(BlockType.ORE_COPPER_RICH, 20_000))
  }

  @Test
  fun `a low level companion rides along with its host`() {
    // Iron holds garnet (30) and tiger's eye (37), both above an untrained miner's 10: nothing at all.
    assertEquals(emptyMap(), extras(BlockType.ORE_IRON_RICH, 20_000))
  }

  @Test
  fun `gold and soil hold nothing extra`() {
    assertEquals(emptyMap(), extras(BlockType.ORE_GOLD_RICH, 20_000))
    assertEquals(emptyMap(), extras(BlockType.SAND, 20_000))
  }

  @Test
  fun `every companion names a real host ore, and no host is gold`() {
    OreCompanions.COMPANIONS.keys.forEach { host ->
      assertNotNull(OreBlocks.blocksFor(host), "$host has no blocks to be mined out of")
      assertTrue(host != ResourceType.GOLD_LODE && host != ResourceType.GOLD_PLACER, "gold sizes the coin supply")
    }
  }

  @Test
  fun `the table lists exactly what the catalogue promises`() {
    assertEquals(levels.keys, OreCompanions.identifiers())
  }
}
