package net.bestia.zone.item

import net.bestia.zone.message.CMSG
import net.bestia.zone.util.EntityId

data class LootItemCMSG(
  override val playerId: Long,
  val targetEntityId: EntityId
) : CMSG {
  companion object {
    fun fromBnet(playerId: Long, bnet: net.bestia.bnet.proto.LootItemCMSGProto.LootItemCMSG): LootItemCMSG {
      return LootItemCMSG(playerId, bnet.entityId)
    }
  }
}
