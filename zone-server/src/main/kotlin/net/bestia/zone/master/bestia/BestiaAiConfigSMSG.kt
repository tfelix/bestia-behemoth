package net.bestia.zone.master.bestia

import net.bestia.bnet.proto.BestiaAiConfigSmsgProto
import net.bestia.bnet.proto.EnvelopeProto
import net.bestia.bnet.proto.SetBestiaAiConfigCmsgProto
import net.bestia.zone.message.SMSG
import net.bestia.zone.util.PlayerBestiaId

/** The orders now in force for one bestia, and where they come from. */
data class BestiaAiConfigSMSG(
  val playerBestiaId: PlayerBestiaId,
  val stored: StoredOrders,
) : SMSG {

  override fun toBnetEnvelope(): EnvelopeProto.Envelope {
    val config = stored.orders
    val payload = BestiaAiConfigSmsgProto.BestiaAiConfigSMSG.newBuilder()
      .setPlayerBestiaId(playerBestiaId)
      .setJob(SetBestiaAiConfigCmsgProto.SetBestiaAiConfigCMSG.Job.valueOf(config.job.name))
      .setCourage(SetBestiaAiConfigCmsgProto.SetBestiaAiConfigCMSG.Courage.valueOf(config.courage.name))
      .setFallbackJob(OrdersWire.fallbackTo(config.fallbackJob))
      .setOverrides(OrdersWire.overridesTo(config.overrides))
      .addAllRules(OrdersWire.rulesTo(config.rules))
      .setOwnOverrides(OrdersWire.overridesTo(stored.ownOverrides))
    stored.presetId?.let(payload::setPresetId)
    config.areaPlaceId?.let(payload::setAreaPlaceId)
    config.chestPlaceId?.let(payload::setChestPlaceId)

    return EnvelopeProto.Envelope.newBuilder()
      .setBestiaAiConfig(payload)
      .build()
  }
}
