package net.bestia.zone.spellscroll.net

import net.bestia.bnet.proto.SpellScrollsCmsgProto
import net.bestia.zone.message.CMSG

data class SpellScrollsCMSG(
  override val playerId: Long,
  val uniqueIds: List<Long>,
) : CMSG {

  companion object {
    fun fromBnet(accountId: Long, proto: SpellScrollsCmsgProto.SpellScrollsCMSG): SpellScrollsCMSG {
      return SpellScrollsCMSG(accountId, proto.uniqueIdsList)
    }
  }
}
