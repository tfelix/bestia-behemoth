package net.bestia.zone.casting.scripts.bestia

import net.bestia.zone.battle.BestiaKind
import net.bestia.zone.battle.Element
import net.bestia.zone.battle.ecs.status.Nature

/** Whether a creature is one of the dead walking, which holy skills single out. */
object Undeath {

  fun isUndead(nature: Nature?): Boolean {
    return nature != null && (nature.kind == BestiaKind.UNDEAD || nature.element.base == Element.UNDEAD)
  }
}
