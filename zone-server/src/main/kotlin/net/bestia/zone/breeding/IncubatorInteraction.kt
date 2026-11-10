package net.bestia.zone.breeding

import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.ecs.core.World
import net.bestia.zone.entity.StaticEntityKind
import net.bestia.zone.entity.ecs.PlayerStructureIdentity
import net.bestia.zone.identity.ecs.Account
import net.bestia.zone.prop.PropInteraction
import net.bestia.zone.session.ConnectionInfoService
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/** Clicking an Incubator opens its window, for its owner's master, whoever the owner is driving. */
@Component
class IncubatorInteraction(
  private val connections: ConnectionInfoService,
  private val incubators: IncubatorService,
) : PropInteraction {

  override val kind = StaticEntityKind.INCUBATOR

  override fun interact(world: World, actorId: EntityId, propId: EntityId): OpError? {
    val structureId = world.get(propId, PlayerStructureIdentity::class)?.structureId ?: return null
    val accountId = world.get(actorId, Account::class)?.accountId ?: return null

    return incubators.open(world, connections.getSelectedMasterEntityId(accountId), structureId)
  }
}
