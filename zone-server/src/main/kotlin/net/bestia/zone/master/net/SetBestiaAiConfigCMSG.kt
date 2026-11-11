package net.bestia.zone.master.net

import net.bestia.zone.master.bestia.OrdersWire
import net.bestia.bnet.proto.SetBestiaAiConfigCmsgProto
import net.bestia.zone.ai.profile.AiConfig
import net.bestia.zone.ai.profile.BestiaJob
import net.bestia.zone.ai.profile.Courage
import net.bestia.zone.message.CMSG
import net.bestia.zone.util.PlayerBestiaId

/**
 * A player setting the orders of one of their own bestias.
 *
 * [orders] is null when the client sent an unset or unknown job or courage step, which the handler refuses: there
 * is no "nearest valid job", so it is rejected rather than guessed at.
 */
data class SetBestiaAiConfigCMSG(
  override val playerId: Long,
  val playerBestiaId: PlayerBestiaId,
  val orders: AiConfig?,
) : CMSG {

  companion object {
    fun fromBnet(
      accountId: Long,
      msg: SetBestiaAiConfigCmsgProto.SetBestiaAiConfigCMSG,
    ): SetBestiaAiConfigCMSG {
      val job = BestiaJob.fromNameOrNull(msg.job.name)
      val courage = Courage.fromNameOrNull(msg.courage.name)
      val fallback = OrdersWire.fallbackFrom(msg.fallbackJob)
      val rules = OrdersWire.rulesFrom(msg.rulesList)
      val orders = if (job != null && courage != null && fallback.isSuccess && rules != null) {
        AiConfig(job, courage, fallback.getOrNull(), OrdersWire.overridesFrom(msg.overrides), rules).takeIf { it.isValid }
      } else {
        null
      }

      return SetBestiaAiConfigCMSG(playerId = accountId, playerBestiaId = msg.playerBestiaId, orders = orders)
    }
  }
}
