package net.bestia.zone.master.net

import net.bestia.zone.ai.profile.OrderRule
import net.bestia.bnet.proto.SetBestiaAiConfigCmsgProto.SetBestiaAiConfigCMSG.Rule
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
  fun `reads rules in their order`() {
    val orders = ordersOf(
      Wire.newBuilder().setJob(Job.REST)
        .addRules(Rule.newBuilder().setCondition(Rule.Condition.NIGHT).setAction(Rule.Action.REST_AT_HOME))
        .addRules(Rule.newBuilder().setCondition(Rule.Condition.STAMINA_BELOW).setThreshold(30).setAction(Rule.Action.EAT))
    )

    assertEquals(
      listOf(
        OrderRule(OrderRule.Condition.NIGHT, null, OrderRule.Action.REST_AT_HOME),
        OrderRule(OrderRule.Condition.STAMINA_BELOW, 30, OrderRule.Action.EAT),
      ),
      orders?.rules,
    )
  }

  @Test
  fun `a rule that pairs a condition with an action it does not allow refuses the orders`() {
    val wire = Wire.newBuilder().setJob(Job.REST)
      .addRules(Rule.newBuilder().setCondition(Rule.Condition.NIGHT).setAction(Rule.Action.HAUL_NOW))

    assertNull(ordersOf(wire))
  }

  @Test
  fun `a fallback the main job cannot take refuses the orders`() {
    assertNull(ordersOf(Wire.newBuilder().setJob(Job.GUARD).setFallbackJob(Job.GATHER)))
  }

  @Test
  fun `reads the preset and the named places`() {
    val msg = SetBestiaAiConfigCMSG.fromBnet(
      1L,
      Wire.newBuilder().setPlayerBestiaId(5).setCourage(Courage.STEADY).setJob(Job.GATHER)
        .setPresetId(7).setAreaPlaceId(11).setChestPlaceId(12).build(),
    )

    assertEquals(7L, msg.presetId)
    assertEquals(11L, msg.orders?.areaPlaceId)
    assertEquals(12L, msg.orders?.chestPlaceId)
  }

  @Test
  fun `unset preset and places are none`() {
    val msg = SetBestiaAiConfigCMSG.fromBnet(1L, Wire.newBuilder().setPlayerBestiaId(5).setCourage(Courage.STEADY).setJob(Job.GATHER).build())

    assertNull(msg.presetId)
    assertNull(msg.orders?.areaPlaceId)
    assertNull(msg.orders?.chestPlaceId)
  }
}
