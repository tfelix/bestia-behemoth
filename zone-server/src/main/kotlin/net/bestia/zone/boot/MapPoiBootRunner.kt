package net.bestia.zone.boot

import net.bestia.zone.cartography.poi.MapPoiIndex
import net.bestia.zone.ecs.core.EcsWorld
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.prop.WorldObjectDivergenceRegistry
import net.bestia.zone.script.ecs.ScriptComponent
import net.bestia.zone.script.persistence.ScriptEntityPersister
import net.bestia.zone.world.WorldService
import org.springframework.boot.CommandLineRunner
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

/**
 * Tells the map which of its services play has already taken away, and keeps it told.
 *
 * After [WorldObjectDivergenceBootRunner] (`@Order(3)`) for the destroyed buildings and after
 * [WardStoneBootRunner] (`@Order(112)`) for the stones, which are only stones once that runner has raised them.
 * Both reads are allowed off the tick only because the tick has not started.
 */
@Component
@Order(113)
class MapPoiBootRunner(
  private val world: EcsWorld,
  private val index: MapPoiIndex,
  private val divergence: WorldObjectDivergenceRegistry,
  private val worldService: WorldService,
) : CommandLineRunner {

  override fun run(vararg args: String?) {
    index.seedDestroyed(divergence.all())

    val metres = worldService.config.voxelSize
    world.each(ScriptComponent::class) { id, script ->
      if (script.scriptId != ScriptEntityPersister.SPAWN_POINT_SCRIPT_ID) return@each
      val position = world.get(id, Position::class) ?: return@each

      index.onWardStoneRaised(id, position.x * metres, position.y * metres)
    }

    world.onDestroy(index::onWardStoneDestroyed)
  }
}
