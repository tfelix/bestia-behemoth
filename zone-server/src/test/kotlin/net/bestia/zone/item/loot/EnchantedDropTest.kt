package net.bestia.zone.item.loot

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.bestia.zone.config.WorldRulesConfig
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.item.ecs.ItemEnchantments
import net.bestia.zone.item.ecs.ItemEnchantmentsComponentSMSG
import net.bestia.zone.item.enchant.ActiveItemEnchantment
import net.bestia.zone.item.enchant.ItemEnchantmentId
import net.bestia.zone.item.enchant.ItemEnchantmentScript
import net.bestia.zone.item.enchant.ItemEnchantmentScripts
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EnchantedDropTest {

  private val now = Instant.parse("2026-11-06T12:00:00Z")
  private val seal = ActiveItemEnchantment(ItemEnchantmentId.POSTAL_SEAL, strength = 70, endsAt = now.plusSeconds(30))

  private val script = mockk<ItemEnchantmentScript>(relaxed = true)
  private val scripts = mockk<ItemEnchantmentScripts> { every { of(ItemEnchantmentId.POSTAL_SEAL) } returns script }
  private val spawner = LootItemEntitySpawner(WorldRulesConfig(tickRate = 20), scripts, Clock.fixed(now, ZoneOffset.UTC))
  private val world = testWorld()

  @Test
  fun `an enchanted item carries its enchantments onto the ground and tells their scripts`() {
    val dropped = spawner.spawnLootItem(world, itemId = 1L, amount = 1, pos = Vec3L(0, 0, 0), uniqueId = 9L, enchantments = listOf(seal))

    assertEquals(ItemEnchantments(listOf(seal), since = now), world.get(dropped, ItemEnchantments::class))
    verify { script.onDropped(world, dropped, seal) }
  }

  @Test
  fun `an item loaded again keeps its time on the ground and is not dropped twice`() {
    val since = now.minusSeconds(10)

    val loaded = spawner.spawnLootItem(
      world, itemId = 1L, amount = 1, pos = Vec3L(0, 0, 0), uniqueId = 9L, entityId = 77L,
      enchantments = listOf(seal), enchantedSince = since,
    )

    assertEquals(since, world.get(loaded, ItemEnchantments::class)?.since)
    verify(exactly = 0) { script.onDropped(any(), any(), any()) }
  }

  @Test
  fun `a plain item has no enchantments`() {
    val dropped = spawner.spawnLootItem(world, itemId = 1L, amount = 1, pos = Vec3L(0, 0, 0))

    assertNull(world.get(dropped, ItemEnchantments::class))
  }

  @Test
  fun `the client gets the time left and the time on the ground`() {
    val ground = ItemEnchantments(listOf(seal), since = now.minusSeconds(10))

    val message = ItemEnchantmentsComponentSMSG.of(5L, ground, now, removed = false)

    assertEquals(listOf(ItemEnchantmentsComponentSMSG.Entry(ItemEnchantmentId.POSTAL_SEAL.id, 30f, 40f)), message.enchantments)
  }
}
