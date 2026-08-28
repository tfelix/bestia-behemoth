package net.bestia.zone.ecs.place

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.worldgen.place.PlaceRegions
import net.bestia.zone.world.WorldRecreatedEvent
import net.bestia.zone.world.WorldService
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service

/**
 * The world's region partition, built once per world and held in memory.
 *
 * `WildSpawnerService.dens by lazy` is the precedent and `world/SettlementLoreService`'s KDoc is the
 * argument verbatim: a durable copy "would be a table that can go stale against the generator that
 * produced it, in exchange for nothing". The partition is a pure function of the world, the world is
 * rebuilt every boot, so a table buys nothing and can lie.
 *
 * Built on first use rather than at boot. A Dijkstra over every cell in the world is not something to do
 * before the socket opens if nothing has asked yet, and the first thing to ask is the first player to
 * spawn.
 *
 * `WorldProvisioning.recreate` can swap the world under a running server, and a partition built against
 * the old one describes ground that no longer exists - every name would be subtly wrong rather than
 * obviously broken. `SettlementLoreService` names this trap for a table; the same applies to a field.
 */
@Service
class PlaceRegionService(
  private val worldService: WorldService,
  private val registry: AreaNameRegistry
) {

  @Volatile
  private var cached: PlaceRegions? = null

  val regions: PlaceRegions
    get() {
      cached?.let { return it }

      synchronized(this) {
        cached?.let { return it }

        val built = PlaceRegions.of(worldService.generated.world)
        LOG.info {
          "Place partition: ${built.count} region(s), ${built.landCount} with land, " +
              "${built.cellSize.toInt()} m cells"
        }

        cached = built
        return built
      }
    }

  /**
   * Rebuilds the index and drops the partition when the world underneath is replaced.
   *
   * Reloaded here rather than left empty for the next boot, because `WorldProvisioning.recreate` does not
   * restart the process: a server that lost its settlement names until somebody restarted it would look
   * like the feature had broken. Safe on this thread for the same reason
   * [net.bestia.zone.boot.PlaceIndexBootRunner] is - the event is published from the boot path, before the
   * tick loop is reading.
   */
  @EventListener
  fun handleWorldRecreated(event: WorldRecreatedEvent) {
    cached = null
    registry.clear()
    registry.loadSettlements(worldService.generated.world)
    LOG.info { "World replaced; rebuilt the place index and dropped the partition" }
  }

  private companion object {
    val LOG = KotlinLogging.logger { }
  }
}
