package net.bestia.api.rest

import net.bestia.api.auth.CompanionAuthFilter.Companion.ACCOUNT_ID
import net.bestia.api.bus.ZoneClient
import net.bestia.api.cache.AnswerCache
import net.bestia.bus.contract.OrderSheet
import net.bestia.bus.contract.ZoneAnswer
import net.bestia.bus.contract.ZoneQuery
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestAttribute
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/**
 * A master's named orders. Bestias that follow a preset change with it; see [OrdersController]. Each write answers
 * with all the master's presets. A 400 says why: INVALID_NAME, NAME_TAKEN, TOO_MANY or that the orders cannot be
 * followed.
 */
@RestController
@RequestMapping("/v1/companion/masters/{masterId}/order-presets")
class OrderPresetsController(
  private val zone: ZoneClient,
  private val cache: AnswerCache,
) {

  /** [bestias] is how many follow it. Named apart from other controllers' types: OpenAPI schemas share one namespace. */
  data class OrderPreset(val id: Long, val name: String, val sheet: OrderSheet, val bestias: Int)

  data class PresetWrite(val name: String, val sheet: OrderSheet)

  @GetMapping
  fun list(@RequestAttribute(ACCOUNT_ID) accountId: Long, @PathVariable masterId: Long): List<OrderPreset> {
    return presetsOf(zone.ask(accountId, ZoneQuery.OrderPresets(masterId)).answerOrThrow())
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  fun add(
    @RequestAttribute(ACCOUNT_ID) accountId: Long,
    @PathVariable masterId: Long,
    @RequestHeader("Idempotency-Key") idempotencyKey: String,
    @RequestBody write: PresetWrite,
  ): List<OrderPreset> {
    return save(accountId, ZoneQuery.SaveOrderPreset(masterId, null, write.name, write.sheet), idempotencyKey)
  }

  @PutMapping("/{presetId}")
  fun change(
    @RequestAttribute(ACCOUNT_ID) accountId: Long,
    @PathVariable masterId: Long,
    @PathVariable presetId: Long,
    @RequestHeader("Idempotency-Key") idempotencyKey: String,
    @RequestBody write: PresetWrite,
  ): List<OrderPreset> {
    return save(accountId, ZoneQuery.SaveOrderPreset(masterId, presetId, write.name, write.sheet), idempotencyKey)
  }

  /** Its bestias keep their orders, as their own. */
  @DeleteMapping("/{presetId}")
  fun delete(
    @RequestAttribute(ACCOUNT_ID) accountId: Long,
    @PathVariable masterId: Long,
    @PathVariable presetId: Long,
    @RequestHeader("Idempotency-Key") idempotencyKey: String,
  ): List<OrderPreset> {
    return save(accountId, ZoneQuery.DeleteOrderPreset(masterId, presetId), idempotencyKey)
  }

  /** A write changes the orders of the bestias that follow the preset, which the cached roster shows. */
  private fun save(accountId: Long, query: ZoneQuery, idempotencyKey: String): List<OrderPreset> {
    val presets = presetsOf(zone.ask(accountId, query, idempotencyKey.take(MAX_KEY_LENGTH)).answerOrThrow())
    cache.forget(accountId)
    return presets
  }

  private fun presetsOf(answer: ZoneAnswer.OrderPresets): List<OrderPreset> {
    return answer.presets.map { OrderPreset(it.id, it.name, it.sheet, it.bestias) }
  }

  private companion object {
    const val MAX_KEY_LENGTH = 64
  }
}
