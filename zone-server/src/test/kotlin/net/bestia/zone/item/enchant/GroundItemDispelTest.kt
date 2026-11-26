package net.bestia.zone.item.enchant

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.item.ecs.ItemEnchantments
import net.bestia.zone.item.keepsake.KeepsakeWardEnchantment
import net.bestia.zone.postal.seal.PostalSeal
import net.bestia.zone.util.EntityId
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.Random
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GroundItemDispelTest {

  private val seal = ActiveItemEnchantment(ItemEnchantmentId.POSTAL_SEAL, strength = PostalSeal.STRENGTH)
  private val ward = ActiveItemEnchantment(ItemEnchantmentId.KEEPSAKE_WARD, strength = KeepsakeWardEnchantment.STRENGTH)
  private val script = mockk<ItemEnchantmentScript>(relaxed = true)
  private val catalog = ItemEnchantmentCatalog().apply {
    load(
      listOf(
        ItemEnchantmentDefinition(1, "POSTAL_SEAL", "PostalSeal", dispellable = true),
        ItemEnchantmentDefinition(2, "KEEPSAKE_WARD", "KeepsakeWardEnchantment", dispellable = true, dispelThreshold = 60),
      )
    )
  }
  private val scripts = mockk<ItemEnchantmentScripts> { every { of(any()) } returns script }
  private val world = testWorld()

  /** items.md: Dispel Lv. 5 (100 %, P 80) against a Postal Seal (350) succeeds 23 % of the time. */
  @Test
  fun `a Lv 5 Dispel breaks a seal on a roll under 23 percent`() {
    val item = itemWith(seal)

    val broken = dispel(item, baseChancePercent = 100, potency = 80, roll = 0.228)

    assertEquals(listOf(seal), broken)
    assertNull(world.get(item, ItemEnchantments::class))
    verify { script.onDispelled(world, item, seal) }
  }

  @Test
  fun `the seal holds on a roll at that chance`() {
    val item = itemWith(seal)

    val broken = dispel(item, baseChancePercent = 100, potency = 80, roll = 0.229)

    assertEquals(emptyList(), broken)
    assertEquals(listOf(seal), world.get(item, ItemEnchantments::class)?.enchantments)
  }

  /** items.md: Dispel Lv. 2 (70 %, P 33) against the seal succeeds 7 % of the time. */
  @Test
  fun `a Lv 2 Dispel breaks a seal only on a roll under 7 percent`() {
    assertEquals(listOf(seal), dispel(itemWith(seal), baseChancePercent = 70, potency = 33, roll = 0.065))
    assertEquals(emptyList(), dispel(itemWith(seal), baseChancePercent = 70, potency = 33, roll = 0.067))
  }

  @Test
  fun `an enchantment no stronger than the cast breaks at the full base chance`() {
    val weak = seal.copy(strength = 50)

    assertEquals(listOf(weak), dispel(itemWith(weak), baseChancePercent = 70, potency = 80, roll = 0.69))
  }

  @Test
  fun `a cast below the ward's threshold cannot break it whatever the roll`() {
    assertEquals(emptyList(), dispel(itemWith(ward), baseChancePercent = 100, potency = 59, roll = 0.0))
  }

  @Test
  fun `an enchantment no dispel may break holds whatever the roll`() {
    catalog.load(listOf(ItemEnchantmentDefinition(1, "POSTAL_SEAL", "PostalSeal", dispellable = false)))

    assertEquals(emptyList(), dispel(itemWith(seal), baseChancePercent = 100, potency = 1_000, roll = 0.0))
  }

  private fun dispel(item: EntityId, baseChancePercent: Int, potency: Int, roll: Double): List<ActiveItemEnchantment> {
    val fixed = object : Random() {
      override fun nextDouble(): Double {
        return roll
      }
    }
    val dispel = GroundItemDispel(catalog, scripts, mockk(), mockk(relaxed = true), mockk(relaxed = true), fixed)

    return dispel.dispel(world, item, baseChancePercent, potency)
  }

  private fun itemWith(enchantment: ActiveItemEnchantment): EntityId {
    return world.createEntity { id -> add(id, ItemEnchantments(listOf(enchantment), Instant.EPOCH)) }
  }
}
