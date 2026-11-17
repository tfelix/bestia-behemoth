package net.bestia.zone.poi

import net.bestia.zone.battle.ecs.damage.PlayerDeathSystem
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.Phase
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.World
import net.bestia.zone.entity.ecs.Dead
import net.bestia.zone.entity.ecs.Poi
import org.springframework.stereotype.Component as SpringComponent

/**
 * Records a POI that has been destroyed. Between `ReceivedDamageSystem` adding [Dead] and `DeathSystem`
 * removing the entity, the window `PlayerStructureDeathSystem` uses too.
 */
@SpringComponent
class PoiDeathSystem(
  private val pois: PoiService,
) : System {
  override val phase = Phase.DEATH
  override val before = setOf(PlayerDeathSystem::class)

  override val reads: ComponentClassSet = setOf(Dead::class, Poi::class)

  // Empty: the service keeps its own set and the row is written off the tick.
  override val writes: ComponentClassSet = emptySet()

  override fun update(world: World, deltaTime: Float) {
    world.query(Dead::class, Poi::class).each { id ->
      pois.destroyed(world, id)
    }
  }
}
