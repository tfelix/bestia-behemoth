package net.bestia.zone.dialog.conversation

import net.bestia.bnet.proto.ConversationChoiceCmsgProto
import net.bestia.zone.message.CMSG
import net.bestia.zone.util.EntityId

/** The option a player picked, against the entity they picked it from. */
data class ConversationChoiceCMSG(
  override val playerId: Long,
  val targetEntityId: EntityId,
  val topicId: Int,
) : CMSG {

  companion object {
    fun fromBnet(accountId: Long, msg: ConversationChoiceCmsgProto.ConversationChoiceCMSG): ConversationChoiceCMSG {
      return ConversationChoiceCMSG(
        playerId = accountId,
        targetEntityId = msg.entityId,
        topicId = msg.topicId,
      )
    }
  }
}
