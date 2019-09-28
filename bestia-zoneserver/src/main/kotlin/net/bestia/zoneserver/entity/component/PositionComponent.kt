package net.bestia.zoneserver.entity.component

import net.bestia.model.geometry.Shape
import net.bestia.model.geometry.Vec3

/**
 * Entity with this component have a defined position in the world. Point refers
 * to the anchor point of a sprite if there is any.
 *
 * @author Thomas Felix
 */
data class PositionComponent(
    override val entityId: Long,

    val shape: Shape = Vec3(),

    /**
     * Returns the current direction of facing. Important to check for AI for
     * sight and detection checks.
     *
     * @return The face direction.
     */
    val facing: Vec3 = Vec3(),

    /**
     * Sets the flag if the entity blocks the line of sight.
     */
    val isSightBlocking: Boolean = false
) : Component {

  val position: Vec3
    get() = shape.anchor
}