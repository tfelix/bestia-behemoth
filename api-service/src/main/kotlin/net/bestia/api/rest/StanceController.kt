package net.bestia.api.rest

import net.bestia.api.auth.CompanionAuthFilter.Companion.ACCOUNT_ID
import net.bestia.api.bus.ZoneClient
import net.bestia.api.cache.AnswerCache
import net.bestia.bus.contract.ZoneAnswer
import net.bestia.bus.contract.ZoneQuery
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestAttribute
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * A bestia's standing order, given from the app. It takes effect at once, as in the game. A 504 means the zone
 * did not answer in time and the order may or may not have arrived: send it again with the same Idempotency-Key.
 */
@RestController
@RequestMapping("/v1/companion/masters/{masterId}/bestias/{bestiaId}/stance")
class StanceController(
  private val zone: ZoneClient,
  private val cache: AnswerCache,
) {

  /** [stance] is HOLD, PATROL, FORAGE or DEFEND; [aggression] 0..100, clamped by the zone. */
  data class StanceOrder(val stance: String, val aggression: Int)

  @PutMapping
  fun set(
    @RequestAttribute(ACCOUNT_ID) accountId: Long,
    @PathVariable masterId: Long,
    @PathVariable bestiaId: Long,
    @RequestHeader("Idempotency-Key") idempotencyKey: String,
    @RequestBody order: StanceOrder,
  ): StanceOrder {
    val query = ZoneQuery.SetStance(masterId, bestiaId, order.stance, order.aggression)
    val stored = zone.ask(accountId, query, idempotencyKey.take(MAX_KEY_LENGTH)).answerOrThrow<ZoneAnswer.Stance>()

    cache.forget(accountId)
    return StanceOrder(stored.stance, stored.aggression)
  }

  private companion object {
    const val MAX_KEY_LENGTH = 64
  }
}
