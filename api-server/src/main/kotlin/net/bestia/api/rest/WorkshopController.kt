package net.bestia.api.rest

import net.bestia.api.auth.CompanionAuthFilter.Companion.ACCOUNT_ID
import net.bestia.api.bus.ZoneClient
import net.bestia.bus.contract.ZoneAnswer
import net.bestia.bus.contract.ZoneQuery
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestAttribute
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * A master's crafting stations: their queues and workers. Ingredients for a queue come only from the chest beside the
 * station. Every write answers with the whole workshop. A 400 carries the refusal, for example
 * CRAFT_MISSING_MATERIALS.
 */
@RestController
@RequestMapping("/v1/companion/masters/{masterId}/workshop")
class WorkshopController(
  private val zone: ZoneClient,
) {

  data class QueueOrder(val recipeId: Long, val amount: Int)

  data class MoveOrder(val to: Int)

  /** [playerBestiaId] for one of the master's bestias; without it [anyFreeCrafter], or else the master. */
  data class WorkerOrder(val playerBestiaId: Long? = null, val anyFreeCrafter: Boolean = false)

  @GetMapping
  fun workshop(@RequestAttribute(ACCOUNT_ID) accountId: Long, @PathVariable masterId: Long): ZoneAnswer.Workshop {
    return zone.ask(accountId, ZoneQuery.Workshop(masterId)).answerOrThrow()
  }

  @PostMapping("/{structureId}/queue")
  fun queue(
    @RequestAttribute(ACCOUNT_ID) accountId: Long,
    @PathVariable masterId: Long,
    @PathVariable structureId: Long,
    @RequestHeader("Idempotency-Key") idempotencyKey: String,
    @RequestBody order: QueueOrder,
  ): ZoneAnswer.Workshop {
    return write(accountId, ZoneQuery.QueueAtStation(masterId, structureId, order.recipeId, order.amount), idempotencyKey)
  }

  @DeleteMapping("/{structureId}/queue/{index}")
  fun cancel(
    @RequestAttribute(ACCOUNT_ID) accountId: Long,
    @PathVariable masterId: Long,
    @PathVariable structureId: Long,
    @PathVariable index: Int,
    @RequestHeader("Idempotency-Key") idempotencyKey: String,
  ): ZoneAnswer.Workshop {
    return write(accountId, ZoneQuery.CancelAtStation(masterId, structureId, index), idempotencyKey)
  }

  @PutMapping("/{structureId}/queue/{index}/position")
  fun move(
    @RequestAttribute(ACCOUNT_ID) accountId: Long,
    @PathVariable masterId: Long,
    @PathVariable structureId: Long,
    @PathVariable index: Int,
    @RequestHeader("Idempotency-Key") idempotencyKey: String,
    @RequestBody order: MoveOrder,
  ): ZoneAnswer.Workshop {
    return write(accountId, ZoneQuery.MoveAtStation(masterId, structureId, index, order.to), idempotencyKey)
  }

  @PutMapping("/{structureId}/worker")
  fun worker(
    @RequestAttribute(ACCOUNT_ID) accountId: Long,
    @PathVariable masterId: Long,
    @PathVariable structureId: Long,
    @RequestHeader("Idempotency-Key") idempotencyKey: String,
    @RequestBody order: WorkerOrder,
  ): ZoneAnswer.Workshop {
    val query = ZoneQuery.SetStationWorker(masterId, structureId, order.playerBestiaId, order.anyFreeCrafter)
    return write(accountId, query, idempotencyKey)
  }

  private fun write(accountId: Long, query: ZoneQuery, idempotencyKey: String): ZoneAnswer.Workshop {
    return zone.ask(accountId, query, idempotencyKey.take(MAX_KEY_LENGTH)).answerOrThrow()
  }

  private companion object {
    const val MAX_KEY_LENGTH = 64
  }
}
