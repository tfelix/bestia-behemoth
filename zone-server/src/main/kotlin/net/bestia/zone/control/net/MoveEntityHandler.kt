package net.bestia.zone.control.net

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.bnet.proto.EnvelopeProto.Envelope.MessageCase
import net.bestia.zone.battle.ecs.attack.AttackCancelService
import net.bestia.zone.battle.ecs.skill.CastCancelService
import net.bestia.zone.control.ClientLeg
import net.bestia.zone.control.MoveRequestRateLimit
import net.bestia.zone.ecs.core.World
import net.bestia.zone.entity.ecs.DeadActionGuard
import net.bestia.zone.logout.ecs.LogoutCancelService
import net.bestia.zone.message.TickMessageHandler
import net.bestia.zone.message.decoder
import net.bestia.zone.movement.ecs.Path
import net.bestia.zone.session.ConnectionInfoService
import net.bestia.zone.travel.JourneyOrder
import net.bestia.zone.travel.JourneyService
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/**
 * Moves one of the player's own entities: stops it, or sends it on a journey through the waypoints.
 *
 * Any owned entity, not only the one being driven: selecting another one and moving it is already open to a
 * client, so naming the entity grants nothing new. What the server owns is every step - the journey plans them,
 * and the client's own first steps are only walked when each one is valid; see [ClientLeg].
 */
@Component
class MoveEntityHandler(
  private val connectionInfoService: ConnectionInfoService,
  private val logoutCancelService: LogoutCancelService,
  private val castCancelService: CastCancelService,
  private val attackCancelService: AttackCancelService,
  private val deadActionGuard: DeadActionGuard,
  private val rateLimit: MoveRequestRateLimit,
  private val journeys: JourneyService,
  private val clientLeg: ClientLeg,
) : TickMessageHandler<MoveEntityCMSG> {
  override val wire = decoder(MessageCase.MOVE_ENTITY) { accountId, envelope ->
    MoveEntityCMSG.fromBnet(accountId, envelope.moveEntity)
  }

  override fun handle(world: World, msg: MoveEntityCMSG): Boolean {
    val id = msg.entityId

    if (!isOwnedBy(msg.playerId, id)) {
      LOG.warn { "Account ${msg.playerId} tried to move entity $id, which it does not own" }
      return true
    }

    // Before the cancels: a corpse expresses no intent, so it must not even abort its own logout.
    if (deadActionGuard.refuses(world, id, "move")) return true

    cancelWhatMovingInterrupts(world, msg.playerId, id)

    // After the cancels, so a spammed click still counts as intent and cannot be used to keep a cast alive.
    if (!rateLimit.spend(msg.playerId)) {
      LOG.debug { "Dropping move for account ${msg.playerId}: over its request rate" }
      return true
    }

    when {
      msg.waypoints.isEmpty() -> stop(world, id)
      msg.append && journeys.append(world, id, msg.waypoints) -> Unit
      startJourney(world, msg) -> clientLeg.adopt(world, id, msg.firstLeg)
    }

    return true
  }

  /**
   * Any move counts as activity and aborts a pending logout, even the empty stop the logout's Cancel button
   * sends. Casting and crafting are stationary, and walking away is how a fight is called off.
   */
  private fun cancelWhatMovingInterrupts(world: World, accountId: Long, id: EntityId) {
    runCatching { connectionInfoService.getActiveEntityId(accountId) }.getOrNull()
      ?.let { logoutCancelService.cancelLogout(world, it) }
    castCancelService.cancelCast(world, id)
    castCancelService.cancelCraft(world, id)
    attackCancelService.cancelAttack(world, id)
  }

  /** @return whether it set off */
  private fun startJourney(world: World, msg: MoveEntityCMSG): Boolean {
    val name = msg.name.trim().take(MAX_NAME_LENGTH).ifEmpty { null }
    return journeys.start(world, msg.entityId, msg.waypoints, JourneyOrder.PLAYER, name, msg.savedRouteId) == null
  }

  private fun stop(world: World, id: EntityId) {
    journeys.cancel(world, id)
    world.remove(id, Path::class)
  }

  private fun isOwnedBy(accountId: Long, id: EntityId): Boolean {
    val masterId = connectionInfoService.activeMasterIdOf(accountId) ?: return false
    if (connectionInfoService.getSelectedMasterEntityId(accountId) == id) return true
    return connectionInfoService.getOwnedEntitiesByMaster(accountId, masterId).any { it.entityId == id }
  }

  private companion object {
    private val LOG = KotlinLogging.logger { }

    /** Matches what a saved route may be called. */
    const val MAX_NAME_LENGTH = 40
  }
}
