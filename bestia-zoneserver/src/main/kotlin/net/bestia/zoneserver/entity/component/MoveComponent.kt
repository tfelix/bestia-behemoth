package net.bestia.zoneserver.entity.component

import net.bestia.model.geometry.Vec3

/**
 * If this component is added to an entity it will start moving along the path
 * saved into this component. If the path is completely resolved the component
 * is removed.
 *
 * FIXME Rename to speed component
 *
 * @author Thomas Felix
 */
data class MoveComponent(
    override val entityId: Long,
    val speed: Vec3
) : Component
