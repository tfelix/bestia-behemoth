package net.bestia.zoneserver.entity.component

import com.fasterxml.jackson.annotation.JsonProperty
import net.bestia.model.domain.Direction
import net.bestia.model.geometry.CollisionShape
import net.bestia.model.geometry.Point

/**
 * Entity with this component have a defined position in the world. Point refers
 * to the anchor point of a sprite if there is any.
 *
 * @author Thomas Felix
 */
data class PositionComponent(
        override val id: Long,
        override val entityId: Long,

        var shape: CollisionShape = Point(),

        /**
         * Returns the current direction of facing. Important to check for AI for
         * sight and detection checks.
         *
         * @return The face direction.
         */
        @JsonProperty("f")
        var facing: Direction = Direction.SOUTH,

        /**
         * Sets the flag if the entity blocks the line of sight.
         *
         * @param sightBlocking
         * The flag to set the sight blocking.
         */
        @JsonProperty("sb")
        var isSightBlocking: Boolean = false
) : Component {

  /**
   * Returns the anchor position of the entities [CollisionShape].
   *
   * @return Current position of the entity.
   */
  /**
   * Its an alias for [.setPosition].
   *
   * @param pos
   * The new position.
   */
  var position: Point
    @JsonProperty("p")
    get() = shape.anchor
    set(pos) = setPosition(pos.x, pos.y)

  /**
   * Sets the position of this component to the given coordiantes.
   *
   * @param x
   * New x position.
   * @param y
   * New y position.
   */
  fun setPosition(x: Long, y: Long) {
    shape = shape.moveByAnchor(x, y)
  }
}