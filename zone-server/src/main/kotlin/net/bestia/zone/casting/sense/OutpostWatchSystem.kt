package net.bestia.zone.casting.sense

import net.bestia.zone.bestia.diary.BestiaHappened
import net.bestia.zone.bestia.diary.BestiaNews
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.Phase
import net.bestia.zone.ecs.core.Schedule
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.World
import net.bestia.zone.entity.StaticEntityKind
import net.bestia.zone.identity.ecs.OwnedBestia
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.prop.PlayerStructureRegistry
import net.bestia.zone.util.EntityId
import net.bestia.zone.world.WorldService
import org.springframework.stereotype.Component
import kotlin.math.hypot

/**
 * A bestia left at an Observation Outpost keeps watch: when a world event begins to stir in range, it sends word to
 * its master, in the diary and through the companion app. It watches as far as Observation Lv3 would, whatever its
 * master knows, since its master may not be there to ask. Each event is told once per watcher.
 */
@Component
class OutpostWatchSystem(
  private val structures: PlayerStructureRegistry,
  private val worldService: WorldService,
  private val news: BestiaNews,
  private val brewing: BrewingEvents? = null,
) : System {

  override val phase = Phase.UPKEEP

  override val schedule: Schedule = Schedule.EverySeconds(60f)

  override val reads: ComponentClassSet = setOf(OwnedBestia::class, Position::class)

  override val writes: ComponentClassSet = emptySet()

  /** The events each watcher already told of, by where they run. */
  private val told = HashMap<EntityId, MutableSet<String>>()

  override fun update(world: World, deltaTime: Float) {
    val events = brewing?.brewing().orEmpty()
    val voxel = worldService.config.voxelSize
    val watching = HashSet<EntityId>()

    world.query(OwnedBestia::class, Position::class).each { id ->
      val at = get<Position>().toVec3L()
      if (!isAtOutpost(at.x, at.y)) return@each
      watching += id

      val owned = get<OwnedBestia>()
      val known = told.getOrPut(id) { HashSet() }
      events
        .filter { hypot(it.x - at.x * voxel, it.y - at.y * voxel) <= WATCH_METRES * it.feltFrom }
        .filter { known.add("${it.eventId}@${it.x.toLong()},${it.y.toLong()}") }
        .forEach { news.tell(BestiaHappened(this, owned.masterId, owned.playerBestiaId, BestiaHappened.Kind.EVENT_SENSED, reason = it.family)) }
    }

    told.keys.retainAll(watching)
  }

  private fun isAtOutpost(x: Long, y: Long): Boolean {
    val size = worldService.config.chunkSize.toLong()
    val voxel = worldService.config.voxelSize
    val chunkX = Math.floorDiv(x, size).toInt()
    val chunkY = Math.floorDiv(y, size).toInt()

    return (-1..1).any { dx ->
      (-1..1).any { dy ->
        structures.`in`(chunkX + dx, chunkY + dy).any {
          it.kind == StaticEntityKind.OBSERVATION_OUTPOST && !it.isUnderConstruction &&
            hypot((it.position.x - x) * voxel, (it.position.y - y) * voxel) <= WATCH_POST_METRES
        }
      }
    }
  }

  private companion object {
    /** What Observation Lv3 feels around an outpost, before a big event's reach. */
    const val WATCH_METRES = 3_000.0

    /** How near the outpost a bestia must stay to count as keeping watch. */
    const val WATCH_POST_METRES = 20.0
  }
}
