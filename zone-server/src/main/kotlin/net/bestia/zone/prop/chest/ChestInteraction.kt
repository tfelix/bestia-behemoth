package net.bestia.zone.prop.chest

import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.ecs.core.World
import net.bestia.zone.entity.StaticEntityKind
import net.bestia.zone.identity.ecs.Account
import net.bestia.zone.item.ecs.ChestStorage
import net.bestia.zone.item.ecs.ExchangeReach
import net.bestia.zone.item.ecs.Inventory
import net.bestia.zone.item.ecs.ItemOwner
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.prop.PropInteraction
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/** Clicking a chest opens it, for the master who built it and that master's bestias. */
@Component
class ChestInteraction(
  private val outMessageProcessor: OutMessageProcessor,
) : PropInteraction {

  override val kind = StaticEntityKind.CHEST

  override fun interact(world: World, actorId: EntityId, propId: EntityId): OpError? {
    val chest = world.get(propId, ChestStorage::class) ?: return null
    val contents = world.get(propId, Inventory::class) ?: return null

    if (ItemOwner.of(world, actorId)?.masterId != chest.ownerMasterId) {
      return OpError.CHEST_NOT_YOURS
    }
    if (!ExchangeReach.within(world, actorId, propId)) {
      return OpError.CHEST_OUT_OF_REACH
    }
    val accountId = world.get(actorId, Account::class)?.accountId ?: return null

    outMessageProcessor.sendToPlayer(accountId, ChestOpenedSMSG(propId, ChestStorage.SLOTS))
    // Goes out with the next sync, after the message above, to the owner like every later change.
    contents.markDirty()

    return null
  }
}
