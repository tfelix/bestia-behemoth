package net.bestia.zone.item

import net.bestia.bnet.proto.UseItemCMSGProto
import net.bestia.zone.message.CMSG
import net.bestia.zone.script.ScriptArgs

data class UseItemCMSG(
  override val playerId: Long,
  val itemId: Long,

  /** Whatever the client gathered first, for an item whose script asks for more than "use it". */
  val args: ScriptArgs
) : CMSG {

  companion object {
    fun fromBnet(playerId: Long, bnet: UseItemCMSGProto.UseItemCMSG): UseItemCMSG {
      return UseItemCMSG(
        playerId = playerId,
        itemId = bnet.itemId,
        args = ScriptArgs.fromBnet(bnet.args)
      )
    }
  }
}
