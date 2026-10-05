package net.bestia.zone.party

import net.bestia.bnet.proto.LeavePartyCmsgProto
import net.bestia.zone.message.CMSG

/**
 * Player leaves their current party. If the requester is the owner the whole party is disbanded
 * instead - there is no ownership transfer.
 */
data class LeavePartyCMSG(
  override val playerId: Long
) : CMSG {
  companion object {
    fun fromBnet(accountId: Long, proto: LeavePartyCmsgProto.LeavePartyCMSG): LeavePartyCMSG {
      return LeavePartyCMSG(playerId = accountId)
    }
  }
}
