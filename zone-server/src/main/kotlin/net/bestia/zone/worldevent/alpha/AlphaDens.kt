package net.bestia.zone.worldevent.alpha

import net.bestia.worldgen.vector.Vec2d
import net.bestia.zone.ai.profile.AiProfileRegistry
import net.bestia.zone.bestia.BestiaCatalogue
import net.bestia.zone.bestia.persistence.Bestia
import net.bestia.zone.spawn.ecs.WildSpawnerService
import net.bestia.zone.world.SettlementLoreService
import net.bestia.zone.world.WorldRecreatedEvent
import net.bestia.zone.world.WorldService
import net.bestia.zone.worldevent.nearestRoad
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import kotlin.math.hypot

/**
 * The dens that can grow an alpha: a hunting species whose den lies near a road or a town, where its alpha meets
 * travellers. Each comes with the spot the alpha prowls, the nearest point of the road, or else a point on the way
 * to the town. Worked out once per world, since dens, roads and towns do not move.
 */
@Component
class AlphaDens(
  private val dens: WildSpawnerService,
  private val bestiaCatalogue: BestiaCatalogue,
  private val aiProfiles: AiProfileRegistry,
  private val worldService: WorldService,
) {

  /** A den by its feature id, where it is and where its alpha prowls, in metres. */
  data class AlphaDen(val den: WildSpawnerService.Den, val species: Bestia, val at: Vec2d, val prowl: Vec2d)

  @Volatile
  private var cached: List<AlphaDen>? = null

  val all: List<AlphaDen>
    get() = cached ?: synchronized(this) { cached ?: find().also { cached = it } }

  @EventListener
  fun handleWorldRecreated(event: WorldRecreatedEvent) {
    cached = null
  }

  private fun find(): List<AlphaDen> {
    val generated = worldService.generated
    val voxel = generated.config.voxelSize
    val towns = SettlementLoreService.settlementPositions(generated).values

    return dens.dens.mapNotNull { den ->
      val species = bestiaCatalogue.find(den.bestiaId)?.takeIf(::hunts) ?: return@mapNotNull null
      val at = Vec2d(den.position.x * voxel, den.position.y * voxel)
      val prowl = nearestRoad(generated, at, ROAD_REACH) ?: towardTown(at, towns) ?: return@mapNotNull null
      AlphaDen(den, species, at, prowl)
    }
  }

  /** A beast that attacks on sight, and an ordinary one: bosses and event creatures have no pack to lead. */
  private fun hunts(species: Bestia): Boolean {
    if (species.boss || species.worldBoss || species.corruptedOnly || species.eventOnly) return false
    val profile = species.aiProfile?.let(aiProfiles::get) ?: return false
    return profile.goals.any { it.name == KILL_ENEMY_GOAL }
  }

  private fun towardTown(at: Vec2d, towns: Collection<Vec2d>): Vec2d? {
    val town = towns.filter { hypot(it.x - at.x, it.y - at.y) <= TOWN_REACH }
      .minByOrNull { hypot(it.x - at.x, it.y - at.y) } ?: return null
    return Vec2d(at.x + (town.x - at.x) * TOWARD_TOWN, at.y + (town.y - at.y) * TOWARD_TOWN)
  }

  private companion object {
    const val KILL_ENEMY_GOAL = "KillEnemy"

    const val ROAD_REACH = 400.0
    const val TOWN_REACH = 1_000.0

    /** How far from its den toward the town the alpha prowls: out on the fields, not in the streets. */
    const val TOWARD_TOWN = 0.6
  }
}
