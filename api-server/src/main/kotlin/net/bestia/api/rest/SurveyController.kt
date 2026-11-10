package net.bestia.api.rest

import net.bestia.api.auth.CompanionAuthFilter.Companion.ACCOUNT_ID
import net.bestia.api.bus.ZoneClient
import net.bestia.api.cache.AnswerCache
import net.bestia.bus.contract.BusReply
import net.bestia.bus.contract.ZoneAnswer
import net.bestia.bus.contract.ZoneQuery
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestAttribute
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

/**
 * Surveys at a master's own theodolites: the list, paying for a survey, and its needle game. Collecting the chart
 * stays in the game. The list is not cached: a survey's work runs by the clock, and no event says when it ends.
 */
@RestController
@RequestMapping("/v1/companion/masters/{masterId}/theodolites")
class SurveyController(
  private val zone: ZoneClient,
  private val cache: AnswerCache,
) {

  /** [state] is IDLE, SURVEYING or READY; [radiusKm] is set while it surveys or its chart waits. */
  data class Theodolite(
    val structureId: Long,
    val position: MastersController.Position,
    val state: String,
    val radiusKm: Int?,
    val remainingSeconds: Int?,
  )

  /** [km] is 1, 3 or 5. */
  data class SurveyOrder(val km: Int)

  /**
   * In round i the needle points at `90 * sin(2 * PI * t / swingPeriodMs)` degrees after t ms (-90 left, +90 right),
   * and must stop within [arcWidthDegrees] around `arcCentresDegrees[i]`.
   */
  data class SurveyGame(val swingPeriodMs: Int, val arcWidthDegrees: Int, val arcCentresDegrees: List<Int>)

  /** Milliseconds from the start of each round to its stop. */
  data class SurveyStops(val stopsMs: List<Long>)

  /** [outcome] is WON, and the theodolite works for ten minutes, or MISSED. */
  data class SurveyResult(val outcome: String)

  @GetMapping
  fun list(@RequestAttribute(ACCOUNT_ID) accountId: Long, @PathVariable masterId: Long): List<Theodolite> {
    val answer = zone.ask(accountId, ZoneQuery.Theodolites(masterId)).answerOrThrow<ZoneAnswer.Theodolites>()

    return answer.theodolites.map {
      Theodolite(it.structureId, MastersController.Position(it.at.east, it.at.north), it.state, it.radiusKm, it.remainingSeconds)
    }
  }

  /**
   * Spends one paper and one ink and answers the needle game. 403: the master's Cartography level does not open this
   * size. 409: the theodolite already surveys. 422: paper or ink is missing. A 504 means the zone did not answer in
   * time and the survey may or may not be paid: send it again with the same Idempotency-Key.
   */
  @PostMapping("/{structureId}/survey")
  fun start(
    @RequestAttribute(ACCOUNT_ID) accountId: Long,
    @PathVariable masterId: Long,
    @PathVariable structureId: Long,
    @RequestHeader("Idempotency-Key") idempotencyKey: String,
    @RequestBody order: SurveyOrder,
  ): SurveyGame {
    val query = ZoneQuery.StartSurvey(masterId, structureId, order.km)
    val reply = zone.ask(accountId, query, idempotencyKey.take(MAX_KEY_LENGTH))
    if (reply.status == BusReply.Status.INVALID) throw refusal(reply.detail)
    val game = reply.answerOrThrow<ZoneAnswer.SurveyGame>()

    // Paper and ink left the bag.
    cache.forget(accountId)
    return SurveyGame(game.swingPeriodMs, game.arcWidthDegrees, game.arcCentresDegrees)
  }

  /** A 404 is a theodolite with no game open for this account: it was played already, or never started. */
  @PostMapping("/{structureId}/survey/stops")
  fun stops(
    @RequestAttribute(ACCOUNT_ID) accountId: Long,
    @PathVariable masterId: Long,
    @PathVariable structureId: Long,
    @RequestHeader("Idempotency-Key") idempotencyKey: String,
    @RequestBody stops: SurveyStops,
  ): SurveyResult {
    val query = ZoneQuery.SurveyStops(masterId, structureId, stops.stopsMs.take(MAX_STOPS))
    val outcome = zone.ask(accountId, query, idempotencyKey.take(MAX_KEY_LENGTH)).answerOrThrow<ZoneAnswer.SurveyOutcome>()

    return SurveyResult(outcome.outcome)
  }

  /** The zone refuses with the game's error name; each gets its own status, so the app can say which it was. */
  private fun refusal(errorName: String?): ResponseStatusException {
    val status = when (errorName) {
      "SURVEY_TIER_LOCKED" -> HttpStatus.FORBIDDEN
      "SURVEY_NO_THEODOLITE" -> HttpStatus.CONFLICT
      "SKILL_MISSING_REAGENT" -> HttpStatus.UNPROCESSABLE_ENTITY
      else -> HttpStatus.BAD_REQUEST
    }

    return ResponseStatusException(status, errorName)
  }

  private companion object {
    const val MAX_KEY_LENGTH = 64

    /** More than any game has rounds; the zone judges a wrong count as a miss. */
    const val MAX_STOPS = 16
  }
}
