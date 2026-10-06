package net.bestia.zone.control

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.bnet.proto.EnvelopeProto.Envelope.MessageCase
import net.bestia.zone.ai.ecs.PlayerControlled
import net.bestia.zone.aoi.ActivePlayerAOIService
import net.bestia.zone.identity.ecs.ActivePlayer
import net.bestia.zone.battle.ecs.attack.AttackCancelService
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.core.modify
import net.bestia.zone.session.ConnectionInfoService
import net.bestia.zone.session.EntityNotOwnedSessionException
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.message.TickMessageHandler
import net.bestia.zone.message.decoder
import net.bestia.zone.util.AccountId
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/**
 * Hands the player's control to another entity they own - one of their bestia, or their master again.
 *
 * Three things move together: the session's active entity (what every handler acts on), [PlayerControlled]
 * (what the AI leaves alone) and [ActivePlayer] (what chunk streaming, visibility and spawning follow). The
 * client only switches once [ActiveEntitySMSG] arrives.
 */
@Component
class SelectEntityHandler(
  private val connectionInfoService: ConnectionInfoService,
  private val attackCancelService: AttackCancelService,
  private val playerAOIService: ActivePlayerAOIService,
  private val outMessageProcessor: OutMessageProcessor,
) : TickMessageHandler<SelectEntityCMSG> {
  override val wire = decoder(MessageCase.SELECT_ACTIVE_ENTITY) { accountId, envelope ->
    SelectEntityCMSG(accountId, envelope.selectActiveEntity.entityId)
  }

  override fun handle(world: World, msg: SelectEntityCMSG): Boolean {
    // Read before switching: this is the entity that is about to stop being driven and start looking after
    // itself again.
    val previous = runCatching { connectionInfoService.getActiveEntityId(msg.playerId) }.getOrNull()

    try {
      connectionInfoService.activateEntity(msg.playerId, msg.entityId)
    } catch (e: EntityNotOwnedSessionException) {
      LOG.warn { "Can not select entity, no entity ${msg.entityId} found for player ${msg.playerId}" }
      return false
    }

    if (previous != msg.entityId) {
      moveControlMarker(world, from = previous, to = msg.entityId)
      moveViewAnchor(world, msg.playerId, from = previous, to = msg.entityId)
    }

    outMessageProcessor.sendToPlayer(msg.playerId, ActiveEntitySMSG(msg.entityId))

    return true
  }

  /**
   * Hands the [PlayerControlled] marker over, which is what stops the AI thinking for whichever creature the
   * player is actually driving while letting the one they just left resume its standing order.
   *
   * Done here rather than inside `ConnectionInfoService` because that service is a pure session map with no
   * access to the world, and giving it one would couple session bookkeeping to the ECS. Each side keeps its own
   * notion of "active" and this handler is the seam that already knows about both.
   */
  private fun moveControlMarker(world: World, from: EntityId?, to: EntityId) {
    from?.let { previous ->
      world.modify(previous) { id -> remove(id, PlayerControlled::class) }
      // Its standing attack order was the player's, not its own, and AiActSystem will not be looking after
      // it either - AttackSystem does not care whether an entity is player-controlled.
      attackCancelService.cancelAttack(world, previous)
    }
    world.modify(to) { id -> add(id, PlayerControlled) }
  }

  /**
   * The player index is re-seated here because `ZoneEngine` only updates it when the anchor's position
   * changes, and the new one may be standing still.
   */
  private fun moveViewAnchor(world: World, accountId: AccountId, from: EntityId?, to: EntityId) {
    from?.let { previous -> world.modify(previous) { id -> remove(id, ActivePlayer::class) } }

    val at = world.modify(to) { id ->
      add(id, ActivePlayer)
      get(id, Position::class)?.toVec3L()
    }
    at?.let { playerAOIService.setEntityPosition(accountId, it) }
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
