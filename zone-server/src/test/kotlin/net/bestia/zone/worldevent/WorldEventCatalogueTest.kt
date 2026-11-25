package net.bestia.zone.worldevent

import net.bestia.zone.world.mana.ManaTier
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class WorldEventCatalogueTest {

  private class Named(override val script: String) : WorldEvent {
    override fun start(signals: ProvinceSignals) = null
  }

  @Test
  fun `the apex roamer is a small event of ominous provinces`() {
    val catalogue = WorldEventCatalogue(listOf(Named("ApexRoamer"))).also { it.load() }

    val apex = catalogue.of("apex-roamer")!!
    assertEquals(1.0, apex.cost)
    assertEquals(ManaTier.OMINOUS, apex.minTier)
  }

  @Test
  fun `an event nobody runs fails the boot`() {
    assertThrows<IllegalArgumentException> { WorldEventCatalogue(emptyList()).load() }
  }
}
