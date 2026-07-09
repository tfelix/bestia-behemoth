package net.bestia.zone.item.loot

import net.bestia.bnet.proto.LootItemCMSGProto
import net.bestia.zone.message.CMSG
import net.bestia.zone.util.EntityId

data class LootItemCMSG(
  override val playerId: Long,
  val targetEntityId: EntityId
) : CMSG {
  companion object {
    fun fromBnet(playerId: Long, bnet: LootItemCMSGProto.LootItemCMSG): LootItemCMSG {
      return LootItemCMSG(playerId, bnet.entityId)
    }
  }
}
