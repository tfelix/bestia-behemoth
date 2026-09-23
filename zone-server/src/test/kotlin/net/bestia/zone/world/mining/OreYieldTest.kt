package net.bestia.zone.world.mining

import io.mockk.every
import io.mockk.mockk
import net.bestia.worldgen.resource.GradeMix
import net.bestia.worldgen.resource.OreGrade
import net.bestia.worldgen.voxel.BlockType
import net.bestia.zone.ecs.item.ItemTemplateRegistry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The grade yields are the join between the voxels the generator places and the coin the economy is
 * quoted in, so the average matters more than any single grade does.
 */
class OreYieldTest {

  private val items = mockk<ItemTemplateRegistry>().also {
    every { it.idOf("gold_ore") } returns GOLD_ORE
    every { it.idOf("iron_ore") } returns IRON_ORE
    every { it.idOf(neq("gold_ore")) } returns null
  }

  private val yields = OreYield(items)

  @Test
  fun `a voxel averages exactly two lumps, which is exactly one bar`() {
    // The money supply is quoted per ore voxel, and the chain voxel -> ore -> bar -> coin has to
    // reproduce it exactly. At 5:3:1 the yields 1, 2 and 7 come to 18 over 9. Ten for a rich voxel -
    // which its 5 kg would suggest - gives 21 over 9, and the mint then owes a third of a coin a voxel.
    val mix = GradeMix()
    val weighted = mix.smallWeight * OreYield.amountFor(OreGrade.SMALL) +
      mix.mediumWeight * OreYield.amountFor(OreGrade.MEDIUM) +
      mix.richWeight * OreYield.amountFor(OreGrade.RICH)
    val total = mix.smallWeight + mix.mediumWeight + mix.richWeight

    assertEquals(2.0, weighted / total, 1e-12, "a voxel no longer averages a whole bar of ore")
  }

  @Test
  fun `a richer voxel is worth more without being worth its mass`() {
    assertEquals(1, OreYield.amountFor(OreGrade.SMALL))
    assertEquals(2, OreYield.amountFor(OreGrade.MEDIUM))
    assertEquals(7, OreYield.amountFor(OreGrade.RICH))
  }

  @Test
  fun `gold blocks come out as gold ore, at their grade`() {
    assertEquals(GOLD_ORE, yields.of(BlockType.ORE_GOLD_SMALL)?.itemId)
    assertEquals(1, yields.of(BlockType.ORE_GOLD_SMALL)?.amount)
    assertEquals(7, yields.of(BlockType.ORE_GOLD_RICH)?.amount)
  }

  @Test
  fun `rock yields nothing rather than an empty stack`() {
    assertNull(yields.of(BlockType.STONE), "plain rock handed something over")
  }

  @Test
  fun `an ore with no item yet stays in the ground`() {
    // Copper has blocks in the palette and no item in items.yml. Inventing a placeholder would put an
    // item in players' packs that nothing can consume.
    assertNull(yields.of(BlockType.ORE_COPPER_RICH), "copper was handed over without an item to be")
  }

  private companion object {
    const val GOLD_ORE = 31L
    const val IRON_ORE = 8L
  }
}
