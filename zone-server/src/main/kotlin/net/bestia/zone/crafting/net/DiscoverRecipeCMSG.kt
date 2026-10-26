package net.bestia.zone.crafting.net

import net.bestia.bnet.proto.DiscoverRecipeCmsgProto
import net.bestia.zone.message.CMSG

/** The player tries a mix of [materials] (player item id to amount) against the blueprints of [skillId]. */
data class DiscoverRecipeCMSG(
  override val playerId: Long,
  val skillId: Long,
  val materials: List<Pair<Long, Int>>,
) : CMSG {
  companion object {
    fun fromBnet(accountId: Long, msg: DiscoverRecipeCmsgProto.DiscoverRecipeCMSG): DiscoverRecipeCMSG =
      DiscoverRecipeCMSG(
        playerId = accountId,
        skillId = msg.skillId,
        materials = msg.materialsList.map { it.playerItemId to it.amount }
      )
  }
}
