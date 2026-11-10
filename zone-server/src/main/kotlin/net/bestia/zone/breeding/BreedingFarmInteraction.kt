package net.bestia.zone.breeding

import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.ecs.core.World
import net.bestia.zone.entity.StaticEntityKind
import net.bestia.zone.identity.ecs.Account
import net.bestia.zone.prop.PropInteraction
import net.bestia.zone.entity.ecs.PlayerStructureIdentity
import net.bestia.zone.session.ConnectionInfoService
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/** Clicking a Breeding Farm opens its window, for its owner's master, whoever the owner is driving. */
@Component
class BreedingFarmInteraction(
  private val connections: ConnectionInfoService,
  private val farms: BreedingFarmService,
) : PropInteraction {

  override val kind = StaticEntityKind.BREEDING_FARM

  override fun interact(world: World, actorId: EntityId, propId: EntityId): OpError? {
    val structureId = world.get(propId, PlayerStructureIdentity::class)?.structureId ?: return null
    val accountId = world.get(actorId, Account::class)?.accountId ?: return null

    return farms.open(world, connections.getSelectedMasterEntityId(accountId), structureId)
  }
}
