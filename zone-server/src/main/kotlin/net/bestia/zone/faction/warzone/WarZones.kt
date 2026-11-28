package net.bestia.zone.faction.warzone

import net.bestia.zone.faction.FactionConfig
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.honor.WarZoneMap
import net.bestia.zone.world.WorldService
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration
import java.time.Instant

/**
 * The open war zones. Kept in memory only: a zone closes half an hour after its last fight, so a restart loses
 * little. Tick thread only.
 */
@Component
class WarZones(
  private val config: FactionConfig,
  private val worldService: WorldService,
  private val clock: Clock = Clock.systemUTC(),
) : WarZoneMap {

  /** [centre] and [radius] in voxels. */
  data class WarZone(val centre: Vec3L, val radius: Long, val closesAt: Instant)

  private class Kill(val at: Vec3L, val time: Instant)

  private val zones = mutableListOf<WarZone>()
  private val recentKills = ArrayDeque<Kill>()

  private var changes: Long = 0

  /** Rises whenever a zone opens or closes, so the maps are only sent again then. */
  val version: Long
    get() {
      return changes
    }

  val open: List<WarZone>
    get() {
      return zones.filter { it.closesAt.isAfter(clock.instant()) }
    }

  override fun covers(at: Vec3L): Boolean {
    return open.any { it.covers(at) }
  }

  override fun rivalKill(at: Vec3L) {
    val now = clock.instant()
    val zoneIndex = zones.indexOfFirst { it.closesAt.isAfter(now) && it.covers(at) }
    if (zoneIndex >= 0) {
      zones[zoneIndex] = zones[zoneIndex].copy(closesAt = now.plus(linger))
      return
    }

    recentKills.removeIf { !it.time.plus(killWindow).isAfter(now) }
    recentKills.add(Kill(at, now))
    val killArea = voxels(config.warZone.killAreaMetres)
    val nearby = recentKills.filter { it.at.distance(at) <= killArea }
    if (nearby.size >= config.warZone.kills) {
      zones.add(WarZone(centreOf(nearby), voxels(config.warZone.radiusMetres), now.plus(linger)))
      recentKills.removeAll(nearby.toSet())
      changes++
    }
  }

  fun closeExpired() {
    val now = clock.instant()
    if (zones.removeIf { !it.closesAt.isAfter(now) }) {
      changes++
    }
  }

  /** The metres per voxel, for whoever draws the zones on a map. */
  val voxelSize: Double
    get() {
      return worldService.config.voxelSize
    }

  private val linger: Duration
    get() {
      return Duration.ofMinutes(config.warZone.lingerMinutes)
    }

  private val killWindow: Duration
    get() {
      return Duration.ofMinutes(config.warZone.killWindowMinutes)
    }

  private fun voxels(metres: Double): Long {
    return (metres / voxelSize).toLong()
  }

  private fun centreOf(kills: List<Kill>): Vec3L {
    return Vec3L(kills.sumOf { it.at.x } / kills.size, kills.sumOf { it.at.y } / kills.size, kills.sumOf { it.at.z } / kills.size)
  }

  private fun WarZone.covers(at: Vec3L): Boolean {
    return centre.distance(at) <= radius
  }
}
