package net.bestia.zone.message

import net.bestia.zone.ecs.ActivePlayerAOIService
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.socket.OutMessageHandler
import net.bestia.zone.world.stream.InterestRange
import org.springframework.stereotype.Component

@Component
class OutMessageProcessor(
  private val playerAOIService: ActivePlayerAOIService,
  private val outMessageHandler: OutMessageHandler,
  private val interestRange: InterestRange,
) {

  fun sendToAllPlayersInRange(pos: Vec3L, msgs: Collection<SMSG>) {
    val accountIdsInRange = playerAOIService.queryEntitiesInCube(pos, interestRange.cubeEdge)

    accountIdsInRange.forEach { accountIdInRange ->
      msgs.forEach { msg -> sendToPlayer(accountIdInRange, msg) }
    }
  }

  fun sendToAllPlayersInRange(pos: Vec3L, msg: SMSG) {
    val accountIdsInRange = playerAOIService.queryEntitiesInCube(pos, interestRange.cubeEdge)

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
}