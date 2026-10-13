package net.bestia.zone.spawn

import io.mockk.every
import io.mockk.mockk
import net.bestia.zone.ai.ecs.AiAgentFactory
import net.bestia.zone.ai.profile.AiProfileRegistry
import net.bestia.zone.bestia.BestiaCatalogue
import net.bestia.zone.bestia.persistence.Bestia
import net.bestia.zone.battle.ecs.status.BaseStatusValues
import net.bestia.zone.battle.ecs.status.CombatStats
import net.bestia.zone.battle.ecs.status.OwnerCombatStats
import net.bestia.zone.battle.ecs.status.StatusValues
import net.bestia.zone.battle.status.CombatValues
import net.bestia.zone.battle.status.CombatValuesPool
import net.bestia.zone.battle.status.StatusValues as Attributes
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.navigation.profile.MovementProfileRegistry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

/**
 * What a spawned creature fights with, and what it does not carry any more.
 */
class BestiaEntitySpawnerCombatStatsTest {

  private val catalogue = mockk<BestiaCatalogue>()
  private val pool = CombatValuesPool()

  private val blob = Bestia(
    id = 1, identifier = "blob", level = 7, experienceReward = 5, health = 10, mana = 8,
    strength = 12, vitality = 14, intelligence = 3, dexterity = 9, willpower = 6, agility = 11
  )

  private val sut = BestiaEntitySpawner(
    bestiaCatalogue = catalogue,
    aiProfileRegistry = mockk<AiProfileRegistry>(relaxed = true),
    aiAgentFactory = mockk<AiAgentFactory>(relaxed = true),
    movementProfileRegistry = MovementProfileRegistry().apply { load() },
    statusEffectService = mockk(relaxed = true),
    combatValuesPool = pool
  )

  init {
    every { catalogue.learnsetUpTo(any(), any()) } returns emptyList()
    every { catalogue.byId(1L) } returns blob
  }

  @Test
  fun `a creature fights with its species' attributes at its species' level`() {
    val world = testWorld()

    val id = sut.spawnMob(world, bestiaId = 1L, pos = Vec3L(1, 2, 3))

    val attributes = Attributes(
      strength = 12, vitality = 14, intelligence = 3, agility = 11, willpower = 6, dexterity = 9
    )
    assertEquals(CombatValues.ofBase(level = 7, base = attributes), world.get(id, CombatStats::class)!!.values)
  }

  @Test
  fun `a thousand creatures of one species share one block`() {
    val world = testWorld()

    val blocks = (1..1000).map {
      world.get(sut.spawnMob(world, bestiaId = 1L, pos = Vec3L(it.toLong(), 0, 0)), CombatStats::class)!!.values
    }

    blocks.forEach { assertSame(blocks.first(), it) }
    assertEquals(1, pool.size)
  }

  @Test
  fun `a creature carries none of the copies only an owner is sent`() {
    val world = testWorld()

    val id = sut.spawnMob(world, bestiaId = 1L, pos = Vec3L(1, 2, 3))

    assertNull(world.get(id, BaseStatusValues::class))
    assertNull(world.get(id, StatusValues::class))
    assertNull(world.get(id, OwnerCombatStats::class))
  }
}
