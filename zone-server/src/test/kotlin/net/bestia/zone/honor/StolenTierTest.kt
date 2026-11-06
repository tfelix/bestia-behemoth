package net.bestia.zone.honor

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class StolenTierTest {

  @Test
  fun `the tier follows the item level bands of the items page`() {
    assertEquals(
      listOf(StolenTier.MUNDANE, StolenTier.SUPERIOR, StolenTier.SUPERIOR, StolenTier.RARE, StolenTier.LEGENDARY),
      listOf(20, 21, 50, 80, 120).map { StolenTier.of(it) },
    )
  }
}
