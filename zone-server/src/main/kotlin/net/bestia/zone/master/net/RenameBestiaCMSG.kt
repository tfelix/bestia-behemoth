package net.bestia.zone.master.net

import net.bestia.bnet.proto.RenameBestiaCmsgProto
import net.bestia.zone.message.CMSG
import net.bestia.zone.util.EntityId

data class RenameBestiaCMSG(
  override val playerId: Long,
  val entityId: EntityId,
  val name: String,
) : CMSG {
  companion object {
    fun fromBnet(accountId: Long, proto: RenameBestiaCmsgProto.RenameBestiaCMSG): RenameBestiaCMSG {
      return RenameBestiaCMSG(
        playerId = accountId,
        entityId = proto.entityId,
        name = proto.name,
      )
    }
  }
}
