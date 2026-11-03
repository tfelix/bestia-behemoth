package net.bestia.zone.honor

import net.bestia.zone.ecs.core.World
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/**
 * Whether two masters are sworn to rival Orders, which changes what a kill between them costs.
 *
 * Players cannot swear to an Order yet, so every master is unsworn and no two are rivals.
 */
@Component
class OrderAllegiance {

  fun areOpposing(world: World, master: EntityId, other: EntityId): Boolean {
    return false
  }
}
