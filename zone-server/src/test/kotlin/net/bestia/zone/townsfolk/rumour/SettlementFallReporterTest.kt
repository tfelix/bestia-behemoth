package net.bestia.zone.townsfolk.rumour

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import net.bestia.worldgen.civ.SettlementTier
import net.bestia.worldgen.core.WorldConfig
import net.bestia.worldgen.vector.Vec2d
import net.bestia.zone.world.WorldService
import net.bestia.zone.world.settlement.SettlementFates
import net.bestia.zone.world.settlement.SettlementSite
import net.bestia.zone.world.settlement.SettlementSiteIndex
import org.junit.jupiter.api.Test

class SettlementFallReporterTest {

  private val rumours = mockk<RumourService>(relaxed = true)
  private val sites = mockk<SettlementSiteIndex> {
    every { siteOf(TOWN) } returns SettlementSite(TOWN, Vec2d(300.0, 400.0), SettlementTier.TOWN, null, emptyList())
  }
  private val worldService = mockk<WorldService> {
    every { config } returns WorldConfig(seed = 1L, widthCells = 8, heightCells = 8)
  }

  @Test
  fun `a town that falls is news from where it stood`() {
    val listener = slot<(Int) -> Unit>()
    val fates = mockk<SettlementFates> { every { onFell(capture(listener)) } returns Unit }
    SettlementFallReporter(rumours, sites, worldService, fates)

    listener.captured(TOWN)

    verify { rumours.post(RumourKind.TOWN_FELL, 300, 400, any(), any()) }
  }

  private companion object {
    const val TOWN = 9
  }
}
