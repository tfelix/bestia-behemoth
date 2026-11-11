package net.bestia.api.rest

import net.bestia.api.auth.CompanionAuthFilter.Companion.ACCOUNT_ID
import net.bestia.api.bus.ZoneClient
import net.bestia.api.cache.AnswerCache
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
 * A master's named places for their bestias' orders: work areas on the map, and names for their chests. Every write
 * answers with all of them. A 400 says why: INVALID_NAME, NAME_TAKEN, TOO_MANY or NOT_A_CHEST.
 */
@RestController
@RequestMapping("/v1/companion/masters/{masterId}/places")
class PlacesController(
  private val zone: ZoneClient,
  private val cache: AnswerCache,
) {

  /** The middle in map metres, the radius in tiles (4 to 64). */
  data class AreaWrite(val name: String, val eastMetres: Double, val northMetres: Double, val radius: Long)

  data class ChestWrite(val name: String, val chestStructureId: Long)

  @GetMapping
  fun bestiaPlaces(@RequestAttribute(ACCOUNT_ID) accountId: Long, @PathVariable masterId: Long): ZoneAnswer.BestiaPlaces {
    return zone.ask(accountId, ZoneQuery.BestiaPlaces(masterId)).answerOrThrow()
  }

  @PostMapping("/areas")
  fun addArea(
    @RequestAttribute(ACCOUNT_ID) accountId: Long,
    @PathVariable masterId: Long,
    @RequestHeader("Idempotency-Key") idempotencyKey: String,
    @RequestBody area: AreaWrite,
  ): ZoneAnswer.BestiaPlaces {
    return write(accountId, ZoneQuery.SaveAreaPlace(masterId, null, area.name, area.eastMetres, area.northMetres, area.radius), idempotencyKey)
  }

  @PutMapping("/areas/{placeId}")
  fun changeArea(
    @RequestAttribute(ACCOUNT_ID) accountId: Long,
    @PathVariable masterId: Long,
    @PathVariable placeId: Long,
    @RequestHeader("Idempotency-Key") idempotencyKey: String,
    @RequestBody area: AreaWrite,
  ): ZoneAnswer.BestiaPlaces {
    val query = ZoneQuery.SaveAreaPlace(masterId, placeId, area.name, area.eastMetres, area.northMetres, area.radius)
    return write(accountId, query, idempotencyKey)
  }

  @PostMapping("/chests")
  fun nameChest(
    @RequestAttribute(ACCOUNT_ID) accountId: Long,
    @PathVariable masterId: Long,
    @RequestHeader("Idempotency-Key") idempotencyKey: String,
    @RequestBody chest: ChestWrite,
  ): ZoneAnswer.BestiaPlaces {
    return write(accountId, ZoneQuery.SaveChestPlace(masterId, null, chest.name, chest.chestStructureId), idempotencyKey)
  }

  /** Orders that named it go back to the area around home and the nearest chest. */
  @DeleteMapping("/{placeId}")
  fun deletePlace(
    @RequestAttribute(ACCOUNT_ID) accountId: Long,
    @PathVariable masterId: Long,
    @PathVariable placeId: Long,
    @RequestHeader("Idempotency-Key") idempotencyKey: String,
  ): ZoneAnswer.BestiaPlaces {
    return write(accountId, ZoneQuery.DeletePlace(masterId, placeId), idempotencyKey)
  }

  /** Deleting a place changes the orders the cached roster shows. */
  private fun write(accountId: Long, query: ZoneQuery, idempotencyKey: String): ZoneAnswer.BestiaPlaces {
    val places: ZoneAnswer.BestiaPlaces = zone.ask(accountId, query, idempotencyKey.take(MAX_KEY_LENGTH)).answerOrThrow()
    cache.forget(accountId)
    return places
  }

  private companion object {
    const val MAX_KEY_LENGTH = 64
  }
}
