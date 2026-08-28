package net.bestia.zone.boot

import net.bestia.zone.ecs.place.AreaNameRegistry
import net.bestia.zone.world.WorldService
import org.springframework.boot.CommandLineRunner
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

/**
 * Loads the generated settlements into the place index before anything can ask where it is.
 *
 * ### Why at boot rather than on first use
 *
 * The index is a plain `HashMap`, mutated from the tick thread and read from it. Filling it lazily on the
 * first lookup would put a writer on whichever thread logged in first while the tick thread was reading -
 * the data race `AreaOfInterestService`'s own KDoc records having shipped, where a reader can hang inside
 * `HashMap.get` during a resize. `PlayerStructureRegistry` avoids it exactly this way and says so.
 *
 * Cheap enough to do unconditionally: one pass over the feature store, no Dijkstra. The region *partition* -
 * which does cost one - stays lazy in
 * [PlaceRegionService][net.bestia.zone.ecs.place.PlaceRegionService], because a server nobody joins should not
 * pay for it.
 *
 * `@Order(6)` puts it after [ScorchBootRunner], which documents itself as the end of the "things about the
 * world" group. Nothing here depends on scorch; the number is just the next free slot in the group this
 * belongs to. It needs the world, so it must stay after [WorldGenerationBootRunner] (`@Order(1)`).
 */
@Component
@Order(6)
class PlaceIndexBootRunner(
  private val worldService: WorldService,
  private val registry: AreaNameRegistry
) : CommandLineRunner {

  override fun run(vararg args: String?) {
    registry.loadSettlements(worldService.generated.world)
  }
}
