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
}
