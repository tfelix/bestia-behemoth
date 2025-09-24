package net.bestia.zone.ecs.movement

import com.github.quillraven.fleks.Component
import com.github.quillraven.fleks.ComponentType
import com.github.quillraven.fleks.Entity
import com.github.quillraven.fleks.World
import net.bestia.zone.ecs.ComponentNotFoundException
import net.bestia.zone.ecs.Dirtyable
import net.bestia.zone.ecs.WorldAcessor
import net.bestia.zone.message.entity.EntitySMSG
import net.bestia.zone.message.entity.SpeedSMSG

data class Speed(
  private var _speed: Float = 1.0f
) : Component<Speed>, Dirtyable {

  private var dirty: Boolean = true

  var speed: Float
    get() = _speed
    set(value) {
      if (_speed != value) {
        _speed = value
        dirty = true
      }
    }

  class SpeedAcessor(
    private val entity: Entity
  ) : WorldAcessor {

    var speed: Float = 0.0f
      private set

    override fun doWithWorld(world: World) {
      val comp = with(world) {
        entity.getOrNull(Speed)
          ?: throw ComponentNotFoundException(Speed)
      }

      speed = comp.speed
    }
  }

  override fun type(): ComponentType<Speed> = Speed

  override fun isDirty(): Boolean {
    return dirty
  }

  override fun clearDirty() {
    dirty = false
  }

  override fun toEntityMessage(entityId: Long): EntitySMSG {
    return SpeedSMSG(
      entityId = entityId,
      speed = speed
    )
  }

  companion object : ComponentType<Speed>()
}
