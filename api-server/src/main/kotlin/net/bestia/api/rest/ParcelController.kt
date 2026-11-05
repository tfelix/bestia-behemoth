package net.bestia.api.rest

import net.bestia.api.auth.CompanionAuthFilter.Companion.ACCOUNT_ID
import net.bestia.api.bus.ZoneClient
import net.bestia.bus.contract.ZoneAnswer
import net.bestia.bus.contract.ZoneQuery
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestAttribute
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * A master's parcels, to watch them come. Collecting needs the master at a post office, so there is nothing to
 * write here. Not cached: a parcel arrives by the clock, and no event clears a cache when it does.
 */
@RestController
@RequestMapping("/v1/companion/masters/{masterId}/parcels")
class ParcelController(private val zone: ZoneClient) {

  /** [item] is the item's identifier, as a bag slot names it. */
  data class ParcelGoods(val item: String, val amount: Int)

  data class Parcel(
    val parcelId: Long,
    val senderName: String,
    val destinationName: String,
    val latestArrivalEpochMs: Long,
    val arrived: Boolean,
    val returning: Boolean,
    val goods: List<ParcelGoods>,
  )

  /** Soonest first. */
  @GetMapping
  fun list(@RequestAttribute(ACCOUNT_ID) accountId: Long, @PathVariable masterId: Long): List<Parcel> {
    val answer = zone.ask(accountId, ZoneQuery.Parcels(masterId)).answerOrThrow<ZoneAnswer.Parcels>()

    return answer.parcels.map { parcel ->
      Parcel(
        parcel.parcelId, parcel.senderName, parcel.destinationName, parcel.latestArrivalEpochMs, parcel.arrived,
        parcel.returning, parcel.goods.map { ParcelGoods(it.item, it.amount) },
      )
    }
  }
}
