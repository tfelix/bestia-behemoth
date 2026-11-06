package net.bestia.zone.item.enchant

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.item.ecs.ItemEnchantments
import net.bestia.zone.util.EntityId
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.Random
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GroundItemDispelTest {

  private val seal = ActiveItemEnchantment(ItemEnchantmentId.POSTAL_SEAL, strength = 70)
  private val script = mockk<ItemEnchantmentScript>(relaxed = true)
  private val catalog = ItemEnchantmentCatalog().apply {
    load(listOf(ItemEnchantmentDefinition(1, "POSTAL_SEAL", "PostalSeal", dispellable = true)))
  }
  private val scripts = mockk<ItemEnchantmentScripts> { every { of(any()) } returns script }
  private val world = testWorld()

  @Test
  fun `the seal breaks on a roll under the base chance minus its strength`() {
    val item = sealedItem()

    val broken = dispel(item, roll = 29)

    assertEquals(listOf(seal), broken)
    assertNull(world.get(item, ItemEnchantments::class))
    verify { script.onDispelled(world, item, seal) }
  }

  @Test
  fun `the seal holds on a roll at that chance`() {
    val item = sealedItem()

    val broken = dispel(item, roll = 30)

    assertEquals(emptyList(), broken)
    assertEquals(listOf(seal), world.get(item, ItemEnchantments::class)?.enchantments)
  }

  @Test
  fun `an enchantment no dispel may break holds whatever the roll`() {
    catalog.load(listOf(ItemEnchantmentDefinition(1, "POSTAL_SEAL", "PostalSeal", dispellable = false)))
    val item = sealedItem()

    assertEquals(emptyList(), dispel(item, roll = 0))
  }

  /** Lv. 5 Dispel: base chance 100, so the seal of strength 70 breaks on 0..29. */
  private fun dispel(item: EntityId, roll: Int): List<ActiveItemEnchantment> {
    val fixed = object : Random() {
      override fun nextInt(bound: Int): Int {
        return roll
      }
    }
    val dispel = GroundItemDispel(catalog, scripts, mockk(), mockk(relaxed = true), mockk(relaxed = true), fixed)

    return dispel.dispel(world, item, baseChancePercent = 100)
  }

  private fun sealedItem(): EntityId {
    return world.createEntity { id -> add(id, ItemEnchantments(listOf(seal), Instant.EPOCH)) }
  }
}
