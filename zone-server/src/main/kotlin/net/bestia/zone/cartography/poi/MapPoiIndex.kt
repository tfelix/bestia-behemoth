package net.bestia.zone.cartography.poi

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.worldgen.vector.Aabb
import net.bestia.zone.cartography.render.MapService
import net.bestia.zone.prop.DivergenceEntry
import net.bestia.zone.prop.WorldObjectDivergenceRegistry
import net.bestia.zone.util.EntityId
import net.bestia.zone.world.WorldService
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Which points a player can use stand in the world *right now*, readable from the map's HTTP threads.
 *
 * ### Two layers, because only one of them moves
 *
 * The **candidates** are the generator's - see [ServiceCandidates] - and are recomputed per request from the
 * frozen feature store, exactly as `MapTileService.placesIn` recomputes its places.
 *
 * The **overlay** is what play has done since: buildings knocked down, and the ward stones still standing. A
 * service whose host building is destroyed is gone from every map at once - the map shows the world as it is,
 * not as it was when somebody charted it - and that is why these are not baked into tiles or into the places
 * list, both of which are cached `immutable`.
 *
 * ### Mirrors, not reads
 *
 * [WorldObjectDivergenceRegistry] and the ECS are tick-thread only, and this is read from servlet threads. So
 * nothing here reads either of them after boot: [onDepleted] and [onWardStoneDestroyed] are called on the tick
 * and *copy* the fact into concurrent collections, which is the "mark and return" the registry's listener
 * contract asks for.
 *
 * ### One revision for the whole world
 *
 * [revision] moves whenever the overlay does, and the services ETag carries it. A building falling therefore
 * makes every client re-download the services of every tile it is looking at, rather than of the one tile the
 * building stood on. That is deliberate: a building's prop id does not carry its position in any form this
 * side can decode without restating `PropId`'s packing, buildings fall rarely, and the cost of a world-wide
 * revalidation is a few small JSON bodies per player. Per-area revisions are the fix if that ever stops being
 * true.
 */
@Service
class MapPoiIndex(
  private val worldService: WorldService,
  private val staffed: StaffedTrades,
  divergence: WorldObjectDivergenceRegistry,
) {

  /** Built on first use: the world is not generated until `WorldService.load`. */
  private val candidates: ServiceCandidates by lazy {
    ServiceCandidates(worldService.generated.world, staffed.hoursByTrade())
  }

  private val destroyedBuildings: MutableSet<Long> = ConcurrentHashMap.newKeySet()

  private val wardStones = ConcurrentHashMap<EntityId, MapPoi>()

  private val changes = AtomicLong()

  init {
    divergence.onDepleted(::onDepleted)
  }

  /** Changes whenever anything this index answers has. An ETag ingredient, not a count of anything. */
  val revision: Long
    get() = changes.get()

  /** Every point standing inside [bounds]. Any thread. */
  fun liveIn(bounds: Aabb): List<MapPoi> {
    val live = candidates.within(bounds).filterTo(ArrayList()) { it.id !in destroyedBuildings }

    for (stone in wardStones.values) {
      if (ServiceCandidates.inside(bounds, stone.x, stone.y)) live.add(stone)
    }

    return live
  }

  /**
   * Seeds the buildings destroyed before this run. At boot, after the divergence rows are loaded and before
   * the tick starts - the one time reading the registry off the tick is allowed.
   */
  fun seedDestroyed(entries: Map<Long, DivergenceEntry>) {
    entries.forEach { (propId, entry) -> if (isDestroyedBuilding(entry)) destroyedBuildings.add(propId) }
    changes.incrementAndGet()

    LOG.info { "Map services: ${destroyedBuildings.size} host building(s) already destroyed" }
  }

  /** A ward stone stands at ([x], [y]) metres. At boot, or on the tick should one ever be raised later. */
  fun onWardStoneRaised(id: EntityId, x: Double, y: Double) {
    wardStones[id] = MapPoi(id, MapService.WARD_STONE, null, x, y, settlement = null, openFrom = null, openTo = null)
    changes.incrementAndGet()
  }

  /**
   * Some entity was destroyed. Tick thread, on every destroy in the world, so it is one hash removal and
   * nothing else for the overwhelming majority that were never ward stones.
   */
  fun onWardStoneDestroyed(id: EntityId) {
    if (wardStones.remove(id) != null) changes.incrementAndGet()
  }

  private fun onDepleted(propId: Long, entry: DivergenceEntry) {
    if (!isDestroyedBuilding(entry)) return
    if (destroyedBuildings.add(propId)) changes.incrementAndGet()
  }

  private fun isDestroyedBuilding(entry: DivergenceEntry): Boolean {
    return entry.kind.isBuilding && entry.resumeAt == null
  }

  private companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
