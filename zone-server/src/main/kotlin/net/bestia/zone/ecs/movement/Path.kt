package net.bestia.zone.ecs.movement

import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.ecs2.Component
import net.bestia.zone.ecs2.Dirtyable
import net.bestia.zone.message.entity.EntitySMSG
import net.bestia.zone.message.entity.PathSMSG

data class Path(
  private var _path: MutableList<Vec3L>
) : Component, Dirtyable {

  init {
    require(_path.isNotEmpty()) { "Path must not be empty on creation." }
  }

  private var dirty: Boolean = true

  val path: List<Vec3L>
    get() = _path.toList()

  fun removeFirst(): Vec3L {
    dirty = true

    return _path.removeFirst()
  }

  fun setPath(newPath: List<Vec3L>) {
    if (_path != newPath) {
      _path.clear()
      _path.addAll(newPath)
      dirty = true
    }
  }

  fun addPathPoint(point: Vec3L) {
    _path.add(point)
    dirty = true
  }

  fun clearPath() {
    if (_path.isNotEmpty()) {
      _path.clear()
      dirty = true
    }
  }

  override fun isDirty(): Boolean {
    return dirty
  }

  override fun clearDirty() {
    dirty = false
  }

  override fun toEntityMessage(entityId: Long): EntitySMSG {
    return PathSMSG(
      entityId = entityId,
      path = path
    )
  }
}
