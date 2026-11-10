package net.bestia.zone.postal.office

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import net.bestia.zone.account.persistence.Master
import net.bestia.zone.account.persistence.MasterRepository
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.persistence.AsyncJobExecutor
import net.bestia.zone.postal.LetterService
import net.bestia.zone.postal.persistence.Parcel
import net.bestia.zone.postal.persistence.ParcelRepository
import net.bestia.zone.settlement.TownFellEvent
import org.junit.jupiter.api.Test
import org.springframework.transaction.TransactionStatus
import org.springframework.transaction.support.TransactionCallback
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant
import kotlin.test.assertEquals

class PlayerOfficeClosureTest {

  private val lost = PostOffices.PLAYER_OFFICE_BASE + 4
  private val successor = 12
  private val now = Instant.parse("2026-11-10T12:00:00Z")

  private val offices = mockk<PostOffices> {
    every { successorOf(lost) } returns successor
    every { officeOf(successor) } returns PostOffices.Office(successor, "Ashfen", 2)
  }
  private val parcels = mockk<ParcelRepository>()
  private val masters = mockk<MasterRepository>()
  private val letters = mockk<LetterService>(relaxed = true)
  private val jobs = mockk<AsyncJobExecutor>().also { executor ->
    val job = slot<() -> Unit>()
    every { executor.submit(any(), capture(job)) } answers { job.captured() }
  }
  private val transactions = mockk<TransactionTemplate>().also { template ->
    every { template.execute<Any?>(any()) } answers {
      firstArg<TransactionCallback<Any?>>().doInTransaction(mockk<TransactionStatus>(relaxed = true))
    }
  }

  private val closure = PlayerOfficeClosure(offices, masters, parcels, letters, jobs, transactions)

  @Test
  fun `mail waiting at the lost office and on its way to it goes to the nearest other office`() {
    val waiting = parcel(origin = lost, destination = 3)
    val inSack = parcel(origin = lost, destination = 3).also { it.sackInstanceId = 77L }
    val inbound = parcel(origin = 3, destination = lost)
    every { parcels.findByOriginOfficeOrDestinationOffice(lost, lost) } returns listOf(waiting, inSack, inbound)
    every { masters.findAllByHomePostOffice(lost) } returns emptyList()

    closure.onFell(TownFellEvent(this, 4L, "Oakhold", Vec3L(100, 100, 0)))

    assertEquals(successor, waiting.originOffice)
    assertEquals(lost, inSack.originOffice, "a sack on the road keeps going")
    assertEquals(successor, inbound.destinationOffice)
  }

  @Test
  fun `residents get the nearest office as home and a pigeon to say so`() {
    val resident = mockk<Master>(relaxed = true) { every { id } returns 9L }
    every { parcels.findByOriginOfficeOrDestinationOffice(lost, lost) } returns emptyList()
    every { masters.findAllByHomePostOffice(lost) } returns listOf(resident)

    closure.onFell(TownFellEvent(this, 4L, "Oakhold", Vec3L(100, 100, 0)))

    verify { resident.homePostOffice = successor }
    verify { letters.postNotice(9L, any(), any(), match { "Ashfen" in it }) }
  }

  private fun parcel(origin: Int, destination: Int): Parcel {
    return Parcel(
      senderMasterId = 3, senderName = "Torvald", recipientMasterId = 10, originOffice = origin,
      destinationOffice = destination, weightUnits = 100, freightFee = 10, coachOnly = false, sentAt = now,
      latestArrival = now.plusSeconds(3600),
    )
  }
}
