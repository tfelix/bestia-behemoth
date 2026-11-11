package net.bestia.zone.casting

import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.ecs.core.World
import net.bestia.zone.util.EntityId

/** Spell scrolls for Spell Training and Spell Inscription; the spellscroll slice implements it. */
interface SpellScrolls {

  /** Why [bestiaId] cannot learn from the scroll [scrollId] in the caster's bag, or null when it can. */
  fun trainingRefusal(world: World, casterId: EntityId, bestiaId: EntityId, scrollId: Long): OpError?

  /** Why the caster cannot copy [skillId] off [bestiaId], or null when it can. */
  fun inscriptionRefusal(world: World, casterId: EntityId, bestiaId: EntityId, skillId: Long): OpError?

  /** Takes the scroll [scrollId] out of the caster's bag for good; what it held, or null when it is not there. */
  fun takeScroll(world: World, casterId: EntityId, scrollId: Long): InscribedSpell?

  /** [skillId] as [bestiaId] knows it, or null when it knows no such attack. */
  fun attackOf(world: World, bestiaId: EntityId, skillId: Long): InscribedSpell?

  /** [bestiaId] knows [spell] from now on, at its level. */
  fun teach(world: World, casterId: EntityId, bestiaId: EntityId, spell: InscribedSpell)

  /** Writes [spell] onto a new scroll in the caster's bag. */
  fun inscribe(world: World, casterId: EntityId, spell: InscribedSpell)

  /** [bestiaId] is lost for good: it leaves the world, its bag goes to its master and its row is deleted. */
  fun breakMind(world: World, casterId: EntityId, bestiaId: EntityId)
}
