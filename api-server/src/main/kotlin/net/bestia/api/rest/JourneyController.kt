package net.bestia.api.rest

import net.bestia.api.auth.CompanionAuthFilter.Companion.ACCOUNT_ID
import net.bestia.api.bus.ZoneClient
import net.bestia.api.cache.AnswerCache
import net.bestia.bus.contract.MapPoint
import net.bestia.bus.contract.ZoneAnswer
import net.bestia.bus.contract.ZoneQuery
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestAttribute
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Sends a bestia off from the app, or stops it - the same journey a map click in the game starts, walked however
 * far while nobody plays. A 404 is a bestia that is not the master's, or not in the world.
 */
@RestController
@RequestMapping("/v1/companion/masters/{masterId}/bestias/{bestiaId}/journey")
class JourneyController(
  private val zone: ZoneClient,
  private val cache: AnswerCache,
) {

  /**
   * Through [waypoints] in turn, or along the saved route under [routeKey] in the master's notebook instead - from
   * its end back when [reverse].
   */
  data class JourneyOrder(
    val waypoints: List<MastersController.Position> = emptyList(),
    val routeKey: String? = null,
    val reverse: Boolean = false,
  )

  @PutMapping
  fun send(
    @RequestAttribute(ACCOUNT_ID) accountId: Long,
    @PathVariable masterId: Long,
    @PathVariable bestiaId: Long,
    @RequestHeader("Idempotency-Key") idempotencyKey: String,
    @RequestBody order: JourneyOrder,
  ): ResponseEntity<Void> {
    val waypoints = order.waypoints.take(MAX_WAYPOINTS).map { MapPoint(it.eastMetres, it.northMetres) }
    val query = ZoneQuery.SendOnJourney(masterId, bestiaId, waypoints, order.routeKey, order.reverse)
    zone.ask(accountId, query, idempotencyKey.take(MAX_KEY_LENGTH)).answerOrThrow<ZoneAnswer.Done>()

    cache.forget(accountId)
    return ResponseEntity.noContent().build()
  }

  @DeleteMapping
  fun stop(
    @RequestAttribute(ACCOUNT_ID) accountId: Long,
    @PathVariable masterId: Long,
    @PathVariable bestiaId: Long,
    @RequestHeader("Idempotency-Key") idempotencyKey: String,
  ): ResponseEntity<Void> {
    zone.ask(accountId, ZoneQuery.StopJourney(masterId, bestiaId), idempotencyKey.take(MAX_KEY_LENGTH))
      .answerOrThrow<ZoneAnswer.Done>()

    cache.forget(accountId)
    return ResponseEntity.noContent().build()
  }

  private companion object {
    const val MAX_KEY_LENGTH = 64

    /** The zone keeps no more stops than this in one journey. */
    const val MAX_WAYPOINTS = 16
  }
}
