package net.bestia.zone.casting.scripts.master

import net.bestia.zone.battle.BattleContextFixture
import net.bestia.zone.battle.LineOfSightService
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.casting.RecordingSkillWorld
import net.bestia.zone.casting.SkillContextFixture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class WizardWardsTest {

  private val world = RecordingSkillWorld()
  private val target = BattleContextFixture.DEFENDER_ID

  @Test
  fun `Safety Wall holds one tile for allies, with more hits and time per level`() {
    SafetyWall(LineOfSightService()).execute(groundCtx(level = 3))

    val spawned = world.spawnedAreaEffects.single()
    val wall = spawned.effect
    assertEquals(6L, spawned.visualId)
    assertEquals(0L, wall.radiusTiles)
    assertEquals(0, wall.damagePerTick)
    // 52 s at a tick every half second.
    assertEquals(104, wall.remainingTicks)
    assertEquals(StatusEffectId.SAFETY_WALL, wall.mechanics.zoneStatus)
    assertEquals(true, wall.mechanics.forAllies)
    assertEquals(10, wall.hitsLeft)
  }

  @Test
  fun `Stone Curse rolls a fifth per level for Stoning that turns to Stone for fifteen seconds per level`() {
    StoneCurse(LineOfSightService()).execute(ctx(level = 2))

    val rolled = world.inflicted.single()
    assertEquals(target, rolled.targetEntityId)
    assertEquals("STONING", rolled.status)
    assertEquals(40, rolled.chancePercent)
    assertEquals(30, rolled.amount)
  }

  @Test
  fun `Suppress Magic breaks the cast and silences on one winning roll`() {
    SuppressMagic(LineOfSightService()).execute(ctx(level = 3))

    val rolled = world.inflicted.single()
    assertEquals("SILENCED" to 60, rolled.status to rolled.chancePercent)
    assertEquals(3, rolled.level)
    assertEquals(listOf(target), world.interrupted)
  }

  private fun ctx(level: Int) = SkillContextFixture.skillCtx(
    BattleContextFixture.entityCtx(attack = BattleContextFixture.attack(level = level)),
    world,
    skillLevel = level,
  )

  private fun groundCtx(level: Int) = SkillContextFixture.skillCtx(
    BattleContextFixture.groundCtx(BattleContextFixture.attack(level = level)),
    world,
    skillLevel = level,
  )
}
