package net.bestia.zoneserver.entity.component

import com.fasterxml.jackson.annotation.JsonProperty
import net.bestia.model.bestia.Direction
import net.bestia.model.geometry.Shape
import net.bestia.model.geometry.Point

/**
 * Entity with this component have a defined position in the world. Point refers
 * to the anchor point of a sprite if there is any.
 *
 * @author Thomas Felix
 */
data class PositionComponent(
    override val entityId: Long,

    @JsonProperty("s")
    val shape: Shape = Point(),

    /**
     * Returns the current direction of facing. Important to check for AI for
     * sight and detection checks.
     *
     * @return The face direction.
     */
    @JsonProperty("f")
    val facing: Direction = Direction.SOUTH,

    /**
     * Sets the flag if the entity blocks the line of sight.
     */
    @JsonProperty("sb")
    val isSightBlocking: Boolean = false
) : Component {

  val position: Point
    @JsonProperty("p")
    get() = shape.anchor
}