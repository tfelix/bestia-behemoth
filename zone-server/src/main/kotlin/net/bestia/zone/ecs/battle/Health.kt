package net.bestia.zone.ecs.battle

import com.github.quillraven.fleks.Component
import com.github.quillraven.fleks.ComponentType
import com.github.quillraven.fleks.Entity
import com.github.quillraven.fleks.World
import net.bestia.zone.status.CurMax
import net.bestia.zone.ecs.ComponentNotFoundException
import net.bestia.zone.ecs.WorldAcessor

class Health(
  current: Int,
  max: Int
) : Component<Health> {

  private val data = CurMax().apply {
    this.max = max
    this.current = current
  }

  var current
    get() = data.current
    set(value) {
      data.current = value
    }

  var max
    get() = data.max
    set(value) {
      data.max = value
    }

  class HealthAcessor(
    private val entity: Entity
  ) : WorldAcessor {

    var health: CurMax = CurMax()
      private set

    override fun doWithWorld(world: World) {
      val comp = with(world) {
        entity.getOrNull(Health)
          ?: throw ComponentNotFoundException(Health)
      }

      health = comp.data.copy()
    }
  }

  override fun type() = Health

  companion object : ComponentType<Health>()
}