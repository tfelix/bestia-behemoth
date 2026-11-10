package net.bestia.zone.auction

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import net.bestia.zone.persistence.AsyncJobExecutor
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TradeLinksTest {

  private val jobs = mockk<AsyncJobExecutor>().also { executor ->
    val job = slot<() -> Unit>()
    every { executor.submit(any(), capture(job)) } answers { }
  }
  private val links = TradeLinks(mockk(relaxed = true), jobs)
  private val ada = TradeLinks.Partner(1L, "Ada")
  private val bo = TradeLinks.Partner(2L, "Bo")
  private val cy = TradeLinks.Partner(3L, "Cy")

  @Test
  fun `a link joins both owners and only them`() {
    links.link(ada, bo)
    links.link(bo, cy)

    assertEquals(setOf(bo), links.partnersOf(ada.masterId))
    assertEquals(setOf(ada, cy), links.partnersOf(bo.masterId))
    assertFalse(links.isLinked(ada.masterId, cy.masterId), "links are direct")
  }

  @Test
  fun `cutting a link ends it for both`() {
    links.link(ada, bo)

    links.unlink(bo.masterId, ada.masterId)

    assertTrue(links.partnersOf(ada.masterId).isEmpty())
    assertTrue(links.partnersOf(bo.masterId).isEmpty())
  }

  @Test
  fun `a house shows its owner's houses and its partners' houses`() {
    val houses = PlayerHouses(mockk(relaxed = true), mockk(relaxed = true), jobs, links)
    houses.remember(row(10L, ada.masterId))
    houses.remember(row(11L, ada.masterId))
    houses.remember(row(20L, bo.masterId))
    houses.remember(row(30L, cy.masterId))
    links.link(ada, bo)

    assertEquals(setOf(10L, 11L, 20L), houses.sharingWith(houses.of(10L)!!))
  }

  private fun row(structureId: Long, ownerMasterId: Long): net.bestia.zone.auction.persistence.PlayerAuctionHouse {
    return net.bestia.zone.auction.persistence.PlayerAuctionHouse(structureId, ownerMasterId, 7L, "Owner", 5, null)
  }
}
