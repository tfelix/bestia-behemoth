package net.bestia.zone.casting.scripts.master

import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.battle.BattleContextFixture
import net.bestia.zone.battle.LineOfSightService
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.casting.RecordingSkillWorld
import net.bestia.zone.casting.SkillContextFixture
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.identity.ecs.Master
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class KnightVanguardSkillsTest {

  private val world = RecordingSkillWorld()
  private val caster = BattleContextFixture.ATTACKER_ID
  private val target = BattleContextFixture.DEFENDER_ID

  @Test
  fun `Provoke puts a fresh provoke on a foe, so the newest provoker wins`() {
    val provoke = Provoke(LineOfSightService())
    world.foes += target

    assertTrue(provoke.isCastPossible(ctx(level = 3)))
    provoke.execute(ctx(level = 3))

    assertEquals(listOf(target to StatusEffectId.PROVOKED.id), world.removedEffects)
    assertEquals(
      listOf(Triple(target, StatusEffectId.PROVOKED.id, 3)),
      world.appliedEffects.map { Triple(it.targetEntityId, it.effectId, it.level) }
    )
  }

  @Test
  fun `Provoke fizzles on a friend and leaves a master alone`() {
    val provoke = Provoke(LineOfSightService())
    assertFalse(provoke.isCastPossible(ctx(level = 1)), "not a foe")

    world.putMaster(target, masterId = 9L)
    provoke.execute(ctx(level = 1))

    assertTrue(world.appliedEffects.isEmpty())
  }

  @Test
  fun `Provoke is refused at the button on a master`() {
    val ecs = testWorld()
    val master = ecs.createEntity { id -> add(id, Master(masterId = 9L)) }
    val wolf = ecs.createEntity { }
    val provoke = Provoke(LineOfSightService())

    assertEquals(OpError.SKILL_NOT_ON_MASTERS, provoke.checkTarget(ecs, caster, master, 1, emptyList()))
    assertNull(provoke.checkTarget(ecs, caster, wolf, 1, emptyList()))
  }

  private fun ctx(level: Int) = SkillContextFixture.skillCtx(world = world, skillLevel = level)
}
