package net.bestia.zone.battle.ecs.status

import net.bestia.zone.battle.Element
import net.bestia.zone.battle.Size
import net.bestia.zone.ecs.core.Component

/**
 * What a creature is made of: the element every hit on it is weighed against, and its body size.
 *
 * Set once at spawn from the species and never changed, so it needs no dirty tracking. An entity without
 * one - a master, a prop - counts as [Element.NORMAL].
 *
 * [size] is carried but not yet applied: Ragnarok Online's size table weighs a *weapon type* against a size,
 * and weapons have no type yet. See `SizeModifier`.
 */
data class Nature(
  val element: Element,
  val size: Size
) : Component
