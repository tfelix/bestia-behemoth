package net.bestia.zone.compendium.ecs

import net.bestia.zone.aoi.AoiLayer
import net.bestia.zone.aoi.EntityAOIService
import net.bestia.zone.bestia.WildBestia
import net.bestia.zone.compendium.CompendiumRecorder
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.Phase
import net.bestia.zone.ecs.core.Schedule
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.World
import net.bestia.zone.entity.ecs.EntityVisual
import net.bestia.zone.identity.ecs.Account
import net.bestia.zone.identity.ecs.Master
import net.bestia.zone.identity.ecs.OwnedBestia
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.util.AccountId
import net.bestia.zone.weather.EnvironmentalExposureSystem
import org.springframework.stereotype.Component as SpringComponent

/**
 * A master and each of its bestias look around once a second; every wild species within [SIGHT_VOXELS] goes
 * into the master's compendium.
 *
 * Its own short range rather than the chunk interest range, which is several times wider than what a player
 * can actually make out on screen.
 */
@SpringComponent
class CompendiumSightingSystem(
  private val aoi: EntityAOIService,
  private val recorder: CompendiumRecorder,
) : System {

  override val phase = Phase.UPKEEP

  /** A creature that froze to death this tick is a body, not a sighting. */
  override val after = setOf(EnvironmentalExposureSystem::class)
  override val schedule: Schedule = Schedule.EverySeconds(1f)

  override val reads: ComponentClassSet =
    setOf(Master::class, OwnedBestia::class, Position::class) + WildBestia.READS
  override val writes: ComponentClassSet = emptySet()

  override fun update(world: World, deltaTime: Float) {
    world.query(Master::class, Account::class, Position::class).each {
      lookAround(world, get<Account>().accountId, get<Master>().masterId, get<Position>())
    }
    world.query(OwnedBestia::class, Account::class, Position::class).each {
      lookAround(world, get<Account>().accountId, get<OwnedBestia>().masterId, get<Position>())
    }
  }

  private fun lookAround(world: World, accountId: AccountId, masterId: Long, from: Position) {
    aoi.forEachInCube(from.toVec3L(), SIGHT_VOXELS * 2, AoiLayer.DYNAMIC_ONLY) { id, _, _, _ ->
      if (WildBestia.isWild(world, id)) {
        recorder.sighted(accountId, masterId, world.getOrThrow(id, EntityVisual::class).id)
      }
    }
  }

  companion object {
    /** About the distance a creature is still clearly visible at the default camera zoom. Voxels are 1 m. */
    const val SIGHT_VOXELS = 24L
  }
}
