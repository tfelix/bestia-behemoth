package net.bestia.zone.world.prop.collect

import net.bestia.bnet.proto.CollectPropCMSGProto
import net.bestia.zone.message.CMSG
import net.bestia.zone.util.EntityId

/**
 * A request to take one static prop straight into the inventory.
 *
 * [targetEntityId] is a live ECS id the client read off a `ChunkStaticEntitiesSMSG`, and is therefore only
 * valid while it holds that chunk. Nothing validates it here - a stale one simply names an entity that is not
 * a collectible prop, which [CollectPropIntentSystem] refuses like any other.
 */
data class CollectPropCMSG(
  override val playerId: Long,
  val targetEntityId: EntityId
) : CMSG {

  companion object {
    fun fromBnet(playerId: Long, bnet: CollectPropCMSGProto.CollectPropCMSG): CollectPropCMSG {
      return CollectPropCMSG(playerId, bnet.entityId)
    }
  }
}
