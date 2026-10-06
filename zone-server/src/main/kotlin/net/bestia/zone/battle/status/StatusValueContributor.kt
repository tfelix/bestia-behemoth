package net.bestia.zone.battle.status

import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.World
import net.bestia.zone.util.EntityId

/**
 * What an entity wears, folded into its status values after the passives and before the status effects. The
 * item slice owns the gear, so it implements this.
 */
interface StatusValueContributor {

  /** The component types [contribute] reads. */
  val reads: ComponentClassSet

  fun contribute(context: StatusValueRecalcContext, world: World, id: EntityId)
}
