package net.bestia.zone.postal.parcel

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.account.persistence.Master
import net.bestia.zone.account.persistence.MasterRepository
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.economy.CommodityItems
import net.bestia.zone.economy.WorldReserve
import net.bestia.zone.item.container.InventoryService
import net.bestia.zone.postal.office.PostOffices
import net.bestia.zone.postal.parcel.ParcelService.Outcome
import net.bestia.zone.postal.persistence.Parcel
import net.bestia.zone.postal.persistence.ParcelRepository
import org.junit.jupiter.api.Test
import org.springframework.context.ApplicationEventPublisher
import org.springframework.transaction.TransactionStatus
import org.springframework.transaction.support.TransactionCallback
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** The parcel rules that do not need a world: weight, timing, and where a parcel may be collected. */
class ParcelServiceTest {

  private val now = Instant.parse("2026-11-05T12:00:00Z")

  private val parcels = mockk<ParcelRepository>(relaxed = true)
  private val masters = mockk<MasterRepository>()
  private val offices = mockk<PostOffices>()
  private val roads = mockk<RoadDistance>()
  private val inventory = mockk<InventoryService>(relaxed = true)
  private val world = mockk<WorldView>().also { view ->
    every { view.read<Any?>(any()) } answers { firstArg<World.() -> Any?>().invoke(mockk()) }
  }
  private val transactions = mockk<TransactionTemplate>().also { template ->
    every { template.execute<Any?>(any()) } answers {
      firstArg<TransactionCallback<Any?>>().doInTransaction(mockk<TransactionStatus>(relaxed = true))
    }
  }

  private val service = ParcelService(
    parcels, masters, offices, roads, inventory, mockk<CommodityItems> { every { coinItemId() } returns COIN },
    world, mockk<WorldReserve>(relaxed = true), mockk<ApplicationEventPublisher>(relaxed = true), transactions,
    Clock.fixed(now, ZoneOffset.UTC),
  )

  private val ashfen = PostOffices.Office(settlement = 1, name = "Ashfen", level = 2)

  init {
    every { masters.findByName("Mira") } returns mockk<Master> {
      every { id } returns RECIPIENT
      every { name } returns "Mira"
      every { homePostOffice } returns 2
      every { homeSettlementName } returns "Ironhill"
    }
    every { offices.homeOf(2, "Ironhill") } returns 2
    every { offices.officeOf(any()) } answers { PostOffices.Office(firstArg(), "Office ${firstArg<Int>()}", 2) }
    every { roads.km(1, 2) } returns 20.0
  }

  private fun goods(weight: Int, amount: Int = 1) = listOf(ParcelService.Goods(APPLE, 0L, amount, weight, null))

  @Test
  fun `a parcel over 10 kg is refused`() {
    val outcome = service.quote(ashfen, "Mira", goods(weight = Freight.MAX_WEIGHT_UNITS + 1))

    assertEquals(Outcome.Refused(OpError.PARCEL_TOO_HEAVY), outcome)
  }

  @Test
  fun `a parcel arrives by the coach after next plus its time on the road`() {
    val quote = assertIs<Outcome.Quoted>(service.quote(ashfen, "Mira", goods(weight = 100, amount = 2))).quote

    assertEquals(400L, quote.fee)
    assertEquals(CoachTimetable.latestArrival(2, 20.0, now), quote.latestArrival)
  }

  @Test
  fun `a parcel to the office it is handed in at arrives at once`() {
    every { offices.homeOf(2, "Ironhill") } returns 1
    every { roads.km(1, 1) } returns 0.0

    val quote = assertIs<Outcome.Quoted>(service.quote(ashfen, "Mira", goods(weight = 100))).quote

    assertEquals(now, quote.latestArrival)
  }

  @Test
  fun `a parcel on the road cannot be collected`() {
    every { parcels.findLockedById(7) } returns parcel(latestArrival = now.plusSeconds(60))

    val refusal = service.collect(ParcelService.Customer(RECIPIENT, 99L), ashfen, 7)

    assertEquals(OpError.PARCEL_NOT_ARRIVED, refusal?.code)
  }

  @Test
  fun `a parcel is collected only near the office it went to`() {
    every { parcels.findLockedById(7) } returns parcel(latestArrival = now, destination = 5)
    every { offices.kmBetween(1, 5) } returns ParcelService.COLLECT_RANGE_KM + 1

    val refusal = service.collect(ParcelService.Customer(RECIPIENT, 99L), ashfen, 7)

    assertEquals(Outcome.Refused(OpError.PARCEL_NOT_HERE, listOf("Office 5")), refusal)
    verify(exactly = 0) { inventory.moveFromEscrow(any(), any()) }
  }

  private fun parcel(latestArrival: Instant, destination: Int = 1): Parcel {
    return Parcel(
      senderMasterId = 3, senderName = "Torvald", recipientMasterId = RECIPIENT, originOffice = 9,
      destinationOffice = destination, weightUnits = 100, freightFee = 10, coachOnly = false, sentAt = now,
      latestArrival = latestArrival,
    )
  }

  private companion object {
    const val COIN = 27L
    const val APPLE = 4L
    const val RECIPIENT = 10L
  }
}
