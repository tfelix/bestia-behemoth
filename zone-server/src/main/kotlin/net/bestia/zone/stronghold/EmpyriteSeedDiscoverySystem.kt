package net.bestia.zone.stronghold

import net.bestia.zone.casting.sense.SenseKind
import net.bestia.zone.casting.sense.SenseMark
import net.bestia.zone.casting.sense.SenseMarksSMSG
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.Phase
import net.bestia.zone.ecs.core.Schedule
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.World
import net.bestia.zone.identity.ecs.Account
import net.bestia.zone.identity.ecs.Master
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.world.WorldService
import net.bestia.zone.world.stream.ChunkStreamSystem
import kotlin.math.hypot
import org.springframework.stereotype.Component as SpringComponent

/**
 * A master who walks onto the core of an awake Empyrite Seed discovers it and is told its name.
 *
 * Once a second rather than every tick: a core is ten metres across, and nobody crosses it in a second.
 */
@SpringComponent
class EmpyriteSeedDiscoverySystem(
  private val seeds: EmpyriteSeedRegistry,
  private val discoveries: EmpyriteSeedDiscoveries,
  private val worldService: WorldService,
  private val messages: OutMessageProcessor,
  private val config: StrongholdConfig,
) : System {

  override val phase = Phase.WORLD
  override val schedule: Schedule = Schedule.EverySeconds(1f)
  override val after = setOf(ChunkStreamSystem::class)

  override val reads: ComponentClassSet = setOf(Master::class, Account::class, Position::class)
  override val writes: ComponentClassSet = emptySet()

  override fun update(world: World, deltaTime: Float) {
    val awake = seeds.awake()
    if (awake.isEmpty()) return
    val voxel = worldService.config.voxelSize

    world.query(Master::class, Account::class, Position::class).each {
      val position = get<Position>()
      val east = position.x * voxel
      val north = position.y * voxel
      val seed = awake.firstOrNull { hypot(it.x - east, it.y - north) <= config.coreRadiusMetres } ?: return@each

      if (discoveries.discover(get<Master>().masterId, seed.rank)) {
        val found = SenseMark(
          SenseKind.EMPYRITE_SEED_FOUND, seed.x, seed.y, label = seed.name, refId = seed.rank.toLong()
        )
        messages.sendToPlayer(get<Account>().accountId, SenseMarksSMSG(listOf(found), FOUND_VISIBLE_SECONDS))
      }
    }
  }

  private companion object {
    const val FOUND_VISIBLE_SECONDS = 60
  }
}
