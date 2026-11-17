package net.bestia.zone.blackboard

import net.bestia.zone.blackboard.ecs.BlackboardOffer
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BlackboardOfferTest {

  @Test
  fun `each open level is one bit, in the low mask up to 64 and the high mask above`() {
    val offer = BlackboardOffer()

    offer.offer(listOf(1, 5, 64, 65, 80))

    assertEquals((1L shl 0) or (1L shl 4) or (1L shl 63), offer.levelsLow)
    assertEquals((1L shl 0) or (1L shl 15), offer.levelsHigh)
  }

  @Test
  fun `an unchanged offer does not sync again`() {
    val offer = BlackboardOffer()
    offer.offer(listOf(10))
    offer.clearDirty()

    offer.offer(listOf(10, 10))
    assertFalse(offer.isDirty())

    offer.offer(emptyList())
    assertTrue(offer.isDirty())
  }
}
