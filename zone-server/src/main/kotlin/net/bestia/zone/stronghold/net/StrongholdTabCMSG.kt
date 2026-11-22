package net.bestia.zone.stronghold.net

import net.bestia.bnet.proto.StrongholdTabCmsgProto
import net.bestia.zone.message.CMSG

data class StrongholdTabCMSG(
  override val playerId: Long,
  val action: Action,
  val rank: Int,
) : CMSG {

  /** In the proto's order. */
  enum class Action { SHOW, COLLECT, SEND_STORAGE_AWAY }

  companion object {
    fun fromBnet(playerId: Long, bnet: StrongholdTabCmsgProto.StrongholdTabCMSG): StrongholdTabCMSG {
      return StrongholdTabCMSG(playerId, Action.entries[bnet.actionValue], bnet.seedRank)
    }
  }
}
