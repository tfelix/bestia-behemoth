package net.bestia.zone.ai.message

import net.bestia.bnet.proto.BestiaAiConfigSmsgProto
import net.bestia.bnet.proto.EnvelopeProto
import net.bestia.bnet.proto.SetBestiaAiConfigCmsgProto
import net.bestia.zone.ai.profile.AiConfig
import net.bestia.zone.message.SMSG
import net.bestia.zone.util.PlayerBestiaId

/**
 * The standing order now actually in force for one bestia.
 *
 * Sent after a successful change rather than assuming the client can predict the result: the numbers are clamped
 * on the way in, so what was asked for and what was stored are not always the same, and the client should show
 * the latter.
 */
data class BestiaAiConfigSMSG(
  val playerBestiaId: PlayerBestiaId,
  val config: AiConfig,
) : SMSG {

  override fun toBnetEnvelope(): EnvelopeProto.Envelope {
    val payload = BestiaAiConfigSmsgProto.BestiaAiConfigSMSG.newBuilder()
      .setPlayerBestiaId(playerBestiaId)
      .setStance(
        SetBestiaAiConfigCmsgProto.SetBestiaAiConfigCMSG.IdleStance.valueOf(config.stance.name)
      )
      .setAggression(config.aggression)
      .setFleeThresholdPct(config.fleeThresholdPct)

    return EnvelopeProto.Envelope.newBuilder()
      .setBestiaAiConfig(payload)
      .build()
  }
}
