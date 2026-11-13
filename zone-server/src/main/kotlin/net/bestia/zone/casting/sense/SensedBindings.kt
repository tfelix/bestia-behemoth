package net.bestia.zone.casting.sense

import net.bestia.zone.battle.Element
import net.bestia.zone.ecs.core.World
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.util.AccountId
import net.bestia.zone.util.EntityId

/** The Spell Bindings a Magic Sense sweep can find; the spellbinding slice implements it. */
interface SensedBindings {

  /** The bindings within [range] tiles of [at] that a sweep of [potency] finds, by the concealment contest. */
  fun around(world: World, at: Vec3L, range: Long, potency: Int): List<SensedBinding>

  /** Shows the glyph [glyphId] to [viewers] beside its binder, for [seconds]. */
  fun reveal(world: World, glyphId: EntityId, viewers: Set<AccountId>, seconds: Int)

  /** [reading] is what level 5 adds: the trigger, the filter and the binder. [glyphId] is null for an item. */
  data class SensedBinding(
    val at: Vec3L,
    val potency: Int,
    val element: Element,
    val spellName: String,
    val reading: String,
    val glyphId: EntityId?,
  )
}
