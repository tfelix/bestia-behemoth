package net.bestia.zone.casting.essence

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.bestia.zone.battle.damage.HitKind
import net.bestia.zone.battle.ecs.damage.LandedHits
import net.bestia.zone.battle.ecs.status.CombatStats
import net.bestia.zone.battle.status.CombatValues
import net.bestia.zone.battle.status.EssenceSkill
import net.bestia.zone.bestia.BestiaCatalogue
import net.bestia.zone.casting.SkillExecutionService
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.skill.SkillIds
import net.bestia.zone.util.EntityId
import org.junit.jupiter.api.Test
import java.util.Random

class ReflexSystemTest {

  private val world = testWorld()
  private val hits = ReflexHits()

  private val skills = mockk<SkillExecutionService>(relaxed = true)
  private val skillIds = SkillIds().apply { load(mapOf("acid_skin" to ACID_SKIN)) }

  private val bestias = mockk<BestiaCatalogue> {
    every { attackLevelOf(ACID_SKIN) } returns 51
  }

  /** Always rolls 7 of 100: a Ruby's 8% casts, an Emerald's 5% does not. */
  private val rolls = mockk<Random> { every { nextInt(100) } returns 7 }

  private val system = ReflexSystem(hits, skills, skillIds, bestias, rolls)

  @Test
  fun `a struck wearer may cast the buff on itself`() {
    val wearer = wearing(EssenceSkill("acid_skin", attackLevelCap = 60, chancePercent = 8))

    struck(wearer)
    system.update(world, 0.05f)
    world.runPostedUntil(System.nanoTime() + POSTED_NANOS)

    verify { skills.executeFree(world, wearer, ACID_SKIN, 1, wearer, null) }
  }

  @Test
  fun `a missed roll or an attack above the gem's cap casts nothing`() {
    val unlucky = wearing(EssenceSkill("acid_skin", attackLevelCap = 60, chancePercent = 5))
    val weakGem = wearing(EssenceSkill("acid_skin", attackLevelCap = 40, chancePercent = 50))

    struck(unlucky)
    struck(weakGem)
    system.update(world, 0.05f)
    world.runPostedUntil(System.nanoTime() + POSTED_NANOS)

    verify(exactly = 0) { skills.executeFree(any(), any(), any(), any(), any(), any()) }
  }

  private fun wearing(reflex: EssenceSkill): EntityId = world.createEntity { id ->
    add(id, CombatStats(CombatValues.OBJECT_BASELINE.copy(reflexes = listOf(reflex))))
  }

  private fun struck(id: EntityId) {
    hits.landed(
      LandedHits.LandedHit(id, sourceId = 99L, healthBefore = 100, healthAfter = 90, maxHealth = 100, HitKind.UNTYPED)
    )
  }

  private companion object {
    const val ACID_SKIN = 2122L
    const val POSTED_NANOS = 50_000_000L
  }
}
