package net.bestia.zone.stronghold.ecs

import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.Phase
import net.bestia.zone.ecs.core.Schedule
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.World
import net.bestia.zone.entity.ecs.ClosedToAttack
import net.bestia.zone.weather.EnvironmentalExposureSystem
import net.bestia.zone.identity.ecs.GuildOwned
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.stronghold.AttackWindows
import net.bestia.zone.stronghold.EmpyriteSeedRegistry
import net.bestia.zone.stronghold.StrongholdConfig
import net.bestia.zone.stronghold.StrongholdRegistry
import net.bestia.zone.world.WorldService
import java.time.Clock
import kotlin.math.hypot
import org.springframework.stereotype.Component as SpringComponent

/**
 * Closes every guild structure on a Stronghold's grounds to attack while no attack window is open, and opens them
 * again when one is. A War Banner away from all grounds is never closed.
 *
 * Every ten seconds: a window opens on the minute, and nobody expects a siege to start on the tick.
 */
@SpringComponent
class AttackWindowSystem(
  private val strongholds: StrongholdRegistry,
  private val seeds: EmpyriteSeedRegistry,
  private val worldService: WorldService,
  private val config: StrongholdConfig,
  private val clock: Clock = Clock.systemUTC(),
) : System {

  override val phase = Phase.UPKEEP
  override val schedule: Schedule = Schedule.EverySeconds(10f)
  override val after = setOf(EnvironmentalExposureSystem::class)

  override val reads: ComponentClassSet = setOf(GuildOwned::class, Position::class)
  override val writes: ComponentClassSet = setOf(ClosedToAttack::class)

  private val windows = AttackWindows.of(config)

  override fun update(world: World, deltaTime: Float) {
    val isOpen = windows.isOpen(clock.instant())
    val heldSeeds = seeds.awake().filter { strongholds.on(it.rank) != null }
    val voxel = worldService.config.voxelSize

    world.query(GuildOwned::class, Position::class).each { id ->
      val at = get<Position>()
      val isOnGrounds = heldSeeds.any { hypot(it.x - at.x * voxel, it.y - at.y * voxel) <= config.groundsRadiusMetres }
      val isClosed = world.has(id, ClosedToAttack::class)

      when {
        !isOpen && isOnGrounds && !isClosed -> world.add(id, ClosedToAttack)
        (isOpen || !isOnGrounds) && isClosed -> world.remove(id, ClosedToAttack::class)
      }
    }
  }
}
