package net.bestia.messages.map

import net.bestia.messages.AccountMessage
import net.bestia.messages.MessageId
import net.bestia.model.geometry.Point

/**
 * The ECS is adviced to move the given entity on the map. This is an internal
 * message.
 *
 * @author Thomas Felix
 */
data class MapMoveMessage(
    override val accountId: Long,

    /**
     * Gets the point on which the bestia should be moved.
     *
     * @return The point to which the bestia is moved.
     */
    val target: Point
) : AccountMessage, MessageId {

  override val messageId: String
    get() = MESSAGE_ID

  companion object {
    const val MESSAGE_ID = "map.move"
  }
}
