package net.bestia.zone.cartography.survey

import net.bestia.bnet.proto.OperationSuccessProto.OpSuccess
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.Phase
import net.bestia.zone.ecs.core.Schedule
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.World
import net.bestia.zone.entity.StaticEntityKind
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.identity.ecs.Master
import net.bestia.zone.identity.ecs.OwnedBestia
import net.bestia.zone.message.OperationSuccessSMSG
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.prop.PlayerStructureRegistry
import net.bestia.zone.prop.StructureEntry
import org.springframework.stereotype.Component as SpringComponent

/**
 * Works the surveys at theodolites. A survey only advances while its owner, or one of the owner's bestias, stands at
 * the theodolite, as at a workbench. When the work is done the owner is told the chart waits.
 */
@SpringComponent
class TheodoliteWorkSystem(
  private val structures: PlayerStructureRegistry,
  private val messages: OutMessageProcessor,
) : System {
  override val phase = Phase.UPKEEP
  override val schedule: Schedule = Schedule.EverySeconds(BEAT_SECONDS)
  override val reads: ComponentClassSet = setOf(Position::class, Master::class, OwnedBestia::class)
  override val writes: ComponentClassSet = emptySet()

  override fun update(world: World, deltaTime: Float) {
    val surveying = structures.all().filter(::isSurveying)
    if (surveying.isEmpty()) return

    val workers = workerPositionsByMaster(world)
    for (entry in surveying) {
      val isWorked = workers[entry.ownerMasterId].orEmpty().any { it.distance(entry.position) <= TheodoliteSurveys.REACH_TILES }
      if (!isWorked) continue

      val remaining = (entry.surveyRemainingSeconds - deltaTime).coerceAtLeast(0f)
      structures.setSurvey(entry.id, entry.surveyRadiusMetres, remaining)
      if (remaining == 0f) {
        entry.ownerAccountId?.let { messages.sendToPlayer(it, OperationSuccessSMSG(OpSuccess.SURVEY_READY)) }
      }
    }
  }

  private fun isSurveying(entry: StructureEntry): Boolean {
    return entry.kind == StaticEntityKind.THEODOLITE && entry.surveyRadiusMetres != null && entry.surveyRemainingSeconds > 0f
  }

  /** Where every master and every owned bestia stands, by master id. */
  private fun workerPositionsByMaster(world: World): Map<Long, List<Vec3L>> {
    val positions = HashMap<Long, MutableList<Vec3L>>()
    world.query(Master::class, Position::class).each {
      positions.getOrPut(get<Master>().masterId) { ArrayList() }.add(get<Position>().toVec3L())
    }
    world.query(OwnedBestia::class, Position::class).each {
      positions.getOrPut(get<OwnedBestia>().masterId) { ArrayList() }.add(get<Position>().toVec3L())
    }

    return positions
  }

  private companion object {
    /** The ten minutes need no finer count, and every beat saves the row. */
    const val BEAT_SECONDS = 10f
  }
}
