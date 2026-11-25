package net.bestia.zone.spawn

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.bestia.zone.ai.ecs.AiAgentFactory
import net.bestia.zone.ai.profile.AiProfileRegistry
import net.bestia.zone.battle.StatusEffectService
import net.bestia.zone.battle.ecs.level.Level
import net.bestia.zone.battle.status.CombatValuesPool
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.bestia.BestiaCatalogue
import net.bestia.zone.bestia.persistence.Bestia
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.navigation.profile.MovementProfileRegistry
import net.bestia.zone.world.mana.ManaTier
import kotlin.test.Test
import kotlin.test.assertEquals

/** A den in thick mana breeds its bestia a few levels higher, and marks them for the glow. */
class BestiaEntitySpawnerManaTest {

  private val catalogue = mockk<BestiaCatalogue>()
  private val statusEffects = mockk<StatusEffectService>(relaxed = true)

  private val sut = BestiaEntitySpawner(
    bestiaCatalogue = catalogue,
    aiProfileRegistry = mockk<AiProfileRegistry>(relaxed = true),
    aiAgentFactory = mockk<AiAgentFactory>(relaxed = true),
    movementProfileRegistry = MovementProfileRegistry().apply { load() },
    statusEffectService = statusEffects,
    combatValuesPool = CombatValuesPool()
  )

  private val world = testWorld()

  init {
    every { catalogue.learnsetUpTo(any(), any()) } returns emptyList()
    every { catalogue.byId(1L) } returns Bestia(
      id = 1, identifier = "blob", levelMin = 3, levelMax = 3, experienceReward = 5, health = 10, mana = 8
    )
  }

  @Test
  fun `calm ground breeds bestia at their own level`() {
    val id = sut.spawnMob(world, bestiaId = 1L, pos = Vec3L(0, 0, 0))

    assertEquals(3, world.get(id, Level::class)?.level)
    verify(exactly = 0) { statusEffects.applyEffect(any(), any(), StatusEffectId.MANA_TOUCHED, any()) }
  }

  @Test
  fun `ominous ground breeds them higher and touched by mana`() {
    val id = sut.spawnMob(world, bestiaId = 1L, pos = Vec3L(0, 0, 0), manaTier = ManaTier.OMINOUS)

    assertEquals(3 + ManaTier.OMINOUS.denLevelBonus, world.get(id, Level::class)?.level)
    verify { statusEffects.applyEffect(any(), id, StatusEffectId.MANA_TOUCHED, 1) }
  }
}
