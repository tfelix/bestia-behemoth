package net.bestia.zone.control.net

import net.bestia.zone.entity.ecs.VisualKind
import net.bestia.zone.entity.ecs.EntityVisual
import net.bestia.zone.entity.StaticEntityKind
import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.bnet.proto.EnvelopeProto.Envelope.MessageCase
import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.crafting.CraftingService
import net.bestia.zone.townsfolk.conversation.TalkService
import net.bestia.zone.identity.ecs.Account
import net.bestia.zone.entity.ecs.DeadActionGuard
import net.bestia.zone.prop.ecs.construction.Building
import net.bestia.zone.prop.ecs.construction.ConstructionSite
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.core.modify
import net.bestia.zone.session.ConnectionInfoService
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.entity.ecs.PlayerStructureIdentity
import net.bestia.zone.townsfolk.ecs.Townsfolk
import net.bestia.zone.message.OperationErrorSMSG
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.message.TickMessageHandler
import net.bestia.zone.message.decoder
import net.bestia.zone.util.EntityId
import net.bestia.zone.prop.PlayerStructureService
import net.bestia.zone.prop.PropInteraction
import net.bestia.zone.entity.ecs.StaticVisual
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
  private val talkService: TalkService,
  private val crafting: CraftingService,
  private val outMessageProcessor: OutMessageProcessor,
  propInteractions: List<PropInteraction>,
) : TickMessageHandler<InteractEntityCMSG> {

  private val propInteractionByKind = propInteractions.associateBy { it.kind }

  override val wire = decoder(MessageCase.INTERACT_ENTITY) { accountId, envelope ->
    InteractEntityCMSG.fromBnet(accountId, envelope.interactEntity)
  }

  override fun handle(world: World, msg: InteractEntityCMSG): Boolean {
    val actorId = connectionInfoService.getActiveEntityId(msg.playerId)

    if (deadActionGuard.refuses(world, actorId, "interact")) {
      return true
    }

    val refusal = world.modify(actorId) { id -> interact(this, id, msg.targetEntityId) }

    refusal?.let { outMessageProcessor.sendToPlayer(msg.playerId, OperationErrorSMSG(it)) }

    return true
  }

  /**
   * A prop names its kind on its [StaticVisual]. A structure that lives as an ordinary entity, like a street stall,
   * names it on its [EntityVisual] instead.
   */
  private fun kindOf(world: World, targetId: EntityId): StaticEntityKind? {
    world.get(targetId, StaticVisual::class)?.let { return it.kind }
    val visual = world.get(targetId, EntityVisual::class)?.takeIf { it.kind == VisualKind.STRUCTURE } ?: return null

    return StaticEntityKind.entries.getOrNull(visual.id.toInt())
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

    // Ahead of the structures: a chest or a Mana Circle is one, but neither is a crafting station.
    val propInteraction = kindOf(world, targetId)?.let(propInteractionByKind::get)
    if (propInteraction != null) {
      return propInteraction.interact(world, actorId, targetId)
    }

    // The component every player-built structure carries and nothing else does, so this needs no second
    // list of which kinds a player can build.
    val structure = world.get(targetId, PlayerStructureIdentity::class)
    if (structure != null) {
      val accountId = world.get(actorId, Account::class)?.accountId ?: return null
      val masterEntityId = connectionInfoService.getSelectedMasterEntityId(accountId)
      crafting.openAtStructure(world, actorId, masterEntityId, structure.structureId)
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
