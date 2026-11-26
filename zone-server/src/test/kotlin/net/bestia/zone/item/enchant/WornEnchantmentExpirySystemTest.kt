package net.bestia.zone.item.enchant

import net.bestia.zone.battle.Element
import net.bestia.zone.battle.ecs.status.IsStatusValueDirty
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.item.ecs.Equipment
import net.bestia.zone.item.ecs.Inventory
import net.bestia.zone.item.equip.EquipmentSlot
import net.bestia.zone.item.equip.EquipmentSlots
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class WornEnchantmentExpirySystemTest {

  private val now = Instant.parse("2026-11-26T12:00:00Z")
  private val world = testWorld()
  private val sut = WornEnchantmentExpirySystem(Clock.fixed(now, ZoneOffset.UTC))

  @Test
  fun `an ended endow leaves the worn weapon and its bag copy, and the stats are rebuilt`() {
    val wielder = wielding(endow(endsAt = now))

    sut.update(world, 1f)

    assertEquals(emptyList(), world.get(wielder, Equipment::class)!!.get(EquipmentSlot.RIGHT_HAND)!!.enchantments)
    assertEquals(emptyList(), world.get(wielder, Inventory::class)!!.byPlayerItemId(77L)!!.enchantments)
    assertNotNull(world.get(wielder, IsStatusValueDirty::class))
  }

  @Test
  fun `a running endow stays and the stats are left alone`() {
    val running = endow(endsAt = now.plusSeconds(1))
    val wielder = wielding(running)

    sut.update(world, 1f)

    assertEquals(listOf(running), world.get(wielder, Equipment::class)!!.get(EquipmentSlot.RIGHT_HAND)!!.enchantments)
    assertNull(world.get(wielder, IsStatusValueDirty::class))
  }

  private fun endow(endsAt: Instant): ActiveItemEnchantment {
    return ActiveItemEnchantment(
      ItemEnchantmentId.ENDOWED_WEAPON, strength = 60, value = Element.FIRE.ordinal, endsAt = endsAt, level = 1
    )
  }

  private fun wielding(endow: ActiveItemEnchantment): Long {
    val equipment = Equipment(availableSlotMask = EquipmentSlots.ALL)
    equipment.equip(
      EquipmentSlot.RIGHT_HAND, Equipment.EquippedItem(itemId = 1L, uniqueId = 77L, enchantments = listOf(endow))
    )
    val bag = Inventory(
      mutableListOf(
        Inventory.Item(
          itemId = 1L, amount = 1, uniqueId = 77L, stackable = false, equipped = true, enchantments = listOf(endow)
        )
      )
    )

    return world.createEntity { id ->
      add(id, equipment)
      add(id, bag)
    }
  }
}
