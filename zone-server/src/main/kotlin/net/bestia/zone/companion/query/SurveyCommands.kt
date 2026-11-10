package net.bestia.zone.companion.query

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.bus.contract.BusReply
import net.bestia.bus.contract.BusReply.Status
import net.bestia.bus.contract.ZoneAnswer
import net.bestia.bus.contract.ZoneAnswer.Theodolites.Theodolite
import net.bestia.bus.contract.ZoneQuery
import net.bestia.zone.account.persistence.LearnedSkillRepository
import net.bestia.zone.account.persistence.Master
import net.bestia.zone.cartography.survey.SurveyTier
import net.bestia.zone.cartography.survey.TheodoliteSurveys
import net.bestia.zone.casting.ReagentPayment
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.entity.StaticEntityKind
import net.bestia.zone.item.container.InventoryService
import net.bestia.zone.message.AccountTaskExecutor
import net.bestia.zone.prop.PlayerStructureRegistry
import net.bestia.zone.prop.StructureEntry
import net.bestia.zone.skill.SkillId
import net.bestia.zone.skill.ecs.KnownSkills
import net.bestia.zone.skill.persistence.SkillReagent
import net.bestia.zone.skill.persistence.SkillRepository
import net.bestia.zone.skill.persistence.findByIdentifier
import org.springframework.stereotype.Service
import kotlin.math.ceil

/**
 * Surveys at a master's own theodolites from the companion app, by the rules of a Cartography cast in the game.
 * Collecting the chart stays in the game. Writes run in the account's inbox, so they are ordered with what the
 * game client sends at the same moment.
 */
@Service
class SurveyCommands(
  private val owned: OwnedMasters,
  private val structures: PlayerStructureRegistry,
  private val surveys: TheodoliteSurveys,
  private val skills: SkillRepository,
  private val learnedSkills: LearnedSkillRepository,
  private val reagentPayment: ReagentPayment,
  private val inventoryService: InventoryService,
  private val world: WorldView,
  private val plane: MapPlane,
  private val inbox: AccountTaskExecutor,
) {

  fun list(correlationId: String, accountId: Long, query: ZoneQuery.Theodolites): BusReply {
    owned.find(accountId, query.masterId) ?: return notFound(correlationId)
    val theodolites = world.read { structures.all().filter { isStandingTheodoliteOf(it, query.masterId) } }

    return BusReply.ok(correlationId, ZoneAnswer.Theodolites(theodolites.sortedBy { it.id }.map(::toAnswer)))
  }

  fun start(correlationId: String, accountId: Long, query: ZoneQuery.StartSurvey): BusReply {
    val tier = SurveyTier.ofKilometres(query.km.toLong()) ?: return refused(correlationId, OpError.SKILL_INVALID_CHOICE)

    return inInbox(correlationId, accountId) { startOwned(correlationId, accountId, query, tier) }
  }

  fun stops(correlationId: String, accountId: Long, query: ZoneQuery.SurveyStops): BusReply {
    return inInbox(correlationId, accountId) {
      owned.find(accountId, query.masterId) ?: return@inInbox notFound(correlationId)
      val outcome = world.read {
        structures.of(query.structureId)?.takeIf { isStandingTheodoliteOf(it, query.masterId) }
          ?.let { surveys.submitStops(accountId, it.id, query.stopsMs) }
      } ?: return@inInbox BusReply.of(correlationId, Status.NOT_FOUND, "no game open")

      BusReply.ok(correlationId, ZoneAnswer.SurveyOutcome(outcome.name))
    }
  }

  private fun startOwned(correlationId: String, accountId: Long, query: ZoneQuery.StartSurvey, tier: SurveyTier): BusReply {
    val master = owned.find(accountId, query.masterId) ?: return notFound(correlationId)
    val theodolite = world.read { structures.of(query.structureId) }
      ?.takeIf { isStandingTheodoliteOf(it, master.id) }
      ?: return notFound(correlationId)
    val cartography = skills.findByIdentifier(SkillId.M_CARTOGRAPHY) ?: return refused(correlationId, OpError.SURVEY_TIER_LOCKED)

    val level = cartographyLevel(master, cartography.id)
    if (level < tier.minLevel) return refused(correlationId, OpError.SURVEY_TIER_LOCKED)
    if (theodolite.surveyRadiusMetres != null) return refused(correlationId, OpError.SURVEY_NO_THEODOLITE)
    // No mana is charged, unlike a cast: the master may not be in the world to pay it.
    if (!payReagents(master, cartography.mechanics.reagents)) return refused(correlationId, OpError.SKILL_MISSING_REAGENT)

    val game = surveys.prepare(master.id, accountId, theodolite, tier, level)
    val answer = ZoneAnswer.SurveyGame(theodolite.id, game.swingPeriodMs, game.arcWidthDegrees, game.arcCentresDegrees)
    return BusReply.ok(correlationId, answer)
  }

  /** The live level while the master is in the world, where a level learned since its last save already counts. */
  private fun cartographyLevel(master: Master, skillId: Long): Int {
    val live = world.modify(master.entityId) { id -> get(id, KnownSkills::class)?.levelOf(skillId) }

    return live ?: learnedSkills.findByMasterIdAndSkillId(master.id, skillId)?.level ?: 0
  }

  /**
   * From the live inventory while the master is in the world, as a cast pays; from the database otherwise. False,
   * taking nothing, when a reagent is missing.
   */
  private fun payReagents(master: Master, reagents: List<SkillReagent>): Boolean {
    val paidLive = world.modify(master.entityId) { id -> reagentPayment.pay(this, id, reagents) }

    return paidLive ?: inventoryService.consumeAll(master.id, reagents.map { it.itemId to it.amount })
  }

  private fun isStandingTheodoliteOf(entry: StructureEntry, masterId: Long): Boolean {
    return entry.kind == StaticEntityKind.THEODOLITE && entry.ownerMasterId == masterId && !entry.isUnderConstruction
  }

  private fun toAnswer(entry: StructureEntry): Theodolite {
    val radiusKm = entry.surveyRadiusMetres?.let { it / 1000 }
    val at = plane.pointOf(entry.position)

    return when {
      radiusKm == null -> Theodolite(entry.id, at, Theodolite.IDLE, null, null)
      entry.surveyRemainingSeconds > 0f ->
        Theodolite(entry.id, at, Theodolite.SURVEYING, radiusKm, ceil(entry.surveyRemainingSeconds).toInt())
      else -> Theodolite(entry.id, at, Theodolite.READY, radiusKm, null)
    }
  }

  private fun refused(correlationId: String, error: OpError): BusReply {
    return BusReply.of(correlationId, Status.INVALID, error.name)
  }

  private fun notFound(correlationId: String): BusReply {
    return BusReply.of(correlationId, Status.NOT_FOUND)
  }

  /** Never lets [work] throw: a failure in an account's inbox closes that account's game connection. */
  private fun inInbox(correlationId: String, accountId: Long, work: () -> BusReply): BusReply {
    var reply = notFound(correlationId)
    inbox.onIo(accountId) {
      reply = try {
        work()
      } catch (e: Exception) {
        LOG.error(e) { "Survey order of account $accountId failed" }
        BusReply.of(correlationId, Status.BUSY, "internal error")
      }
    }.join()

    return reply
  }

  private companion object {
    val LOG = KotlinLogging.logger { }
  }
}
