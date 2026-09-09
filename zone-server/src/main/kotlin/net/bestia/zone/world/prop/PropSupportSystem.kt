package net.bestia.zone.world.prop

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.worldgen.core.ChunkPos
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.Schedule
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.movement.GroundHeight
import net.bestia.zone.ecs.prop.PropPose
import net.bestia.zone.ecs.prop.PropVitality
import net.bestia.zone.ecs.prop.StaticSync
import net.bestia.zone.ecs.prop.StaticVisual
import net.bestia.zone.ecs.prop.WorldObjectIdentity
import net.bestia.zone.world.stream.ChunkService
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import net.bestia.zone.ecs.core.System as EcsSystem

/**
 * Destroys a generated prop whose ground has been dug out from under it.
 *
 * A prop's height comes from the generator's own column - `GeneratedPropSource` says so, and says the two
 * agree - and a carve is exactly what breaks that agreement. Nothing used to notice: no prop code listened for
 * a terrain change, so a tree stood in the air over the hole beneath it, and re-materialising its column put it
 * back at the same generated height.
 *
 * ### Terminal, whatever the kind's regrowth says
 *
 * `recordDepletion` is given a null `resumeAt` even for a tree with a `regrowSeconds`, because the ground it
 * would grow back onto is not there. A regrown tree over an open shaft is the same bug with a delay on it.
 *
 * ### Order 47
 *
 * After `ChunkStreamSystem` (45), whose last step rebuilds the walkability tiles this reads - before them the
 * ground still reads as it was, and no prop looks unsupported. After `WorldObjectResidencySystem` (46), so a
 * column materialised this tick is judged this tick rather than next. The prop component types are declared as
 * `writes` because `WorldObjectResidencyService.remove` destroys the entity carrying them, which is also what
 * places this in a later wave than both.
 */
@Component
@Order(47)
class PropSupportSystem(
  private val residency: WorldObjectResidencyService,
  private val divergence: WorldObjectDivergenceRegistry,
  private val ground: GroundHeight,
  private val chunkService: ChunkService
) : EcsSystem {

  override val schedule: Schedule = Schedule.EveryTick

  override val reads: ComponentClassSet = setOf(PropPose::class, StaticVisual::class)

  override val writes: ComponentClassSet = setOf(
    PropPose::class,
    StaticVisual::class,
    PropVitality::class,
    WorldObjectIdentity::class,
    StaticSync::class
  )

  /**
   * Columns an edit has touched whose props have not been re-checked.
   *
   * Filled from a `ChunkService.onChunkChanged` listener, which runs on the tick thread inside the carve and
   * so may do nothing but record and return - the contract that listener list states, and the one
   * `MacroGraphService` already keeps. Survives a tick, because the tiles this reads are rebuilt out of a
   * budget and a wide carve outruns it.
   */
  private val touched = LinkedHashSet<Long>()

  init {
    // Safe at construction, unlike anything that would reach `derived()`: registering only appends to a list,
    // and the lambda cannot fire until an edit happens, by which time there is a world.
    chunkService.onChunkChanged { chunk -> touched.add(columnOf(chunk.x, chunk.y)) }
  }

  override fun update(world: World, deltaTime: Float) {
    if (!chunkService.isReady || touched.isEmpty()) return

    val derived = chunkService.derived()
    val ready = touched.filter { column -> !derived.isStale(ChunkPos(unpackX(column), unpackY(column), 0)) }
    if (ready.isEmpty()) return

    touched.removeAll(ready.toSet())

    for (column in ready) {
      fellUnsupported(world, unpackX(column), unpackY(column))
    }
  }

  private fun fellUnsupported(world: World, chunkX: Int, chunkY: Int) {
    // A copy, because `remove` rewrites the residency array for this column as it goes.
    val resident = residency.entitiesIn(chunkX, chunkY).copyOf()

    for (entityId in resident) {
      val pose = world.get(entityId, PropPose::class) ?: continue
      val visual = world.get(entityId, StaticVisual::class) ?: continue
      val identity = world.get(entityId, WorldObjectIdentity::class) ?: continue

      // A building's walls are voxels and are still standing; undermining a house is a different event from
      // undermining a tree, and not one this system has an answer for.
      if (visual.kind.isBuilding) continue

      // Whichever of the two ways a prop is used up got there first wins, exactly as the collect and death
      // paths guard against each other. A prop felled and undermined in one tick yields once.
      if (divergence.of(identity.propId) != null) continue

      if (isSupported(pose)) continue

      divergence.recordDepletion(identity.propId, visual.kind, resumeAt = null)
      residency.remove(world, entityId)

      LOG.debug {
        "Destroyed ${visual.kind} (prop ${identity.propId}) at ${pose.position}: its ground was carved away"
      }
    }
  }

  /**
   * Whether the ground is still where [pose] was planted.
   *
   * A null answer is treated as supported rather than as gone. The column can be genuinely unavailable - off
   * the grid, or a tile that has not been built - and felling a wood because the answer had not arrived yet is
   * the worse of the two mistakes by a long way: a destroyed prop is durable, and a spared one is re-checked
   * on the next edit.
   *
   * The tolerance is one position unit because `ChunkCoords.standingZ` rounds, so ground and pose can differ
   * by half a voxel with nothing having moved. Only downwards: ground *rising* under a prop is what happens
   * when somebody builds up to it, and a tree with its trunk buried is not a tree that should vanish.
   */
  private fun isSupported(pose: PropPose): Boolean {
    val now = ground.standingZAt(pose.position) ?: return true

    return now >= pose.position.z - SUPPORT_TOLERANCE
  }

  private fun columnOf(chunkX: Int, chunkY: Int): Long {
    return (chunkX.toLong() shl 32) or (chunkY.toLong() and 0xFFFFFFFFL)
  }

  private fun unpackX(column: Long): Int {
    return (column shr 32).toInt()
  }

  private fun unpackY(column: Long): Int {
    return column.toInt()
  }

  private companion object {
    val LOG = KotlinLogging.logger { }

    /** Position units of subsidence a prop tolerates before it counts as unsupported. */
    const val SUPPORT_TOLERANCE = 1L
  }
}
