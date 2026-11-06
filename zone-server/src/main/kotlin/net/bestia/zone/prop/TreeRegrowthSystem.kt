package net.bestia.zone.prop

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.Phase
import net.bestia.zone.ecs.core.Schedule
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.World
import net.bestia.zone.entity.GrowthStage
import net.bestia.zone.entity.ecs.WorldObjectIdentity
import net.bestia.zone.movement.ecs.Position
import org.springframework.stereotype.Component
import java.time.Clock

/**
 * Grows removed trees back, one stage a week: stump, sapling, young tree, grown.
 *
 * A stump only sprouts while a grown tree stands near it - see [TreeNeighbourhood]. Otherwise it stays a stump
 * and tries again a week later, so a wood cleared from one side grows back from the other, and a wood cleared
 * completely never does. Once it has sprouted, a tree keeps growing whatever happens around it.
 *
 * ### Over every row, held or not
 *
 * A tree grows while nobody looks, so this sweeps the divergence rows, not the resident entities. Where the
 * column is held, the standing entity is swapped for the next stage and the column re-announced.
 *
 * Each next stage is due a whole stage after the last one was, not after now: after a long downtime the trees
 * catch up a stage per sweep instead of starting their week over.
 *
 * ### Once a minute
 *
 * A stage takes a week, so a faster cadence would find nothing to do. [BUDGET] bounds one sweep for the morning
 * after a downtime, when every row is due at once.
 */
@Component
class TreeRegrowthSystem(
  private val divergence: WorldObjectDivergenceRegistry,
  private val kinds: PropKindRegistry,
  private val neighbourhood: TreeNeighbourhood,
  private val residency: WorldObjectResidencyService,
  private val clock: Clock = Clock.systemUTC()
) : System {
  override val phase = Phase.UPKEEP

  override val schedule: Schedule = Schedule.EverySeconds(SWEEP_SECONDS)

  override val reads: ComponentClassSet = setOf(WorldObjectIdentity::class, Position::class)

  /** A tree's next stage is a new prop entity in its place, the way a removed tree's stump is. */
  override val writes: ComponentClassSet = PropRemoval.WRITES

  override fun update(world: World, deltaTime: Float) {
    for ((propId, entry) in divergence.due(clock.instant(), BUDGET)) {
      val regrowth = kinds.of(entry.kind).regrowth ?: continue
      val stage = entry.growth ?: continue
      val dueAt = entry.resumeAt ?: continue

      val standing = residentEntityOf(world, propId)
      // Mid-fight: its health is on the entity, and a new one would heal it. It grows at the next sweep.
      if (standing != null && world.has(standing, Position::class)) continue

      val next = when (stage) {
        GrowthStage.STUMP ->
          if (neighbourhood.hasGrownTreeNear(propId, regrowth.neighbourRadius)) GrowthStage.SAPLING else GrowthStage.STUMP
        GrowthStage.SAPLING -> GrowthStage.YOUNG
        GrowthStage.YOUNG, GrowthStage.GROWN -> GrowthStage.GROWN
      }

      if (next == GrowthStage.GROWN) {
        divergence.evictRegrown(propId)
      } else {
        divergence.record(propId, entry.kind, next.asDivergence(), dueAt.plusSeconds(regrowth.stageSeconds))
      }

      if (standing != null && next != stage) residency.replace(world, standing, next)

      LOG.debug { "Tree $propId: $stage -> $next" }
    }
  }

  private fun residentEntityOf(world: World, propId: Long): Long? {
    val column = neighbourhood.columnOf(propId)

    return residency.entitiesIn(column.x, column.y).firstOrNull { entity ->
      world.get(entity, WorldObjectIdentity::class)?.propId == propId
    }
  }

  private companion object {
    private val LOG = KotlinLogging.logger { }

    const val SWEEP_SECONDS = 60f

    /** Rows one sweep grows on. A neighbour check can cost up to four generated columns. */
    const val BUDGET = 64
  }
}
