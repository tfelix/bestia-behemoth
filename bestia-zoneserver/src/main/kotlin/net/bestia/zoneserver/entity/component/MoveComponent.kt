package net.bestia.zoneserver.entity.component

import net.bestia.model.geometry.Point
import java.util.LinkedList
import java.util.Queue

/**
 * If this component is added to an entity it will start moving along the path
 * saved into this component. If the path is completely resolved the component
 * is removed.
 *
 * @author Thomas Felix
 */
data class MoveComponent(
        override val id: Long,
        override val entityId: Long,
        val path: Queue<Point> = LinkedList()
) : Component {

  fun setPath(path: Collection<Point>) {
    this.path.clear()
    this.path.addAll(path)
  }
}
