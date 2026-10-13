package net.bestia.zone.spawn

import net.bestia.zone.battle.status.CombatValuesPool
import io.mockk.every
import io.mockk.mockk
import net.bestia.zone.ai.ecs.AiAgentFactory
import net.bestia.zone.ai.profile.AiProfileRegistry
import net.bestia.zone.skill.ecs.KnownSkills
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.navigation.profile.MovementProfileRegistry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import net.bestia.zone.bestia.persistence.Bestia
import net.bestia.zone.bestia.BestiaCatalogue
import net.bestia.zone.bestia.persistence.BestiaRepository
import net.bestia.zone.bestia.persistence.BestiaSkillRepository
import net.bestia.zone.bestia.persistence.LearnedSkill

/** A wild bestia can only cast the attack skills its AI profile lists if it knows them. */
class BestiaEntitySpawnerLearnsetTest {

  private val bestiaRepository = mockk<BestiaRepository>()
  private val bestiaSkillRepository = mockk<BestiaSkillRepository>()

  /** Real, so the level cut is the catalogue's own rather than whatever a stub was told to return. */
  private val catalogue = BestiaCatalogue(bestiaRepository, bestiaSkillRepository)

  private val sut = BestiaEntitySpawner(
    bestiaCatalogue = catalogue,
    aiProfileRegistry = mockk<AiProfileRegistry>(relaxed = true),
    aiAgentFactory = mockk<AiAgentFactory>(relaxed = true),
    movementProfileRegistry = MovementProfileRegistry().apply { load() },
    statusEffectService = mockk(relaxed = true),
    combatValuesPool = CombatValuesPool()
  )

  init {
    every { bestiaRepository.findAll() } returns listOf(
      Bestia(id = 1L, identifier = "blob", level = 3, experienceReward = 5, health = 10, mana = 8)
    )
  }

  @Test
  fun `a mob knows the skills its species learns up to its level`() {
    every { bestiaSkillRepository.findAllLearned() } returns listOf(LearnedSkill(1, EMBER, 3), LearnedSkill(1, LATER, 4))

    val world = testWorld()

    val id = sut.spawnMob(world, bestiaId = 1L, pos = Vec3L(0, 0, 0))
    val known = world.get(id, KnownSkills::class)

    assertEquals(1, known?.levelOf(EMBER))
    assertEquals(0, known?.levelOf(LATER), "learned at Lv 4, one above the species")
  }

  @Test
  fun `a mob without a learnset gets no KnownSkills at all`() {
    every { bestiaSkillRepository.findAllLearned() } returns emptyList()
    val world = testWorld()

    val id = sut.spawnMob(world, bestiaId = 1L, pos = Vec3L(0, 0, 0))

    assertNull(world.get(id, KnownSkills::class))
  }

  private companion object {
    const val EMBER = 1000L
    const val LATER = 1001L
  }
}
