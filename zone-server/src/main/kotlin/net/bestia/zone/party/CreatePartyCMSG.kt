package net.bestia.zone.party

import net.bestia.bnet.proto.CreatePartyCmsgProto
import net.bestia.zone.message.CMSG

data class CreatePartyCMSG(
  override val playerId: Long,
  val partyName: String
) : CMSG {
  companion object {
    fun fromBnet(accountId: Long, proto: CreatePartyCmsgProto.CreatePartyCMSG): CreatePartyCMSG {
      return CreatePartyCMSG(playerId = accountId, partyName = proto.partyName)
    }
  }
}
