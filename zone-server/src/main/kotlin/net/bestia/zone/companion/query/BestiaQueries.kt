package net.bestia.zone.companion.query

import net.bestia.bus.contract.ZoneAnswer
import net.bestia.zone.account.persistence.PlayerBestiaRepository
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.movement.ecs.Speed
import net.bestia.zone.travel.Journey
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * A master's bestias. Unlike their master they stay in the world when the player leaves, so most of them are read
 * live; only one the zone has not respawned since a restart comes from its row.
 */
@Service
class BestiaQueries(
  private val playerBestias: PlayerBestiaRepository,
  private val owned: OwnedMasters,
  private val world: WorldView,
  private val plane: MapPlane,
  private val activities: BestiaActivities,
) {

  @Transactional(readOnly = true)
  fun roster(accountId: Long, masterId: Long): ZoneAnswer.Roster? {
    owned.find(accountId, masterId) ?: return null
    val rows = playerBestias.findAllByMasterId(masterId).sortedBy { it.id }

    val entityIds = rows.mapNotNull { it.entityId }
    val live: Map<Long, Vec3L> = world.read {
      entityIds.mapNotNull { id -> get(id, Position::class)?.let { id to it.toVec3L() } }.toMap()
    }
    val travelling: Map<Long, ZoneAnswer.Journey> = world.read {
      entityIds.mapNotNull { id -> get(id, Journey::class)?.let { id to journeyOf(it) } }.toMap()
    }
    val speeds: Map<Long, Float> = world.read {
      entityIds.mapNotNull { id -> get(id, Speed::class)?.let { id to it.speed } }.toMap()
    }
    val jobs = rows.filter { it.entityId != null }.associate { it.entityId!! to it.aiConfig.job }
    val doing: Map<Long, ZoneAnswer.Roster.Activity> = world.read {
      jobs.mapNotNull { (id, job) -> activities.of(this, id, job)?.let { id to it } }.toMap()
    }
    val now = System.currentTimeMillis()

    return ZoneAnswer.Roster(
      rows.map { row ->
        val here = row.entityId?.let { live[it] }
        val position = here ?: row.position

        ZoneAnswer.Roster.Bestia(
          id = row.id,
          name = row.name,
          speciesId = row.bestia.id,
          species = row.bestia.identifier,
          level = row.level,
          job = row.aiConfig.job.name,
          courage = row.aiConfig.courage.name,
          inWorld = here != null,
          eastMetres = plane.eastMetres(position),
          northMetres = plane.northMetres(position),
          asOfEpochMs = if (here != null) now else row.persistedAtEpochMs,
          // One at rest walks at the speed it spawns with.
          metresPerSecond = plane.metresPerSecond(row.entityId?.let { speeds[it] } ?: Speed().speed),
          journey = row.entityId?.let { travelling[it] },
          fallbackJob = row.aiConfig.fallbackJob?.name,
          overrides = OrdersOnTheBus.numbersOf(row.aiConfig.overrides),
          rules = OrdersOnTheBus.linesOf(row.aiConfig.rules),
          areaPlaceId = row.aiConfig.areaPlaceId,
          chestPlaceId = row.aiConfig.chestPlaceId,
          presetId = row.orderPresetId,
          ownOverrides = OrdersOnTheBus.numbersOf(row.ownOverrides),
          activity = row.entityId?.let { doing[it] },
        )
      }
    )
  }

  private fun journeyOf(journey: Journey): ZoneAnswer.Journey {
    return ZoneAnswer.Journey(
      name = journey.name,
      waypoints = journey.waypoints.map { plane.pointOf(it) },
      nextIndex = journey.nextIndex,
      route = journey.route.map { plane.pointOf(it) },
    )
  }
}
