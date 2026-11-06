package net.bestia.zone.casting.scripts.bestia

import net.bestia.zone.battle.BattleContextFixture
import net.bestia.zone.battle.LineOfSightService
import net.bestia.zone.casting.RecordingSkillWorld
import net.bestia.zone.casting.SkillContext
import net.bestia.zone.casting.SkillContextFixture
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DispelTest {

  private val sut = Dispel(LineOfSightService())

  @Test
  fun `a creature loses its buffs`() {
    val world = RecordingSkillWorld()
    val ctx = SkillContextFixture.skillCtx(world = world)

    sut.execute(ctx)

    assertEquals(listOf(BattleContextFixture.DEFENDER_ID), world.dispelled)
  }

  @Test
  fun `an enchanted ground item is tried at the base chance of the level`() {
    val world = RecordingSkillWorld()
    val ctx = groundItemCtx(world, skillLevel = 5)

    assertTrue(sut.isCastPossible(ctx))
    sut.execute(ctx)

    assertEquals(listOf(GROUND_ITEM to 100), world.dispelledGroundItems)
    assertEquals(emptyList(), world.dispelled)
  }

  @Test
  fun `a plain spot on the ground is no target`() {
    val ctx = SkillContextFixture.skillCtx(battle = BattleContextFixture.groundCtx())

    assertFalse(sut.isCastPossible(ctx))
  }

  private fun groundItemCtx(world: RecordingSkillWorld, skillLevel: Int): SkillContext {
    val battle = BattleContextFixture.groundCtx()

    return SkillContext(
      battle = battle,
      world = world,
      casterId = battle.attacker.id,
      targetEntityId = GROUND_ITEM,
      targetPosition = null,
      skillId = 1143L,
      skillLevel = skillLevel,
      skill = SkillContextFixture.skill(1143L),
    )
  }

  private companion object {
    const val GROUND_ITEM = 40L
  }
}
