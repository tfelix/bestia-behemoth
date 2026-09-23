package net.bestia.zone.economy.shop

import io.mockk.every
import io.mockk.mockk
import net.bestia.zone.dialog.conversation.Speaker
import net.bestia.zone.dialog.conversation.SpeakerResolver
import net.bestia.zone.economy.EconomyCatalogue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Which counter deals in what. The prices are the town's either way, so this is what stops a settlement
 * reading as one shop wearing several faces.
 */
class MerchantStockTest {

  private val catalogue = EconomyCatalogue().apply { load() }
  private val speakers = mockk<SpeakerResolver>()
  private val stock = MerchantStock(catalogue, speakers)

  @Test
  fun `a general store holds everything, because it produces none of it`() {
    assertEquals(catalogue.commodities().map { it.id }.toSet(), stock.of(keeperOf("general_store")))
  }

  @Test
  fun `a miller deals in what it takes as well as what it makes`() {
    assertEquals(setOf("grain", "flour"), stock.of(keeperOf("miller")))
  }

  @Test
  fun `a baker takes flour and sells bread, and has no grain to offer`() {
    val offered = stock.of(keeperOf("baker"))

    assertEquals(setOf("flour", "bread"), offered)
    assertTrue("grain" !in offered!!, "a bakery was selling raw grain")
  }

  @Test
  fun `somebody who keeps no trade keeps no shop`() {
    assertNull(stock.of(keeperOf(null)), "a townsperson with no business was given a counter")
  }

  @Test
  fun `a trade that produces no commodity has nothing to sell`() {
    // A temple is a service, in economy.yml's own words: inventing an abstract good to hang off it
    // would buy an item nobody can hold.
    assertNull(stock.of(keeperOf("temple")))
  }

  @Test
  fun `an entity that is not a townsperson at all is not a merchant`() {
    every { speakers.of(NOBODY) } returns null

    assertNull(stock.of(NOBODY))
  }

  private fun keeperOf(business: String?): Long {
    val entityId = business.hashCode().toLong()
    every { speakers.of(entityId) } returns mockk<Speaker>().also { every { it.business } returns business }

    return entityId
  }

  private companion object {
    const val NOBODY = 999L
  }
}
