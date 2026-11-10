package net.bestia.zone.auction.persistence

import io.mockk.mockk
import net.bestia.zone.auction.AuctionCategory
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals

class AuctionListingBidTest {

  private val listing = AuctionListing(
    sellerMasterId = 1, sellerName = "Ada", itemId = 4, amount = 1, itemLevel = 1,
    category = AuctionCategory.CONSUMABLE, price = 100, townName = "Oakhold", expiresAt = Instant.EPOCH,
    container = mockk(), isAuction = true,
  )

  @Test
  fun `the first bid must reach the starting bid`() {
    assertEquals(100, listing.nextMinBid)
  }

  @Test
  fun `each next bid is five percent higher, rounded up, and at least one coin`() {
    listing.currentBid = 101
    assertEquals(107, listing.nextMinBid)

    listing.currentBid = 10
    assertEquals(11, listing.nextMinBid)
  }
}
