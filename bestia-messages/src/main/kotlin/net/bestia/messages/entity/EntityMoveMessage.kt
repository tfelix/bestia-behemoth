package net.bestia.messages.entity

import java.util.ArrayList

import com.fasterxml.jackson.annotation.JsonProperty
import net.bestia.messages.AccountMessage
import net.bestia.messages.EntityMessage

import net.bestia.model.geometry.Point

/**
 * This message is send from the server to all clients as soon as a movement is
 * started as well as from the clients to the server.
 *
 * It is also send from the client to the server in order to perform a movement.
 * In this case the speed is ignored since it is determined by the server.
 *
 * It contains the path of the bestia as well as the speed. As long as nothing
 * changes along the path no further update is send and the client can use this
 * information to interpolate the movement of the entity. If a new
 * [EntityMoveMessage] is send by the server it takes precedence over the
 * old one.
 *
 *
 * @author Thomas Felix
 */
data class EntityMoveMessage(
    override val accountId: Long,
    override val entityId: Long,

    @JsonProperty("pX")
    private val cordsX: List<Long>,

    @JsonProperty("pY")
    private val cordsY: List<Long>
) : EntityMessage, AccountMessage {

  /**
   * Turns the list of coordinates into a array of points.
   *
   * @return The array of point objects.
   */
  val path: List<Point>
    get() {
      if (cordsX.size != cordsY.size) {
        throw IllegalStateException("Size of the coordiante arrays does not match.")
      }

      val patch = ArrayList<Point>(cordsX.size)

      for (i in cordsX.indices) {
        patch.add(Point(cordsX[i], cordsY[i]))
      }

      return patch
    }

  val messageId: String
    get() = MESSAGE_ID

  companion object {
    const val MESSAGE_ID = "entity.move"
  }
}
