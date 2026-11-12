package net.bestia.zone.casting.scripts.master

import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.battle.BattleContextFixture
import net.bestia.zone.battle.Element
import net.bestia.zone.battle.Size
import net.bestia.zone.battle.ecs.status.Nature
import net.bestia.zone.battle.LineOfSightService
import net.bestia.zone.battle.ecs.effects.ActiveStatusEffect
import net.bestia.zone.battle.ecs.effects.StatusEffects
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.casting.RecordingSkillWorld
import net.bestia.zone.casting.SkillContextFixture
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.movement.ecs.Facing
import net.bestia.zone.movement.ecs.Position
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AssassinShadowSkillsTest {

  private val world = RecordingSkillWorld()
  private val caster = BattleContextFixture.ATTACKER_ID
  private val target = BattleContextFixture.DEFENDER_ID

  @Test
  fun `Hide and Cloak put their own status on the caster, and a recast toggles it off`() {
    Hide().execute(ctx(level = 4))
    Cloak().execute(ctx(level = 2))

    assertEquals(
      listOf(Triple(caster, StatusEffectId.HIDING.id, 4), Triple(caster, StatusEffectId.CLOAKING.id, 2)),
      world.appliedEffects.map { Triple(it.targetEntityId, it.effectId, it.level) }
    )
    assertEquals(StatusEffectId.HIDING, Hide().toggledStatus)
    assertEquals(StatusEffectId.CLOAKING, Cloak().toggledStatus)
  }

  @Test
  fun `Backstab strikes from hiding and from behind at 200 percent ATK and 50 more each level`() {
    val backstab = Backstab(LineOfSightService())
    world.effectsOn[caster] = mutableSetOf(StatusEffectId.CLOAKING.id)
    world.behind += target

    assertTrue(backstab.isCastPossible(ctx(level = 5)))
    backstab.execute(ctx(level = 5))

    assertEquals(listOf(target to 400), world.strikes)
    assertEquals(listOf(200, 250, 300, 350, 400), (1..5).map { Backstab.atkPercent(it) })
  }

  @Test
  fun `Backstab fizzles out of hiding or in front of the target`() {
    val backstab = Backstab(LineOfSightService())
    world.behind += target
    assertFalse(backstab.isCastPossible(ctx(level = 1)), "not hidden")

    world.effectsOn[caster] = mutableSetOf(StatusEffectId.HIDING.id)
    world.behind.clear()
    assertFalse(backstab.isCastPossible(ctx(level = 1)), "not behind")
  }

  @Test
  fun `Backstab is refused at the button out of hiding, and in front of the target`() {
    val ecs = testWorld()
    val foe = ecs.createEntity { id ->
      add(id, Position(0, 0, 0))
      add(id, Facing(dx = 0, dy = 1))
    }
    val assassin = ecs.createEntity { id -> add(id, Position(0, -1, 0)) }
    val backstab = Backstab(LineOfSightService())

    assertEquals(OpError.SKILL_NEEDS_HIDING, backstab.checkCastStart(ecs, assassin, 1, emptyList()))
    ecs.add(assassin, StatusEffects(mutableListOf(ActiveStatusEffect(StatusEffectId.HIDING.id, 1, 60f))))
    assertNull(backstab.checkCastStart(ecs, assassin, 1, emptyList()))

    assertNull(backstab.checkTarget(ecs, assassin, foe, 1, emptyList()))
    ecs.getOrThrow(assassin, Position::class).y = 1
    assertEquals(OpError.SKILL_NEEDS_BEHIND, backstab.checkTarget(ecs, assassin, foe, 1, emptyList()))
  }

  @Test
  fun `Shadow Step blinks the hidden caster behind its target and keeps it hidden`() {
    val shadowStep = ShadowStep(LineOfSightService())
    world.effectsOn[caster] = mutableSetOf(StatusEffectId.HIDING.id)
    world.spotsBehind[target] = Vec3L(4, 2, 0)

    assertTrue(shadowStep.isCastPossible(ctx(level = 1)))
    shadowStep.execute(ctx(level = 1))

    assertEquals(listOf(caster to Vec3L(4, 2, 0)), world.blinks)
    assertFalse(shadowStep.isReportedAsAction, "a cast would end the hiding")
    assertEquals(listOf(4L, 6L, 8L), (1..3).map { ShadowStep.rangeTiles(it) })
  }

  @Test
  fun `Shadow Step fizzles out of hiding`() {
    assertFalse(ShadowStep(LineOfSightService()).isCastPossible(ctx(level = 3)))
  }

  @Test
  fun `Gut Punch stuns for 4 to 6 seconds, and a boss for half as long`() {
    GutPunch(LineOfSightService()).execute(ctx(level = 2))
    world.natures[target] = Nature(Element.NORMAL, Size.MEDIUM, boss = true)
    GutPunch(LineOfSightService()).execute(ctx(level = 3))

    assertEquals(
      listOf(StatusEffectId.STUN.id to 5.0, StatusEffectId.STUN.id to 3.0),
      world.appliedEffects.map { it.effectId to it.durationSeconds }
    )
    assertEquals(listOf(4.0, 5.0, 6.0), (1..3).map { GutPunch.stunSeconds(it, isBoss = false) })
  }

  private fun ctx(level: Int) = SkillContextFixture.skillCtx(world = world, skillLevel = level)
}
