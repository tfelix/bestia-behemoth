package net.bestia.zone.master.net

import net.bestia.zone.master.bestia.OrdersWire
import net.bestia.bnet.proto.SetBestiaAiConfigCmsgProto
import net.bestia.zone.ai.profile.AiConfig
import net.bestia.zone.message.CMSG
import net.bestia.zone.util.PlayerBestiaId

/**
 * A player setting the orders of one of their own bestias.
 *
 * [orders] is null when the client sent an unset or unknown job or courage step, which the handler refuses: there
 * is no "nearest valid job", so it is rejected rather than guessed at. With [presetId] the bestia follows that preset
 * and the numbers of [orders] are only its own.
 */
data class SetBestiaAiConfigCMSG(
  override val playerId: Long,
  val playerBestiaId: PlayerBestiaId,
  val orders: AiConfig?,
  val presetId: Long? = null,
) : CMSG {

  companion object {
    fun fromBnet(
      accountId: Long,
      msg: SetBestiaAiConfigCmsgProto.SetBestiaAiConfigCMSG,
    ): SetBestiaAiConfigCMSG {
      val orders = OrdersWire.ordersFrom(
        msg.job, msg.courage, msg.fallbackJob, msg.overrides, msg.rulesList,
        areaPlaceId = msg.takeIf { it.hasAreaPlaceId() }?.areaPlaceId,
        chestPlaceId = msg.takeIf { it.hasChestPlaceId() }?.chestPlaceId,
      )

      return SetBestiaAiConfigCMSG(
        playerId = accountId,
        playerBestiaId = msg.playerBestiaId,
        orders = orders,
        presetId = msg.takeIf { it.hasPresetId() }?.presetId,
      )
    }
  }
}
