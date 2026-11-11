package net.bestia.zone.master.bestia

import net.bestia.zone.account.persistence.PlayerBestia
import net.bestia.zone.ai.profile.AiConfig
import net.bestia.zone.ai.profile.OrderOverrides

/** A bestia's orders as they are in force, and where they come from: its own, or a preset plus its own numbers. */
data class StoredOrders(
  val orders: AiConfig,
  val presetId: Long?,
  val ownOverrides: OrderOverrides,
) {
  companion object {
    fun of(playerBestia: PlayerBestia) =
      StoredOrders(playerBestia.aiConfig, playerBestia.orderPresetId, playerBestia.ownOverrides)
  }
}
