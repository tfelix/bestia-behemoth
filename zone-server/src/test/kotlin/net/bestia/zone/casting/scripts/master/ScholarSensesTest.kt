package net.bestia.zone.casting.scripts.master

import net.bestia.zone.battle.BattleContextFixture
import net.bestia.zone.battle.LineOfSightService
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.casting.RecordingSkillWorld
import net.bestia.zone.casting.SkillContextFixture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ScholarSensesTest {

  @Test
  fun `Magic Sense sweeps at the caster's level`() {
    val world = RecordingSkillWorld()
    MagicSense().execute(SkillContextFixture.skillCtx(BattleContextFixture.entityCtx(), world, skillLevel = 3))

    assertEquals(listOf(3), world.magicSenses)
  }

  @Test
  fun `Sense reads the monster it was cast on`() {
    val world = RecordingSkillWorld()
    val ctx = SkillContextFixture.skillCtx(BattleContextFixture.entityCtx(), world)
    MonsterSense(LineOfSightService()).execute(ctx)

    assertEquals(1, world.monsterSenses.size)
  }

  @Test
  fun `Ruwach puts its pulse on the caster`() {
    val world = RecordingSkillWorld()
    val ctx = SkillContextFixture.skillCtx(BattleContextFixture.entityCtx(), world)
    Ruwach().execute(ctx)

    assertEquals(StatusEffectId.RUWACH.id, world.appliedEffects.single().effectId)
  }
}
