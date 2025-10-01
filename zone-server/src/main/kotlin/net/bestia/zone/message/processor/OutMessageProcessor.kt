package net.bestia.zone.message.processor

import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.message.SMSG
import net.bestia.zone.socket.OutMessageHandler
import net.bestia.zone.ecs.ActivePlayerAOIService
import org.springframework.stereotype.Component

@Component
class OutMessageProcessor(
  private val playerAOIService: ActivePlayerAOIService,
  private val outMessageHandler: OutMessageHandler,
) {

  fun sendToAllPlayersInRange(pos: Vec3L, msgs: Collection<SMSG>) {
    val accountIdsInRange = playerAOIService.queryEntitiesInCube(pos, UPDATE_RANGE)

    accountIdsInRange.forEach { accountIdInRange ->
      msgs.forEach { msg -> sendToPlayer(accountIdInRange, msg) }
    }
  }

  fun sendToAllPlayersInRange(pos: Vec3L, msg: SMSG) {
    val accountIdsInRange = playerAOIService.queryEntitiesInCube(pos, UPDATE_RANGE)

    accountIdsInRange.forEach { accountIdInRange ->
      sendToPlayer(accountIdInRange, msg)
    }
  }

  fun sendToPlayer(playerId: Long, msg: SMSG) {
    outMessageHandler.sendMessage(playerId, msg)
  }

  fun sendToPlayer(playerId: Long, msgs: Collection<SMSG>) {
    msgs.forEach { msg -> sendToPlayer(playerId, msg) }
  }

  companion object {
    // Range in units (1m = 100units).
    private const val UPDATE_RANGE = 100 * 100L
  }
}