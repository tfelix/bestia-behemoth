package net.bestia.zone.postal.courier

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.economy.CommodityItems
import net.bestia.zone.economy.WorldReserve
import net.bestia.zone.entity.GameMetrics.CoinSource
import net.bestia.zone.item.container.InventoryService
import net.bestia.zone.item.enchant.ActiveItemEnchantment
import net.bestia.zone.item.enchant.ItemEnchantmentId
import net.bestia.zone.item.persistence.Item
import net.bestia.zone.item.persistence.ItemInstance
import net.bestia.zone.item.persistence.ItemRepository
import net.bestia.zone.postal.office.PostOffices
import net.bestia.zone.postal.parcel.ParcelService.Outcome
import net.bestia.zone.postal.persistence.Parcel
import net.bestia.zone.postal.persistence.ParcelRepository
import net.bestia.zone.postal.seal.PostalSeal
import org.junit.jupiter.api.Test
import org.springframework.transaction.TransactionStatus
import org.springframework.transaction.support.TransactionCallback
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CourierServiceTest {

  private val now = Instant.parse("2026-11-06T12:00:00Z")

  private val parcels = mockk<ParcelRepository>(relaxed = true)
  private val offices = mockk<PostOffices>().also { offices ->
    every { offices.officeOf(any()) } answers { PostOffices.Office(firstArg(), "Office ${firstArg<Int>()}", 2) }
  }
  private val inventory = mockk<InventoryService>(relaxed = true)
  private val sackTemplate = mockk<Item>(relaxed = true) { every { id } returns SACK }
  private val items = mockk<ItemRepository> { every { findByIdentifier(CourierService.MAIL_SACK) } returns sackTemplate }
  private val reserve = mockk<WorldReserve>(relaxed = true)
  private val world = mockk<WorldView>(relaxed = true).also { view ->
    every { view.read<Any?>(any()) } answers { firstArg<World.() -> Any?>().invoke(mockk(relaxed = true)) }
  }
  private val transactions = mockk<TransactionTemplate>().also { template ->
    every { template.execute<Any?>(any()) } answers {
      firstArg<TransactionCallback<Any?>>().doInTransaction(mockk<TransactionStatus>(relaxed = true))
    }
  }

  private val service = CourierService(
    parcels, offices, inventory, items, mockk<CommodityItems> { every { coinItemId() } returns COIN }, reserve, world,
    transactions, Clock.fixed(now, ZoneOffset.UTC),
  )

  private val ashfen = PostOffices.Office(settlement = ASHFEN, name = "Ashfen", level = 2)
  private val ironhill = PostOffices.Office(settlement = IRONHILL, name = "Ironhill", level = 2)
  private val courier = CourierService.Courier(masterId = COURIER, liveEntityId = 77L, freeWeight = 1_000)

  @Test
  fun `the board shows parcels waiting for a courier with its 60 percent, and hides coach-only ones`() {
    val waiting = parcel(fee = 100)
    every { parcels.findByOriginOfficeAndSackInstanceIdIsNullOrderByLatestArrival(ASHFEN) } returns
      listOf(waiting, parcel(coachOnly = true))

    val board = service.routeBoard(ashfen)

    assertEquals(listOf(CourierService.BoardLine(0, "Office $IRONHILL", 200, 60, waiting.latestArrival)), board)
  }

  @Test
  fun `parcels heavier than the courier can carry stay on the board`() {
    every { parcels.findLockedById(1) } returns parcel(weight = 600)
    every { parcels.findLockedById(2) } returns parcel(weight = 600)

    val refused = service.take(courier, ashfen, listOf(1, 2))

    assertEquals(Outcome.Refused(OpError.ROUTE_TOO_HEAVY), refused)
    verify(exactly = 0) { inventory.mintInstanceForMaster(any(), any()) }
  }

  @Test
  fun `taken parcels go into one sealed sack that weighs them`() {
    val first = parcel(weight = 200)
    val second = parcel(weight = 300)
    every { parcels.findLockedById(1) } returns first
    every { parcels.findLockedById(2) } returns second
    val sack = ItemInstance(item = sackTemplate, id = 555L)
    every { inventory.mintInstanceForMaster(COURIER, sackTemplate) } returns sack

    assertNull(service.take(courier, ashfen, listOf(1, 2)))

    assertEquals(500, sack.heldWeight)
    assertEquals(listOf(ActiveItemEnchantment(ItemEnchantmentId.POSTAL_SEAL, PostalSeal.STRENGTH)), sack.enchantments(now))
    assertTrue(listOf(first, second).all { it.sackInstanceId == 555L && it.courierMasterId == COURIER })
  }

  @Test
  fun `parcels to two offices do not share a sack`() {
    every { parcels.findLockedById(1) } returns parcel()
    every { parcels.findLockedById(2) } returns parcel(destination = 99)

    assertEquals(Outcome.Refused(OpError.REQUEST_REFUSED), service.take(courier, ashfen, listOf(1, 2)))
  }

  @Test
  fun `a delivery pays the courier 60 percent out of the reserve and the parcels arrive now`() {
    val carried = parcel(fee = 100).also { it.sackInstanceId = 555L; it.courierMasterId = COURIER }
    every { parcels.findByCourierMasterIdAndDestinationOffice(COURIER, IRONHILL) } returns listOf(carried)

    val delivery = service.deliver(courier, ironhill)

    assertEquals(CourierService.Delivery(parcels = 1, pay = 60), delivery)
    assertTrue(carried.hasArrived(now))
    verify { inventory.destroyInstance(COURIER, 555L) }
    verify { inventory.grantToMaster(COURIER, COIN, 60) }
    verify { reserve.charge(60.0, CoinSource.COURIER) }
  }

  @Test
  fun `a courier with nothing for this office is paid nothing`() {
    every { parcels.findByCourierMasterIdAndDestinationOffice(COURIER, IRONHILL) } returns emptyList()

    assertNull(service.deliver(courier, ironhill))
  }

  private fun parcel(
    weight: Int = 200,
    fee: Long = 40,
    coachOnly: Boolean = false,
    destination: Int = IRONHILL,
  ): Parcel {
    return Parcel(
      senderMasterId = 3, senderName = "Torvald", recipientMasterId = 4, originOffice = ASHFEN,
      destinationOffice = destination, weightUnits = weight, freightFee = fee, coachOnly = coachOnly,
      sentAt = now, latestArrival = now.plusSeconds(3600),
    )
  }

  private companion object {
    const val COIN = 27L
    const val SACK = 100L
    const val COURIER = 8L
    const val ASHFEN = 1
    const val IRONHILL = 2
  }
}
