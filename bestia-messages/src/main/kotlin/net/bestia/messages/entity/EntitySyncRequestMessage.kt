package net.bestia.messages.entity

import net.bestia.messages.AccountMessage
import net.bestia.messages.MessageId

/**
 * Requests the server to send a full list with all visible entities to the
 * client. This message is issued by the engine if a reload has occured or the
 * engine is unsure to have synced to all entities.
 *
 * @author Thomas Felix
 */
data class EntitySyncRequestMessage(
    override val accountId: Long
) : AccountMessage, MessageId {

  override val messageId: String
    get() = MESSAGE_ID

  companion object {
    const val MESSAGE_ID = "entity.sync"
  }
}
