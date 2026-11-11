package net.bestia.zone.trade.stall

import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.ecs.core.World
import net.bestia.zone.entity.StaticEntityKind
import net.bestia.zone.prop.PropInteraction
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/** Clicking a stall opens it: to manage for its seller, to buy from for everyone else. */
@Component
class StreetStallInteraction(
  private val stalls: StreetStalls,
) : PropInteraction {

  override val kind = StaticEntityKind.STREET_STALL

  override fun interact(world: World, actorId: EntityId, propId: EntityId): OpError? {
    return stalls.view(world, actorId, propId)
  }
}
