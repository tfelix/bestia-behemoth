package net.bestia.zone.master.net

import net.bestia.bnet.proto.SetBestiaAiConfigCmsgProto.SetBestiaAiConfigCMSG.Courage
import net.bestia.bnet.proto.SetBestiaAiConfigCmsgProto.SetBestiaAiConfigCMSG.Job
import net.bestia.bnet.proto.SetBestiaAiConfigCmsgProto.SetBestiaAiConfigCMSG.Overrides
import net.bestia.bnet.proto.SetBestiaAiConfigCmsgProto.SetBestiaAiConfigCMSG as Wire
import net.bestia.zone.ai.profile.AiConfig
import net.bestia.zone.ai.profile.BestiaJob
import net.bestia.zone.ai.profile.OrderOverrides
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class SetBestiaAiConfigCMSGTest {

  private fun ordersOf(wire: Wire.Builder): AiConfig? {
    return SetBestiaAiConfigCMSG.fromBnet(1L, wire.setPlayerBestiaId(5).setCourage(Courage.STEADY).build()).orders
  }

  @Test
  fun `reads a fallback job and only the overrides that were set`() {
    val orders = ordersOf(
      Wire.newBuilder().setJob(Job.GATHER).setFallbackJob(Job.CRAFT)
        .setOverrides(Overrides.newBuilder().setHaulAtLoadPct(60).setRetreatBelowHealthPct(0))
    )

    assertEquals(BestiaJob.CRAFT, orders?.fallbackJob)
    assertEquals(OrderOverrides(retreatBelowHealthPct = 0, haulAtLoadPct = 60), orders?.overrides)
  }

  @Test
  fun `an unset fallback is none`() {
    assertNull(ordersOf(Wire.newBuilder().setJob(Job.GUARD))?.fallbackJob)
  }

  @Test
  fun `a fallback the main job cannot take refuses the orders`() {
    assertNull(ordersOf(Wire.newBuilder().setJob(Job.GUARD).setFallbackJob(Job.GATHER)))
  }
}
