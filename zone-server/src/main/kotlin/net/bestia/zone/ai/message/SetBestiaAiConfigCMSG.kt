package net.bestia.zone.ai.message

import net.bestia.bnet.proto.SetBestiaAiConfigCmsgProto
import net.bestia.zone.ai.profile.AiConfig
import net.bestia.zone.ai.profile.IdleStance
import net.bestia.zone.message.CMSG
import net.bestia.zone.util.PlayerBestiaId

/**
 * A player setting the standing order for one of their own bestias.
 *
 * [stance] is null when the client sent `IDLE_STANCE_UNSPECIFIED`, which the handler refuses. An unrecognised
 * stance is the one thing here that cannot be clamped into something sensible — unlike the two numbers, there
 * is no "nearest valid stance" — so it is rejected rather than guessed at.
 */
data class SetBestiaAiConfigCMSG(
  override val playerId: Long,
  val playerBestiaId: PlayerBestiaId,
  val stance: IdleStance?,
  val aggression: Int,
  val fleeThresholdPct: Int,
) : CMSG {

  /** The requested config, with both numbers forced into range. Only valid once [stance] is known non-null. */
  fun toConfig(stance: IdleStance): AiConfig =
    AiConfig(stance = stance, aggression = aggression, fleeThresholdPct = fleeThresholdPct).sanitised()

  companion object {
    fun fromBnet(
      accountId: Long,
      msg: SetBestiaAiConfigCmsgProto.SetBestiaAiConfigCMSG,
    ): SetBestiaAiConfigCMSG = SetBestiaAiConfigCMSG(
      playerId = accountId,
      playerBestiaId = msg.playerBestiaId,
      stance = stanceOf(msg.stance),
      aggression = msg.aggression,
      fleeThresholdPct = msg.fleeThresholdPct,
    )

    /**
     * Maps the wire enum onto the domain one by name, so the two can be extended independently without a
     * hand-maintained branch per stance going stale.
     */
    private fun stanceOf(wire: SetBestiaAiConfigCmsgProto.SetBestiaAiConfigCMSG.IdleStance): IdleStance? =
      when (wire) {
        SetBestiaAiConfigCmsgProto.SetBestiaAiConfigCMSG.IdleStance.IDLE_STANCE_UNSPECIFIED,
        SetBestiaAiConfigCmsgProto.SetBestiaAiConfigCMSG.IdleStance.UNRECOGNIZED -> null

        else -> IdleStance.fromNameOrNull(wire.name)
      }
  }
}
