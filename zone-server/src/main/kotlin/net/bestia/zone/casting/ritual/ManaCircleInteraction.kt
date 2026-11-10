package net.bestia.zone.casting.ritual

import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.ecs.core.World
import net.bestia.zone.entity.StaticEntityKind
import net.bestia.zone.prop.PropInteraction
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/** Using a Mana Circle's focus stone opens, joins or starts its ritual. */
@Component
class ManaCircleInteraction(
  private val rituals: RitualService,
) : PropInteraction {

  override val kind = StaticEntityKind.MANA_CIRCLE

  override fun interact(world: World, actorId: EntityId, propId: EntityId): OpError? {
    return rituals.interact(world, actorId, propId)
  }
}
