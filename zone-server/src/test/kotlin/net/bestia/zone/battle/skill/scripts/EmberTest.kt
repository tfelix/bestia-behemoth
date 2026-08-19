package net.bestia.zone.battle.skill.scripts

import net.bestia.zone.battle.BattleContextFixture
import net.bestia.zone.battle.LineOfSightService
import net.bestia.zone.battle.skill.RecordingSkillWorld
import net.bestia.zone.battle.skill.SkillContextFixture
import net.bestia.zone.ecs.battle.effects.AreaEffect
import net.bestia.zone.geometry.Vec3L
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EmberTest {

  private val sut = Ember(LineOfSightService())

  @Test
  fun `a ground cast leaves a patch that ticks eight times`() {
    val patch = burn()

    assertEquals(1.2f, patch.tickIntervalSeconds)
    assertEquals(8, patch.remainingTicks, "9.6s at 1.2s a tick is eight ticks")
  }

  @Test
  fun `the patch lands on the aimed-at point`() {
    val aimedAt = Vec3L(3, 0, 0)
    val world = RecordingSkillWorld()

    sut.execute(SkillContextFixture.skillCtx(groundCtx(targetPosition = aimedAt), world))

    assertEquals(aimedAt, world.spawnedAreaEffects.single().centre)
  }

  @Test
  fun `the burnt area is the one the catalogue declares rather than one the script picked`() {
    assertEquals(1L, burn(aoeRadius = 1.0).radiusTiles)
    assertEquals(3L, burn(aoeRadius = 3.0).radiusTiles)
  }

  @Test
  fun `the flames do not care whose side anyone is on`() {
    assertTrue(burn().hitsCaster, "Ember burns its own caster if they stand in it")
  }

  @Test
  fun `damage per tick scales with intelligence`() {
    val dull = burn(intelligence = 10).damagePerTick
    val smart = burn(intelligence = 100).damagePerTick

    assertTrue(smart > dull, "Ember should scale with INT ($dull -> $smart)")
  }

  @Test
  fun `every tick burns for at least one`() {
    val perTick = burn(level = 1, intelligence = 1).damagePerTick

    assertTrue(perTick >= 1, "expected at least 1 per tick but was $perTick")
  }

  @Test
  fun `the patch is the whole effect, so there is no number to float over anything`() {
    assertNull(sut.execute(SkillContextFixture.skillCtx(groundCtx())))
  }

  @Test
  fun `aimed at an entity it does nothing at all`() {
    val world = RecordingSkillWorld()
    val ctx = SkillContextFixture.skillCtx(
      BattleContextFixture.entityCtx(attack = BattleContextFixture.attack(aoeRadius = 1.0)),
      world
    )

    assertFalse(sut.isCastPossible(ctx), "Ember is a ground skill and must refuse an entity target")
    assertTrue(world.spawnedAreaEffects.isEmpty())
  }

  @Test
  fun `a point beyond the skill range is refused`() {
    assertTrue(sut.isCastPossible(SkillContextFixture.skillCtx(groundCtx(targetPosition = Vec3L(3, 0, 0)))))
    assertFalse(sut.isCastPossible(SkillContextFixture.skillCtx(groundCtx(targetPosition = Vec3L(500, 0, 0)))))
  }

  /** Runs the script and returns the one patch it spawned. */
  private fun burn(
    aoeRadius: Double = 1.0,
    level: Int = 10,
    intelligence: Int = 10
  ): AreaEffect {
    val world = RecordingSkillWorld()

    sut.execute(
      SkillContextFixture.skillCtx(
        groundCtx(aoeRadius = aoeRadius, level = level, intelligence = intelligence),
        world
      )
    )

    return world.spawnedAreaEffects.single().effect
  }

  private fun groundCtx(
    aoeRadius: Double = 1.0,
    level: Int = 10,
    intelligence: Int = 10,
    targetPosition: Vec3L = Vec3L(3, 0, 0)
  ) = BattleContextFixture.groundCtx(
    attack = BattleContextFixture.attack(aoeRadius = aoeRadius),
    attackerEntity = BattleContextFixture.battleEntity(level = level, intelligence = intelligence),
    targetPosition = targetPosition
  )
}
