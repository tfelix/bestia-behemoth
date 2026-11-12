package net.bestia.zone.battle

import net.bestia.zone.battle.status.EntityAction
import net.bestia.zone.ecs.core.World
import net.bestia.zone.util.EntityId

/** Told when an entity swings, casts or walks, so an effect on it can answer, like a bleeding wound opening. */
fun interface ActionReactions {

  fun notifyActed(world: World, entityId: EntityId, action: EntityAction)

  companion object {
    val NONE = ActionReactions { _, _, _ -> }
  }
}
