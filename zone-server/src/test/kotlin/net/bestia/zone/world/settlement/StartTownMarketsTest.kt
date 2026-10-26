package net.bestia.zone.world.settlement

import io.mockk.every
import io.mockk.mockk
import net.bestia.worldgen.civ.BuildingFunction
import net.bestia.worldgen.civ.SettlementSpawnPoints
import net.bestia.zone.world.GeneratedWorlds
import net.bestia.zone.world.MasterSpawnPointService
import net.bestia.zone.world.WorldService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class StartTownMarketsTest {

  private val generated = GeneratedWorlds.of(42L)

  private val worldService: WorldService = mockk<WorldService>().also {
    every { it.generated } returns generated
    every { it.config } returns generated.config
  }

  private val siteIndex = SettlementSiteIndex(worldService)
  private val sut = StartTownMarkets(worldService, siteIndex)

  @Test
  fun `only the start towns' market halls are auction houses`() {
    val startTowns = SettlementSpawnPoints.choose(generated, MasterSpawnPointService.HOMES_HELD)
      .take(MasterSpawnPointService.HOMES_OFFERED)
      .map { it.settlementIndex }
      .toSet()

    val halls = generated.world.chronicle.settlements.indices
      .mapNotNull { siteIndex.siteOf(it) }
      .flatMap { site -> site.buildingsOf(BuildingFunction.MARKET).map { site.index to it.propId } }

    assertTrue(halls.any { it.first in startTowns }, "no start town has a market hall, so this proves nothing")
    assertTrue(halls.any { it.first !in startTowns }, "every market is in a start town, so this proves nothing")

    for ((settlement, propId) in halls) {
      assertEquals(settlement in startTowns, sut.isAuctionHouse(propId), "market hall of settlement $settlement")
    }
  }
}
