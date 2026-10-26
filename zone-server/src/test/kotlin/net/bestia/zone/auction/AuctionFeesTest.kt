package net.bestia.zone.auction

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class AuctionFeesTest {

  @Test
  fun `a fixed-price listing costs a tenth of the asking price`() {
    assertEquals(100L, AuctionFees.listingFee(1_000L))
  }

  @Test
  fun `the fee rounds up to whole coins`() {
    assertEquals(2L, AuctionFees.listingFee(11L))
  }

  @Test
  fun `asking for a single coin still costs one`() {
    assertEquals(1L, AuctionFees.listingFee(1L))
  }
}
