package net.bestia.zone.casting.scripts

import net.bestia.zone.battle.BattleContextFixture
import net.bestia.zone.battle.LineOfSightService
import net.bestia.zone.battle.damage.Heal
import net.bestia.zone.casting.RecordingSkillWorld
import net.bestia.zone.casting.SkillContext
import net.bestia.zone.casting.SkillContextFixture
import net.bestia.zone.battle.status.StatusEffectId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FirstAidTest {

  private val sut = FirstAid(LineOfSightService())

  @Test
  fun `five pulses heal a share of a small pool`() {
    // 30% of 200 is 60, below the Lv.1 cap of 100.
    assertEquals(60, healedBy(skillLevel = 1, targetMaxHealth = 200))
  }

  @Test
  fun `the flat amount caps the heal on a large pool`() {
    assertEquals(100, healedBy(skillLevel = 1, targetMaxHealth = 2000))
  }

  @Test
  fun `each rank heals more than the last`() {
    assertEquals(listOf(100, 200, 350), (1..3).map { healedBy(skillLevel = it, targetMaxHealth = 2000) })
  }

  @Test
  fun `each rank costs more mana`() {
    val skill = ctx(skillLevel = 1, targetMaxHealth = 2000).skill

    assertEquals(listOf(12, 16, 20), (1..3).map { sut.costOf(skill, it).manaCost })
  }

  @Test
  fun `a target treated within the last minute is refused`() {
    val untreated = ctx(skillLevel = 1, targetMaxHealth = 1000)
    val justTreated = ctx(skillLevel = 1, targetMaxHealth = 1000, targetEffects = setOf(StatusEffectId.FIRST_AID_COOLDOWN.id))

    assertTrue(sut.isCastPossible(untreated))
    assertFalse(sut.isCastPossible(justTreated), "a bestia may only receive First Aid once a minute")
  }

  @Test
  fun `starting the cast marks the patient and heals nothing yet`() {
    val world = RecordingSkillWorld()
    val ctx = ctx(skillLevel = 1, targetMaxHealth = 1000, world = world)

    assertNull(sut.execute(ctx))
    assertEquals(StatusEffectId.FIRST_AID_COOLDOWN.id, world.appliedEffects.single().effectId)
    assertTrue(world.appliedDamage.isEmpty())
  }

  @Test
  fun `a healer who lost the race for the patient channels for nothing`() {
    val world = RecordingSkillWorld()
    val ctx = ctx(skillLevel = 1, targetMaxHealth = 1000, world = world)
    sut.execute(ctx)
    world.effectSource = ctx.casterId + 1

    repeat(5) { sut.onChannelTick(ctx, it) }

    assertTrue(world.appliedDamage.isEmpty())
  }

  @Test
  fun `there is nothing to bandage on open ground`() {
    val world = RecordingSkillWorld()
    val groundCtx = SkillContextFixture.skillCtx(BattleContextFixture.groundCtx(), world)

    assertFalse(sut.isCastPossible(groundCtx))
    assertNull(sut.execute(groundCtx))
    assertTrue(world.appliedEffects.isEmpty())
  }

  /** Starts the cast, then lets all five pulses of the channel land, and sums what they healed. */
  private fun healedBy(skillLevel: Int, targetMaxHealth: Int): Int {
    val world = RecordingSkillWorld()
    val ctx = ctx(skillLevel, targetMaxHealth, world = world)
    sut.execute(ctx)
    world.effectSource = ctx.casterId

    repeat(5) { sut.onChannelTick(ctx, it) }

    return world.appliedDamage.sumOf { (_, damage) -> (damage as Heal).amount }
  }

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
