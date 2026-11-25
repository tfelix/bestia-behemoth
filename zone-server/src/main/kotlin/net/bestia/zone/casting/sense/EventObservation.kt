package net.bestia.zone.casting.sense

import net.bestia.zone.casting.sense.BrewingEvents.Brewing
import net.bestia.zone.ecs.core.World
import net.bestia.zone.entity.StaticEntityKind
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.identity.ecs.Account
import net.bestia.zone.identity.ecs.Master
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.prop.PlayerStructureRegistry
import net.bestia.zone.util.EntityId
import net.bestia.zone.world.WorldService
import org.springframework.stereotype.Component
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.roundToLong

/**
 * Observation: the world events brewing around an Observation Outpost. Range is a kilometre per level from the
 * outpost, further for big events, and each level tells more about each one:
 *
 * | Lv. | tells                                              |
 * | --- | -------------------------------------------------- |
 * | 1   | the direction                                      |
 * | 2   | also near or far                                   |
 * | 3   | the distance within 500 m, and the kind of thing   |
 * | 4   | also how far along it is                           |
 * | 5   | what exactly it is, and roughly when it moves on   |
 */
@Component
class EventObservation(
  private val structures: PlayerStructureRegistry,
  private val worldService: WorldService,
  private val messages: OutMessageProcessor,
  private val brewing: BrewingEvents? = null,
  private val observedMarks: ObservedMarks? = null,
) {

  /** The outpost the caster stands at, or null. Read-only, so a cast can be refused before its bar. */
  fun outpostNear(world: World, casterId: EntityId): Vec3L? {
    val at = world.get(casterId, Position::class)?.toVec3L() ?: return null
    val size = worldService.config.chunkSize.toLong()
    val chunkX = Math.floorDiv(at.x, size).toInt()
    val chunkY = Math.floorDiv(at.y, size).toInt()

    return (-1..1).flatMap { dx -> (-1..1).flatMap { dy -> structures.`in`(chunkX + dx, chunkY + dy) } }
      .filter { it.kind == StaticEntityKind.OBSERVATION_OUTPOST && !it.isUnderConstruction }
      .map { it.position }
      .firstOrNull { it.distance(at) <= OUTPOST_REACH }
  }

  fun observe(world: World, casterId: EntityId, level: Int) {
    val accountId = world.get(casterId, Account::class)?.accountId ?: return
    val outpost = outpostNear(world, casterId) ?: return
    val voxel = worldService.config.voxelSize
    val fromX = outpost.x * voxel
    val fromY = outpost.y * voxel

    val felt = brewing?.brewing().orEmpty()
      .filter { hypot(it.x - fromX, it.y - fromY) <= level * METRES_PER_LEVEL * it.feltFrom }
    val marks = felt.map { markOf(it, fromX, fromY, level) }

    messages.sendToPlayer(accountId, SenseMarksSMSG(marks, VISIBLE_SECONDS))

    val masterId = world.get(casterId, Master::class)?.masterId ?: return
    if (level >= DISTANCE_LEVEL) observedMarks?.record(accountId, masterId, marks.zip(felt).map { (mark, event) -> onMap(mark, event, level) })
  }

  /** A pin for the Scholar's own map: as much as this level told, in plain words, since the map shows text as typed. */
  private fun onMap(mark: SenseMark, event: Brewing, level: Int): ObservedMarks.Observed {
    val name = if (level >= EXACT_LEVEL) event.eventId.replace('-', ' ').replaceFirstChar { it.uppercase() }
    else "Something of the ${event.family}"
    val stage = event.stage.name.lowercase().takeIf { level >= STAGE_LEVEL }
    val hours = mark.depthMetres.takeIf { it > 0.0 }?.let { "within ${it.toInt()} hours" }
    val note = listOfNotNull(stage, hours).joinToString(", ").ifEmpty { null }

    return ObservedMarks.Observed(mark.eastMetres, mark.northMetres, name, note)
  }

  private fun markOf(event: Brewing, fromX: Double, fromY: Double, level: Int): SenseMark {
    val distance = hypot(event.x - fromX, event.y - fromY)
    val range = level * METRES_PER_LEVEL * event.feltFrom
    val shown = if (level >= DISTANCE_LEVEL) (distance / DISTANCE_STEP).roundToLong() * DISTANCE_STEP else range
    val scale = if (distance > 0.0) shown / distance else 0.0

    val precision = when {
      level >= DISTANCE_LEVEL -> Precision.DISTANCE
      level >= NEAR_FAR_LEVEL -> if (distance < range / 2) Precision.NEAR else Precision.FAR
      else -> Precision.DIRECTION
    }
    val label = when {
      level >= EXACT_LEVEL -> event.eventId
      level >= DISTANCE_LEVEL -> event.family
      else -> ""
    }
    val hours = event.changesInSeconds?.takeIf { level >= EXACT_LEVEL }?.let { ceil(it / 3600.0) } ?: 0.0

    return SenseMark(
      kind = SenseKind.EVENT,
      eastMetres = fromX + (event.x - fromX) * scale,
      northMetres = fromY + (event.y - fromY) * scale,
      label = label,
      refId = if (level >= STAGE_LEVEL) event.stage.ordinal + 1L else 0L,
      depthMetres = hours,
      sizeMetres = precision.ordinal.toDouble(),
    )
  }

  /** How exactly the mark's distance may be shown; travels as `size_metres`. See `sense_marks_smsg.proto`. */
  enum class Precision { DIRECTION, NEAR, FAR, DISTANCE }

  private companion object {
    const val OUTPOST_REACH = 5L
    const val METRES_PER_LEVEL = 1000.0
    const val DISTANCE_STEP = 500.0
    const val NEAR_FAR_LEVEL = 2
    const val DISTANCE_LEVEL = 3
    const val STAGE_LEVEL = 4
    const val EXACT_LEVEL = 5
    const val VISIBLE_SECONDS = 120
  }
}
