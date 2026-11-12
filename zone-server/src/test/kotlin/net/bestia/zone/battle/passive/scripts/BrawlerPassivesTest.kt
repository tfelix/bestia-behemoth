package net.bestia.zone.battle.passive.scripts

import net.bestia.zone.battle.status.StatusValueRecalcContext
import net.bestia.zone.battle.status.StatusValues
import net.bestia.zone.casting.SkillLevelCost
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BrawlerPassivesTest {

  private val context = StatusValueRecalcContext(StatusValues(1, 1, 1, 1, 1, 1), baseSpeed = 10f)

  @Test
  fun `Tough Guy cuts every stamina cost and drain by a tenth per level and quickens stamina regen alike`() {
    ToughGuy().apply(context, 5)

    assertEquals(50, context.staminaCostReduction)
    assertEquals(50, context.staminaDrainReduction)
    assertEquals(50, context.staminaRegen.percent)
  }

  @Test
  fun `a skill costs a Tough Guy less stamina, never below nothing`() {
    val sprint = SkillLevelCost(manaCost = 0, staminaCost = 20, cooldownSeconds = 0f, castTimeSeconds = 0f, rangeTiles = 1)

    assertEquals(14, sprint.staminaCostFor(savedPercent = 30))
    assertEquals(0, sprint.staminaCostFor(savedPercent = 150))
  }

  @Test
  fun `Unbreakable spares a lethal blow once per 10 minutes, 6 at Lv 3`() {
    assertEquals(listOf(600, 600, 360), (1..3).map { level -> recalc { Unbreakable().apply(it, level) }.lastStandCooldownSeconds })
  }

  @Test
  fun `Unbreakable steadies against stun and knockback from Lv 2`() {
    assertEquals(listOf(false, true, true), (1..3).map { level -> recalc { Unbreakable().apply(it, level) }.halvesStunAndKnockback })
  }

  private fun recalc(apply: (StatusValueRecalcContext) -> Unit): StatusValueRecalcContext {
    return StatusValueRecalcContext(StatusValues(1, 1, 1, 1, 1, 1), baseSpeed = 10f).also(apply)
  }
}
