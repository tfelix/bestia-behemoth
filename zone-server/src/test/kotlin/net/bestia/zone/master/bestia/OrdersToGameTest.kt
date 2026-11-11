package net.bestia.zone.master.bestia

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import net.bestia.zone.ai.profile.AiConfig
import net.bestia.zone.ai.profile.BestiaJob
import net.bestia.zone.ai.profile.OrderOverrides
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.message.SMSG
import net.bestia.zone.session.ConnectionInfoService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class OrdersToGameTest {

  private val sessions = mockk<ConnectionInfoService>()
  private val out = mockk<OutMessageProcessor>(relaxed = true)
  private val relay = OrdersToGame(sessions, out)

  private val stored = StoredOrders(
    AiConfig(BestiaJob.GATHER, areaPlaceId = 11, overrides = OrderOverrides(areaRadius = 20, haulAtLoadPct = 50)),
    presetId = 7,
    ownOverrides = OrderOverrides(areaRadius = 20),
  )

  private fun changed() = BestiaAiConfigChangedEvent(this, accountId = 1, masterId = 2, playerBestiaId = 5, stored = stored)

  @Test
  fun `a master who plays gets the orders with their preset and places`() {
    every { sessions.isPlaying(1, 2) } returns true
    val sent = slot<SMSG>()

    relay.onOrdersChanged(changed())

    verify { out.sendToPlayer(1, capture(sent)) }
    val wire = sent.captured.toBnetEnvelope().bestiaAiConfig
    assertEquals(5L, wire.playerBestiaId)
    assertEquals(7L, wire.presetId)
    assertEquals(11L, wire.areaPlaceId)
    assertFalse(wire.hasChestPlaceId())
    assertEquals(50, wire.overrides.haulAtLoadPct)
    assertFalse(wire.ownOverrides.hasHaulAtLoadPct())
    assertEquals(20, wire.ownOverrides.areaRadius)
  }

  @Test
  fun `a master who is not playing gets nothing`() {
    every { sessions.isPlaying(1, 2) } returns false

    relay.onOrdersChanged(changed())

    verify(exactly = 0) { out.sendToPlayer(any(), any<SMSG>()) }
  }
}
