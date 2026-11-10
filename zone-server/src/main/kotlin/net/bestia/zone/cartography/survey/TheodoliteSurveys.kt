package net.bestia.zone.cartography.survey

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.bnet.proto.OperationSuccessProto.OpSuccess
import net.bestia.zone.cartography.SurveyService
import net.bestia.zone.cartography.chart.ChartService
import net.bestia.zone.ecs.core.World
import net.bestia.zone.entity.StaticEntityKind
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.message.OperationErrorSMSG
import net.bestia.zone.message.OperationSuccessSMSG
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.persistence.AsyncJobExecutor
import net.bestia.zone.prop.PlayerStructureRegistry
import net.bestia.zone.prop.StructureEntry
import net.bestia.zone.util.EntityId
import net.bestia.zone.world.WorldService
import net.bestia.zone.world.mana.ManaConcentrationService
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Surveys at a theodolite: the needle game a cast of Cartography (or the companion app) opens, the ten minutes of
 * work a won game starts, and collecting the chart once it is drawn. One survey per theodolite at a time.
 */
@Service
class TheodoliteSurveys(
  private val structures: PlayerStructureRegistry,
  private val chartService: ChartService,
  private val surveyService: SurveyService,
  private val mana: ManaConcentrationService,
  private val worldService: WorldService,
  private val asyncJobExecutor: AsyncJobExecutor,
  private val messages: OutMessageProcessor,
) {

  private data class Session(val masterId: Long, val accountId: Long, val tier: SurveyTier, val game: SurveyMinigame)

  private val sessions = ConcurrentHashMap<Long, Session>()

  /** The owner's idle theodolite within reach of [at], or null. */
  fun idleTheodoliteNear(masterId: Long, at: Vec3L): StructureEntry? {
    return structures.all().firstOrNull {
      it.kind == StaticEntityKind.THEODOLITE && !it.isUnderConstruction && it.ownerMasterId == masterId &&
        it.surveyRadiusMetres == null && it.position.distance(at) <= REACH_TILES
    }
  }

  /** Opens the needle game for a cast at [theodolite]. [prepare] reads the database, so it runs off the tick. */
  fun open(masterId: Long, accountId: Long, theodolite: StructureEntry, tier: SurveyTier, cartographyLevel: Int) {
    asyncJobExecutor.submit(masterId) {
      val game = prepare(masterId, accountId, theodolite, tier, cartographyLevel)
      messages.sendToPlayer(accountId, SurveyMinigameSMSG(theodolite.id, game))
    }
  }

  /**
   * Builds the needle game for [tier] at [theodolite] and keeps it until [accountId] sends its stops. Blocks on the
   * master's charts, which set the difficulty, so never call it on the tick or inside a world scope.
   */
  fun prepare(masterId: Long, accountId: Long, theodolite: StructureEntry, tier: SurveyTier, cartographyLevel: Int): SurveyMinigame {
    val voxelSize = worldService.config.voxelSize
    val centreX = theodolite.position.x * voxelSize
    val centreY = theodolite.position.y * voxelSize

    val charted = chartService.inventoryCoverage(masterId)
    val km = (0..MAX_SEARCH_KM).firstOrNull { km -> isChartedAround(charted::contains, centreX, centreY, km * 1000.0) }
      ?: MAX_SEARCH_KM
    val manaHere = mana.manaAt(theodolite.position)
    val game = SurveyMinigame.of(SurveyMinigame.difficulty(tier, km, manaHere, cartographyLevel))

    sessions[theodolite.id] = Session(masterId, accountId, tier, game)
    return game
  }

  /**
   * Judges the stops for the open game at [structureId]; null when [accountId] has no game open there. A win starts
   * the ten minutes of work. On the tick or inside a world scope, since it writes the structure registry.
   */
  fun submitStops(accountId: Long, structureId: Long, stopsMs: List<Long>): StopsOutcome? {
    val session = sessions.remove(structureId)?.takeIf { it.accountId == accountId } ?: return null
    if (!session.game.isHit(stopsMs)) return StopsOutcome.MISSED

    structures.setSurvey(structureId, session.tier.radiusMetres, WORK_SECONDS)
    return StopsOutcome.WON
  }

  enum class StopsOutcome { WON, MISSED }

  /** Clicking the theodolite: collects a drawn chart, or says how long the survey still needs. */
  fun collect(world: World, actorId: EntityId, accountId: Long?, masterId: Long?, structureId: Long): OpError? {
    val entry = structures.of(structureId) ?: return null
    val radius = entry.surveyRadiusMetres ?: return null
    if (masterId != entry.ownerMasterId) return OpError.SURVEY_NOT_YOURS
    if (entry.surveyRemainingSeconds > 0f) {
      accountId?.let {
        val minutes = ((entry.surveyRemainingSeconds + 59) / 60).toInt()
        messages.sendToPlayer(it, OperationErrorSMSG(OpError.SURVEY_IN_PROGRESS, listOf(minutes.toString())))
      }
      return null
    }

    structures.setSurvey(structureId, null, 0f)
    val voxelSize = worldService.config.voxelSize
    asyncJobExecutor.submit(entry.ownerMasterId) {
      val result = chartService.mint(entry.ownerMasterId, entry.position.x * voxelSize, entry.position.y * voxelSize, radius.toDouble())
      if (result is ChartService.Result.Ok) {
        surveyService.applyToLiveInventory(actorId, result)
        accountId?.let { messages.sendToPlayer(it, OperationSuccessSMSG(OpSuccess.CHART_WRITTEN)) }
      } else {
        LOG.warn { "Theodolite $structureId could not hand master $masterId its chart: $result" }
      }
    }

    return null
  }

  private fun isChartedAround(contains: (Double, Double) -> Boolean, x: Double, y: Double, radius: Double): Boolean {
    if (radius == 0.0) return contains(x, y)

    return (0 until DIRECTIONS).any { step ->
      val angle = 2 * PI * step / DIRECTIONS
      contains(x + cos(angle) * radius, y + sin(angle) * radius)
    }
  }

  companion object {
    private val LOG = KotlinLogging.logger { }

    const val REACH_TILES = 2L

    /** Ten minutes of work at the theodolite before the chart is drawn. */
    const val WORK_SECONDS = 600f

    private const val MAX_SEARCH_KM = 15
    private const val DIRECTIONS = 32
  }
}
