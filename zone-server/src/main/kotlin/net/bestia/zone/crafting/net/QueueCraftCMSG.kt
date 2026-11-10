package net.bestia.zone.crafting.net

import net.bestia.bnet.proto.QueueCraftCmsgProto
import net.bestia.zone.message.CMSG

/** The player added a craft to a station's queue. */
data class QueueCraftCMSG(
  override val playerId: Long,
  val structureId: Long,
  val recipeId: Long,
  val amount: Int
) : CMSG {
  companion object {
    fun fromBnet(accountId: Long, queueCraft: QueueCraftCmsgProto.QueueCraftCMSG): QueueCraftCMSG {
      return QueueCraftCMSG(
        playerId = accountId,
        structureId = queueCraft.structureId,
        recipeId = queueCraft.recipeId,
        amount = queueCraft.amount.coerceAtLeast(1)
      )
    }
  }
}
