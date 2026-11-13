package net.bestia.zone.battle.status.scripts

import net.bestia.zone.battle.ecs.effects.ActiveStatusEffect
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.battle.status.StatusValueRecalcContext
import net.bestia.zone.battle.status.StatusValues
import net.bestia.zone.ecs.core.testWorld
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class HeardSongsTest {

  @Test
  fun `Battle Hymn adds 3 percent ATK and MATK a level, more from a Bard with Grand Performance`() {
    assertEquals(15, BattleHymnHeard().attackPercentAt(level = 5, effectPercent = 100))
    assertEquals(19, BattleHymnHeard().attackPercentAt(level = 5, effectPercent = 130))
  }

  private fun HeardSongScript.attackPercentAt(level: Int, effectPercent: Int): Int {
    return recalc(StatusEffectId.BATTLE_HYMN, level, effectPercent).attackPercent
  }

  private fun HeardSongScript.recalc(id: StatusEffectId, level: Int, effectPercent: Int): StatusValueRecalcContext {
    val context = StatusValueRecalcContext(StatusValues(1, 1, 1, 1, 1, 1), baseSpeed = 10f)
    val effect = ActiveStatusEffect(id.id, level, 5f).apply { amount = effectPercent }
    apply(testWorld(), 1L, context, effect)

    return context
  }
}
