package net.bestia.zone.casting.scripts.master

import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.battle.BattleContextFixture
import net.bestia.zone.battle.LineOfSightService
import net.bestia.zone.casting.InscribedSpell
import net.bestia.zone.casting.RecordingSkillWorld
import net.bestia.zone.casting.SkillContextFixture
import net.bestia.zone.casting.SpellScrollOdds
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Random

class SpellScrollSageTest {

  /** Answers each `nextInt` with the next of [rolls], so a test picks the outcome of every roll. */
  private class Rolls(vararg rolls: Int) : Random() {
    private val queue = ArrayDeque(rolls.toList())

    override fun nextInt(bound: Int): Int = queue.removeFirst()
  }

  private val spell = InscribedSpell(skillId = 1004L, level = 2, attackLevel = 15)
  private val bestiaId = BattleContextFixture.DEFENDER_ID

  @Test
  fun `the base chance falls with the level a bestia learns the attack at`() {
    assertEquals(
      listOf(80, 80, 70, 60, 50, 10, -20, -40),
      listOf(1, 20, 21, 60, 80, 90, 100, 101).map(SpellScrollOdds::basePercent)
    )
  }

  @Test
  fun `training adds 10 per level and inscription the mind of the caster too`() {
    assertEquals(90, SpellScrollOdds.trainingPercent(attackLevel = 50, skillLevel = 3))
    // -20 + 30 + 40 / 2 + 20 / 4
    assertEquals(35, SpellScrollOdds.inscriptionPercent(attackLevel = 95, skillLevel = 3, intelligence = 40, willpower = 20))
    assertEquals(0, SpellScrollOdds.inscriptionPercent(attackLevel = 120, skillLevel = 1, intelligence = 0, willpower = 0))
    assertEquals(100, SpellScrollOdds.trainingPercent(attackLevel = 1, skillLevel = 5))
  }

  @Test
  fun `half of the failed extractions break the mind`() {
    assertEquals(25, SpellScrollOdds.mindBreakPercent(50))
    assertEquals(0, SpellScrollOdds.mindBreakPercent(100))
  }

  @Test
  fun `Spell Training teaches the scroll's attack when the roll is under the chance`() {
    val world = RecordingSkillWorld().apply { spellScrolls[7L] = spell }

    // 80 + 10 at level 1
    SpellTraining(LineOfSightService(), random = Rolls(89)).execute(trainingCtx(world, skillLevel = 1))

    assertEquals(listOf(bestiaId to spell), world.taughtSpells)
  }

  @Test
  fun `Spell Training spends the scroll also when it fails`() {
    val world = RecordingSkillWorld().apply { spellScrolls[7L] = spell }

    SpellTraining(LineOfSightService(), random = Rolls(90)).execute(trainingCtx(world, skillLevel = 1))

    assertTrue(world.taughtSpells.isEmpty())
    assertTrue(world.spellScrolls.isEmpty())
    assertEquals(listOf(OpError.SPELL_TRAINING_FAILED), world.toldCaster)
  }

  @Test
  fun `a refused training is no cast at all`() {
    val world = RecordingSkillWorld().apply { spellRefusal = OpError.SPELL_ALREADY_KNOWN }

    assertTrue(!SpellTraining(LineOfSightService()).isCastPossible(trainingCtx(world, skillLevel = 1)))
  }

  @Test
  fun `Spell Inscription writes the bestia's attack onto a scroll on success`() {
    val world = RecordingSkillWorld().apply { bestiaAttacks[spell.skillId] = spell }

    SpellInscription(LineOfSightService(), random = Rolls(0)).execute(inscriptionCtx(world))

    assertEquals(listOf(spell), world.inscribedSpells)
    assertTrue(world.brokenMinds.isEmpty())
  }

  @Test
  fun `a failed extraction breaks the mind half of the time`() {
    val breaking = RecordingSkillWorld().apply { bestiaAttacks[spell.skillId] = spell }
    val sparing = RecordingSkillWorld().apply { bestiaAttacks[spell.skillId] = spell }

    SpellInscription(LineOfSightService(), random = Rolls(99, 49)).execute(inscriptionCtx(breaking))
    SpellInscription(LineOfSightService(), random = Rolls(99, 50)).execute(inscriptionCtx(sparing))

    assertEquals(listOf(bestiaId), breaking.brokenMinds)
    assertTrue(sparing.brokenMinds.isEmpty())
    assertEquals(listOf(OpError.SPELL_INSCRIPTION_FAILED), sparing.toldCaster)
    assertTrue(breaking.inscribedSpells.isEmpty() && sparing.inscribedSpells.isEmpty())
  }

  private fun trainingCtx(world: RecordingSkillWorld, skillLevel: Int) =
    SkillContextFixture.skillCtx(BattleContextFixture.entityCtx(), world, skillLevel = skillLevel, args = listOf(7L))

  private fun inscriptionCtx(world: RecordingSkillWorld) =
    SkillContextFixture.skillCtx(BattleContextFixture.entityCtx(), world, skillLevel = 1, args = listOf(spell.skillId))
}
