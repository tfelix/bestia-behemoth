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

  @Test
  fun `an owner fee rounds up and half of it, rounded down, reaches the till`() {
    val ownerFee = AuctionFees.ownerFee(price = 1001, percent = 5)

    assertEquals(51, ownerFee)
    assertEquals(25, AuctionFees.tillShare(ownerFee))
  }

  @Test
  fun `a start town house charges no owner fee`() {
    assertEquals(0, AuctionFees.ownerFee(price = 1000, percent = 0))
  }

  @Test
  fun `each Trade Post Owner level allows five percent more`() {
    assertEquals(5, AuctionFees.maxOwnerFeePercent(1))
    assertEquals(25, AuctionFees.maxOwnerFeePercent(5))
  }

  @Test
  fun `an auction's fee grows with how long it runs`() {
    assertEquals(60, AuctionFees.listingFee(1000, days = 1))
    assertEquals(100, AuctionFees.listingFee(1000, days = 2))
    assertEquals(200, AuctionFees.listingFee(1000, days = 5))
  }

  @Test
  fun `only known lengths and a buyout above the start are valid terms`() {
    assertEquals(true, ListingTerms(100, days = 5, buyout = 101).isValid)
    assertEquals(false, ListingTerms(100, days = 3).isValid)
    assertEquals(false, ListingTerms(100, days = 1, buyout = 100).isValid)
    assertEquals(false, ListingTerms(100, days = 0, buyout = 200).isValid)
  }
}
