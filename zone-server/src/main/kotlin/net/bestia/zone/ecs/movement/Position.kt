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
import net.bestia.zone.message.entity.PositionSMSG

data class Position(
  private var _x: Long,
  private var _y: Long,
  private var _z: Long,
  var fraction: Float = 0f
) : Component<Position>, Dirtyable {

  private var dirty: Boolean = true

  var x: Long
    get() = _x
    set(value) {
      if (_x != value) {
        _x = value
        dirty = true
      }
    }

  var y: Long
    get() = _y
    set(value) {
      if (_y != value) {
        _y = value
        dirty = true
      }
    }

  var z: Long
    get() = _z
    set(value) {
      if (_z != value) {
        _z = value
        dirty = true
      }
    }

  class PositionAcessor(
    private val entity: Entity
  ) : WorldAcessor {

    var position: Vec3L = Vec3L.ZERO
      private set

    override fun doWithWorld(world: World) {
      val comp = with(world) {
        entity.getOrNull(Position)
          ?: throw ComponentNotFoundException(Position)
      }

      position = comp.toVec3L()
    }
  }

  override fun type(): ComponentType<Position> = Position

  fun toVec3L(): Vec3L {
    return Vec3L(x, y, z)
  }

  override fun isDirty(): Boolean {
    return dirty
  }

  override fun clearDirty() {
    dirty = false
  }

  override fun toEntityMessage(entityId: Long): EntitySMSG {
    return PositionSMSG(
      entityId = entityId,
      position = Vec3L(x, y, z)
    )
  }

  companion object : ComponentType<Position>() {
    fun fromVec3(pos: Vec3L): Position {
      return Position(
        pos.x,
        pos.y,
        pos.z
      )
    }
  }
}