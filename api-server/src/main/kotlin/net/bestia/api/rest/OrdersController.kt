package net.bestia.api.rest

import net.bestia.bus.contract.OrderRuleLine
import net.bestia.bus.contract.OrderNumbers
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
 * A bestia's orders, given from the app. It takes effect at once, as in the game. A 504 means the zone
 * did not answer in time and the order may or may not have arrived: send it again with the same Idempotency-Key.
 */
@RestController
@RequestMapping("/v1/companion/masters/{masterId}/bestias/{bestiaId}/orders")
class OrdersController(
  private val zone: ZoneClient,
  private val cache: AnswerCache,
) {

  /**
   * [job] is GUARD, GATHER, CRAFT, MINE or REST; [courage] is TIMID, CAREFUL, STEADY, BOLD or BRAVE. [fallbackJob]
   * runs while a GATHER, CRAFT or MINE job has nothing to do; null for none. [overrides] are the Fine-tune numbers.
   *
   * With [presetId] the bestia follows that preset: the other fields come from it, and [overrides] are only the
   * numbers it keeps as its own. In an answer, [overrides] are the numbers in force and [ownOverrides] its own.
   */
  data class BestiaOrders(
    val job: String,
    val courage: String,
    val fallbackJob: String? = null,
    val overrides: OrderNumbers = OrderNumbers(),
    val rules: List<OrderRuleLine> = emptyList(),
    val presetId: Long? = null,
    val ownOverrides: OrderNumbers = OrderNumbers(),
  )

  @PutMapping
  fun set(
    @RequestAttribute(ACCOUNT_ID) accountId: Long,
    @PathVariable masterId: Long,
    @PathVariable bestiaId: Long,
    @RequestHeader("Idempotency-Key") idempotencyKey: String,
    @RequestBody orders: BestiaOrders,
  ): BestiaOrders {
    val query = ZoneQuery.SetOrders(masterId, bestiaId, orders.job, orders.courage, orders.fallbackJob, orders.overrides, orders.rules, orders.presetId)
    val stored = zone.ask(accountId, query, idempotencyKey.take(MAX_KEY_LENGTH)).answerOrThrow<ZoneAnswer.Orders>()

    cache.forget(accountId)
    return BestiaOrders(
      stored.job, stored.courage, stored.fallbackJob, stored.overrides, stored.rules, stored.presetId, stored.ownOverrides,
    )
  }

  private companion object {
    const val MAX_KEY_LENGTH = 64
  }
}
