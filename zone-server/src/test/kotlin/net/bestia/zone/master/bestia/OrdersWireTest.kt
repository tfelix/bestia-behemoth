package net.bestia.zone.master.bestia

import net.bestia.bnet.proto.SetBestiaAiConfigCmsgProto.SetBestiaAiConfigCMSG.Job
import net.bestia.zone.ai.profile.AiConfig
import net.bestia.zone.ai.profile.BestiaJob
import net.bestia.zone.ai.profile.Courage
import net.bestia.zone.ai.profile.OrderOverrides
import net.bestia.zone.ai.profile.OrderRule
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class OrdersWireTest {

  @Test
  fun `a preset's sheet reads back as the same orders`() {
    val orders = AiConfig(
      BestiaJob.GATHER, Courage.BOLD, BestiaJob.CRAFT,
      OrderOverrides(retreatBelowHealthPct = 0, areaRadius = 20),
      listOf(OrderRule(OrderRule.Condition.NIGHT, null, OrderRule.Action.REST_AT_HOME)),
      areaPlaceId = 11,
      chestPlaceId = 12,
    )

    assertEquals(orders, OrdersWire.ordersFrom(OrdersWire.sheetOf(orders)))
  }

  @Test
  fun `a sheet whose fallback the main job cannot take is refused`() {
    val sheet = OrdersWire.sheetOf(AiConfig(BestiaJob.GUARD)).toBuilder().setFallbackJob(Job.GATHER).build()

    assertNull(OrdersWire.ordersFrom(sheet))
  }
}
