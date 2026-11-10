package net.bestia.zone.casting.scripts

import net.bestia.zone.battle.BattleContextFixture
import net.bestia.zone.battle.LineOfSightService
import net.bestia.zone.casting.RecordingSkillWorld
import net.bestia.zone.casting.SkillContextFixture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** Stands for every crafting skill: they share [net.bestia.zone.casting.CraftingSkillStrategy] and nothing else. */
class CarpentryTest {

  /** A station comes from a kit, which is the first entry of the window this opens away from one. */
  @Test
  fun `a cast on bare ground opens the crafting window with the skill picked`() {
    val world = RecordingSkillWorld()
    val ctx = SkillContextFixture.skillCtx(BattleContextFixture.groundCtx(), world, skillId = CARPENTRY_ID)

    assertNull(Carpentry(LineOfSightService()).execute(ctx), "working is not hitting")

    assertEquals(listOf(CARPENTRY_ID), world.recipeOffers)
  }

  private companion object {
    const val CARPENTRY_ID = 10L
  }
}
