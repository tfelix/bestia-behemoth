package net.bestia.zone.battle.skill.scripts

import net.bestia.zone.battle.BattleContextFixture
import net.bestia.zone.battle.LineOfSightService
import net.bestia.zone.battle.damage.Heal
import net.bestia.zone.battle.skill.RecordingSkillWorld
import net.bestia.zone.battle.skill.SkillContext
import net.bestia.zone.battle.skill.SkillContextFixture
import net.bestia.zone.battle.status.StatusEffectId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FirstAidTest {

  private val sut = FirstAid(LineOfSightService())

  @Test
  fun `heals a share of the target's own pool once that beats the flat floor`() {
    // 30% of 2000 is 600, well past the Lv.1 floor of 100.
    val healed = sut.execute(ctx(skillLevel = 1, targetMaxHealth = 2000))

    assertTrue(healed is Heal)
    assertEquals(600, healed!!.amount)
  }

  @Test
  fun `falls back to the flat amount for a target with a small pool`() {
    // 30% of 100 is 30, so the Lv.1 floor of 100 is what a young bestia actually gets.
    assertEquals(100, healedBy(skillLevel = 1, targetMaxHealth = 100))
  }

  @Test
  fun `each rank heals more than the last`() {
    val amounts = (1..3).map { healedBy(skillLevel = it, targetMaxHealth = 2000) }

    assertEquals(listOf(600, 1200, 2000), amounts)
  }

  @Test
  fun `a rank beyond the table is treated as the top rank rather than crashing`() {
    assertEquals(
      healedBy(skillLevel = 3, targetMaxHealth = 2000),
      healedBy(skillLevel = 9, targetMaxHealth = 2000)
    )
  }

  @Test
  fun `a target treated within the last minute is refused`() {
    val untreated = ctx(skillLevel = 1, targetMaxHealth = 1000)
    val justTreated = ctx(
      skillLevel = 1,
      targetMaxHealth = 1000,
      targetEffects = setOf(StatusEffectId.FIRST_AID_COOLDOWN.id)
    )

    assertTrue(sut.isCastPossible(untreated))
    assertFalse(sut.isCastPossible(justTreated), "a bestia may only receive First Aid once a minute")
  }

  @Test
  fun `a resolved cast marks the target so the next one is refused`() {
    val world = RecordingSkillWorld()
    val ctx = ctx(skillLevel = 1, targetMaxHealth = 1000, world = world)

    sut.execute(ctx)

    val marked = world.appliedEffects.single()
    assertEquals(StatusEffectId.FIRST_AID_COOLDOWN.id, marked.effectId)
    assertEquals(ctx.targetEntityId, marked.targetEntityId, "the mark goes on the patient, not the healer")
  }

  @Test
  fun `a second healer resolving at the same time is refused, and heals nothing`() {
    // The race off-thread resolution makes possible: both casters started channelling on an untreated bestia,
    // so both pass the snapshot check in isCastPossible. The claim on the cooldown is what separates them.
    val world = RecordingSkillWorld()
    val first = ctx(skillLevel = 1, targetMaxHealth = 2000, world = world)
    val second = ctx(skillLevel = 1, targetMaxHealth = 2000, world = world)

    assertTrue(sut.isCastPossible(first))
    assertTrue(sut.isCastPossible(second), "neither caster can see the other's mark yet")

    assertEquals(600, sut.execute(first)?.amount)
    assertNull(sut.execute(second), "the healer that lost the claim must heal nothing")
    assertEquals(1, world.appliedEffects.size, "the cooldown is claimed once, not twice")
  }

  @Test
  fun `there is nothing to bandage on open ground`() {
    val world = RecordingSkillWorld()
    val groundCtx = SkillContextFixture.skillCtx(BattleContextFixture.groundCtx(), world)

    assertFalse(sut.isCastPossible(groundCtx))
    assertNull(sut.execute(groundCtx))
    assertTrue(world.appliedEffects.isEmpty())
  }

  private fun healedBy(skillLevel: Int, targetMaxHealth: Int): Int =
    sut.execute(ctx(skillLevel, targetMaxHealth))?.amount
      ?: error("First Aid must always heal something against an entity")

  private fun ctx(
    skillLevel: Int,
    targetMaxHealth: Int,
    targetEffects: Set<Long> = emptySet(),
    world: RecordingSkillWorld = RecordingSkillWorld()
  ): SkillContext = SkillContextFixture.skillCtx(
    BattleContextFixture.entityCtx(
      attack = BattleContextFixture.attack(skillLevel),
      defenderEntity = BattleContextFixture.battleEntity(
        maxHealth = targetMaxHealth,
        activeEffectIds = targetEffects
      )
    ),
    world
  )
}
