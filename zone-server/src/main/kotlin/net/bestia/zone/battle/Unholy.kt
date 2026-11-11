package net.bestia.zone.battle

import net.bestia.zone.battle.ecs.status.Nature

/** What the Priest's holy skills single out: a Demon, or the Undead by kind or by element. */
object Unholy {

  fun isUnholy(kind: BestiaKind?, element: Element): Boolean {
    return kind == BestiaKind.DEMON || kind == BestiaKind.UNDEAD || element.base == Element.UNDEAD
  }

  /** An entity with no Nature, like a master, is never unholy. */
  fun isUnholy(nature: Nature?): Boolean {
    return nature != null && isUnholy(nature.kind, nature.element)
  }
}
