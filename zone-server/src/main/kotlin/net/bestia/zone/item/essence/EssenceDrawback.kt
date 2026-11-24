package net.bestia.zone.item.essence

import net.bestia.zone.battle.status.StatusValueRecalcContext

/** The price a darker essence asks for its one and a half times the points. It does not grow with the gem. */
enum class EssenceDrawback {
  MOVE_SPEED,
  DEF,
  HIT,
  MAX_MANA;

  fun applyTo(context: StatusValueRecalcContext) {
    when (this) {
      MOVE_SPEED -> context.speed *= 0.9f
      DEF -> context.addPercent(defense = -10)
      HIT -> context.addAccuracy(hit = -10)
      MAX_MANA -> context.addMaxPools(manaPercent = -10)
    }
  }
}
