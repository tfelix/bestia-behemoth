package net.bestia.zone.ecs.movement

import com.github.quillraven.fleks.Component
import com.github.quillraven.fleks.ComponentType
import com.github.quillraven.fleks.Entity
import com.github.quillraven.fleks.World
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.ecs.ComponentNotFoundException
import net.bestia.zone.ecs.Dirtyable
import net.bestia.zone.ecs.WorldAcessor
import net.bestia.zone.message.entity.EntitySMSG
import net.bestia.zone.message.entity.PathSMSG

data class Path(
  private var _path: MutableList<Vec3L>
) : Component<Path>, Dirtyable {

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

  class PathAcessor(
    private val entity: Entity
  ) : WorldAcessor {

    var path: List<Vec3L> = emptyList()
      private set

    override fun doWithWorld(world: World) {
      val comp = with(world) {
        entity.getOrNull(Path)
          ?: throw ComponentNotFoundException(Path)
      }

      path = comp.path
    }
  }

  override fun type(): ComponentType<Path> = Path

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

  companion object : ComponentType<Path>()
}
