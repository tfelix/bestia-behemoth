package net.bestia.zone.battle.skill.scripts

import net.bestia.zone.battle.BattleContextFixture
import net.bestia.zone.battle.LineOfSightService
import net.bestia.zone.battle.damage.Heal
import net.bestia.zone.battle.damage.Miss
import net.bestia.zone.battle.status.StatusEffectId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FirstAidTest {

  private val sut = FirstAid(LineOfSightService())

  @Test
  fun `heals a share of the target's own pool once that beats the flat floor`() {
    // 30% of 2000 is 600, well past the Lv.1 floor of 100.
    val healed = sut.execute(ctx(skillLevel = 1, targetMaxHealth = 2000))

    assertTrue(healed is Heal)
    assertEquals(600, healed.amount)
  }

  @Test
  fun `falls back to the flat amount for a target with a small pool`() {
    // 30% of 100 is 30, so the Lv.1 floor of 100 is what a young bestia actually gets.
    assertEquals(100, sut.execute(ctx(skillLevel = 1, targetMaxHealth = 100)).amount)
  }

  @Test
  fun `each rank heals more than the last`() {
    val amounts = (1..3).map { sut.execute(ctx(skillLevel = it, targetMaxHealth = 2000)).amount }

    assertEquals(listOf(600, 1200, 2000), amounts)
  }

  @Test
  fun `a rank beyond the table is treated as the top rank rather than crashing`() {
    assertEquals(
      sut.execute(ctx(skillLevel = 3, targetMaxHealth = 2000)).amount,
      sut.execute(ctx(skillLevel = 9, targetMaxHealth = 2000)).amount
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

    assertTrue(sut.isAttackPossible(untreated))
    assertFalse(sut.isAttackPossible(justTreated), "a bestia may only receive First Aid once a minute")
  }

  @Test
  fun `a resolved cast marks the target so the next one is refused`() {
    assertEquals(listOf(StatusEffectId.FIRST_AID_COOLDOWN), sut.effectsOnTarget(ctx(1, 1000)))
  }

  @Test
  fun `there is nothing to bandage on open ground`() {
    val groundCtx = BattleContextFixture.groundCtx()

    assertEquals(Miss, sut.execute(groundCtx))
    assertFalse(sut.isAttackPossible(groundCtx))
  }

  private fun ctx(
    skillLevel: Int,
    targetMaxHealth: Int,
    targetEffects: Set<Long> = emptySet()
  ) = BattleContextFixture.entityCtx(
    attack = BattleContextFixture.attack(skillLevel),
    defenderEntity = BattleContextFixture.battleEntity(
      maxHealth = targetMaxHealth,
      activeEffectIds = targetEffects
    )
  )
}
