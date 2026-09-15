package net.bestia.zone.world.prop.interact

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.dialog.DialogId
import net.bestia.zone.dialog.DialogService
import net.bestia.zone.dialog.conversation.TalkService
import net.bestia.zone.ecs.account.Account
import net.bestia.zone.ecs.battle.damage.DeadActionGuard
import net.bestia.zone.ecs.construction.Building
import net.bestia.zone.ecs.construction.ConstructionSite
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.ecs.core.session.ConnectionInfoService
import net.bestia.zone.ecs.movement.Position
import net.bestia.zone.ecs.prop.PlayerStructureIdentity
import net.bestia.zone.ecs.spawn.townsfolk.Townsfolk
import net.bestia.zone.message.InMessageProcessor
import net.bestia.zone.message.OperationErrorSMSG
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.util.EntityId
import net.bestia.zone.world.prop.PlayerStructureService
import org.springframework.stereotype.Component

/**
 * Decides what clicking on something means, from what that something **is**.
 *
 * One handler rather than a message per interaction, which is what makes the click generic: a new
 * interactable kind is a branch here plus whatever it does, not a new `.proto`, a new dispatch arm and a new
 * client send method. The argument bag the message carries is the same one item usage takes, so an
 * interaction that needs the player to choose something first already has somewhere to put it.
 *
 * The acting entity comes from [ConnectionInfoService.getActiveEntityId] and never from the message; the id
 * the client sends names the *target*.
 */
@Component
class InteractEntityHandler(
  private val connectionInfoService: ConnectionInfoService,
  private val deadActionGuard: DeadActionGuard,
  private val dialogService: DialogService,
  private val talkService: TalkService,
  private val outMessageProcessor: OutMessageProcessor,
  private val world: WorldView
) : InMessageProcessor.IncomingMessageHandler<InteractEntityCMSG> {

  override val handles = InteractEntityCMSG::class

  override fun handle(msg: InteractEntityCMSG): Boolean {
    LOG.trace { "RX: $msg" }

    val actorId = connectionInfoService.getActiveEntityId(msg.playerId)

    if (deadActionGuard.refuses(actorId, "interact")) {
      return true
    }

    val refusal = world.modify(actorId) { id -> interact(this, id, msg.targetEntityId) }

    refusal?.let { outMessageProcessor.sendToPlayer(msg.playerId, OperationErrorSMSG(it)) }

    return true
  }

  /** @return the refusal to send back, or null when the click was acted on (or meant nothing). */
  private fun interact(world: World, actorId: EntityId, targetId: EntityId): OpError? {
    if (!world.isAlive(targetId)) {
      LOG.debug { "Entity $actorId interacted with $targetId, which no longer exists" }
      return null
    }

    if (world.get(targetId, ConstructionSite::class) != null) {
      return toggleBuilding(world, actorId, targetId)
    }

    // The component every player-built structure carries and nothing else does, so this needs no second
    // list of which kinds a player can build.
    if (world.has(targetId, PlayerStructureIdentity::class)) {
      // A finished station has nothing to open yet. The dialog is the placeholder for whatever window it
      // eventually gets, so that clicking one answers rather than doing nothing at all.
      world.get(actorId, Account::class)?.accountId?.let { accountId ->
        dialogService.send(accountId, DialogId.WORKBENCH_PLACEHOLDER, sourceEntityId = targetId)
      }
      return null
    }

    // A townsperson: clicking one is how you speak to them. `TalkService` owns the range check and the
    // refusal it sends, so this is the dispatch and nothing more.
    if (world.has(targetId, Townsfolk::class)) {
      world.get(actorId, Account::class)?.accountId?.let { accountId ->
        talkService.open(accountId, actorId, targetId)
      }
      return null
    }

    LOG.debug { "Entity $actorId interacted with $targetId, which has nothing to interact with" }

    return null
  }

  /**
   * Starts work on a site, or stops it if this entity was already building that one.
   *
   * A toggle rather than two messages, because a click is what the player has: clicking the thing you are
   * building is how you stop, the same way clicking away from a target drops it.
   */
  private fun toggleBuilding(world: World, actorId: EntityId, siteId: EntityId): OpError? {
    if (world.get(actorId, Building::class)?.siteEntityId == siteId) {
      world.remove(actorId, Building::class)
      return null
    }

    val actorAt = world.get(actorId, Position::class)?.toVec3L() ?: return OpError.BUILD_OUT_OF_RANGE
    val siteAt = world.get(siteId, Position::class)?.toVec3L() ?: return OpError.BUILD_OUT_OF_RANGE

    if (actorAt.distance(siteAt) > PlayerStructureService.RANGE_TILES) {
      return OpError.BUILD_OUT_OF_RANGE
    }

    world.add(actorId, Building(siteId))

    return null
  }

  private companion object {
    val LOG = KotlinLogging.logger { }
  }
}
