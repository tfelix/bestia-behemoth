package net.bestia.zone.dialog.conversation

import net.bestia.bnet.proto.InteractCmsgProto
import net.bestia.zone.message.CMSG
import net.bestia.zone.util.EntityId

/**
 * "I want to deal with that thing."
 *
 * Says nothing about how, because the server decides what interacting with an entity means. Talking is
 * the only answer today, which is why this lives beside the conversation - it moves the day a second
 * kind of interaction exists.
 */
data class InteractCMSG(
  override val playerId: Long,
  val targetEntityId: EntityId,
) : CMSG {

  companion object {
    fun fromBnet(accountId: Long, msg: InteractCmsgProto.InteractCMSG): InteractCMSG {
      return InteractCMSG(playerId = accountId, targetEntityId = msg.entityId)
    }
  }
}
