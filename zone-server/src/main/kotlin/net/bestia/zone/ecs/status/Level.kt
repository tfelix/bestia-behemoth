package net.bestia.zone.ecs.status

import com.github.quillraven.fleks.Component
import com.github.quillraven.fleks.ComponentType
import com.github.quillraven.fleks.Entity
import com.github.quillraven.fleks.World
import net.bestia.zone.ecs.ComponentNotFoundException
import net.bestia.zone.ecs.WorldAcessor

data class Level(
  val level: Int,
) : Component<Level> {

  override fun type(): ComponentType<Level> = Level

  class LevelAcessor(
    private val entity: Entity
  ) : WorldAcessor {

    var level: Int = 0
      private set

    override fun doWithWorld(world: World) {
      val comp = with(world) {
        entity.getOrNull(Level)
          ?: throw ComponentNotFoundException(Level)
      }

      level = comp.level
    }
  }

  companion object : ComponentType<Level>()
}