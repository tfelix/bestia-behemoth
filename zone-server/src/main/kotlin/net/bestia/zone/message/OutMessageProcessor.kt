package net.bestia.zone.message

import net.bestia.zone.ecs.ActivePlayerAOIService
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.ecs.visibility.EntityAudience
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.socket.OutMessageHandler
import net.bestia.zone.util.EntityId
import net.bestia.zone.world.stream.InterestRange
import org.springframework.stereotype.Component

@Component
class OutMessageProcessor(
  private val playerAOIService: ActivePlayerAOIService,
  private val outMessageHandler: OutMessageHandler,
  private val interestRange: InterestRange,
  private val outbox: TickOutbox,
  private val entityAudience: EntityAudience,
) {

  /**
   * Sends a one-off event about [entityId], such as a hit or a chat line, to the accounts its component state
   * goes to. Tick thread only: the audience is read from the world.
   */
  fun sendToObserversOf(world: WorldView, entityId: EntityId, msg: SMSG) {
    val audience = world.read { entityAudience.of(this, entityId).toList() }

    audience.forEach { accountId -> sendToPlayer(accountId, msg) }
  }

  fun sendToAllPlayersInRange(pos: Vec3L, msgs: Collection<SMSG>) {
    if (msgs.isEmpty()) return

    val accountIdsInRange = playerAOIService.queryEntitiesInCube(pos, interestRange.cubeEdge)

    accountIdsInRange.forEach { accountIdInRange -> sendToPlayer(accountIdInRange, msgs) }
  }

  fun sendToAllPlayersInRange(pos: Vec3L, msg: SMSG) {
    val accountIdsInRange = playerAOIService.queryEntitiesInCube(pos, interestRange.cubeEdge)

    accountIdsInRange.forEach { accountIdInRange ->
      sendToPlayer(accountIdInRange, msg)
    }
  }

  /**
   * Sends to every connected account, whether or not it has picked a master yet.
   *
   * For the handful of things that are a property of the world rather than of a place in it - the world clock
   * jumping, today. Everything else should be going through [sendToObserversOf], because a message
   * nobody sees is a message nobody needed.
   *
   * @return how many accounts it went to
   */
  fun sendToAllConnected(msg: SMSG): Int {
    val accountIds = outMessageHandler.connectedAccountIds

    accountIds.forEach { accountId -> sendToPlayer(accountId, msg) }

    return accountIds.size
  }

  /** On the tick this joins the account's batch for the tick, see [TickOutbox]; elsewhere it goes out now. */
  fun sendToPlayer(playerId: Long, msg: SMSG) {
    if (outbox.offer(playerId, listOf(msg))) return

    outMessageHandler.sendMessage(playerId, msg)
  }

  /** One flush for the batch rather than one per message; see [OutMessageHandler.sendMessages]. */
  fun sendToPlayer(playerId: Long, msgs: Collection<SMSG>) {
    if (outbox.offer(playerId, msgs)) return

    outMessageHandler.sendMessages(playerId, msgs)
  }

  /** Whether [playerId] would receive a message sent now; see [OutMessageHandler.isConnected]. */
  fun isPlayerConnected(playerId: Long): Boolean = outMessageHandler.isConnected(playerId)
}
