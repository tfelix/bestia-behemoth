package net.bestia.zone.ecs.visual

import com.github.quillraven.fleks.Component
import com.github.quillraven.fleks.ComponentType
import com.github.quillraven.fleks.Entity
import com.github.quillraven.fleks.World
import net.bestia.zone.ecs.ComponentNotFoundException
import net.bestia.zone.ecs.WorldAcessor

data class BestiaVisual(
  val id: Int,
) : Component<BestiaVisual> {

  override fun type(): ComponentType<BestiaVisual> = BestiaVisual

  class BestiaVisualAcessor(
    private val entity: Entity
  ) : WorldAcessor {

    var id: Int = 0
      private set

    override fun doWithWorld(world: World) {
      val comp = with(world) {
        entity.getOrNull(BestiaVisual)
          ?: throw ComponentNotFoundException(BestiaVisual)
      }

      id = comp.id
    }
  }

  companion object : ComponentType<BestiaVisual>()
}