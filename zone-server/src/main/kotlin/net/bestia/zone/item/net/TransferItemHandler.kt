package net.bestia.zone.item.net

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.bnet.proto.EnvelopeProto.Envelope.MessageCase
import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.ecs.core.World
import net.bestia.zone.entity.ecs.DeadActionGuard
import net.bestia.zone.item.container.ItemHandover
import net.bestia.zone.item.container.ItemHandover.Party
import net.bestia.zone.item.ecs.Equipment
import net.bestia.zone.item.ecs.CarryCheck
import net.bestia.zone.item.ecs.ChestStorage
import net.bestia.zone.item.ecs.ExchangeReach
import net.bestia.zone.item.ecs.Inventory
import net.bestia.zone.item.ecs.ItemOwner
import net.bestia.zone.item.ecs.ItemTemplateRegistry
import net.bestia.zone.message.OperationErrorSMSG
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.message.TickMessageHandler
import net.bestia.zone.message.decoder
import net.bestia.zone.session.ConnectionInfoService
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/**
 * Hands a held item from one of a master's own entities to another standing next to it, or into or out of a chest
 * the master built.
 *
 * The goods leave the giver's live bag on the tick that checks the move; [ItemHandover] says what follows.
 *
 * Neither entity has to be the one the player drives, so both ids come from the message - and each is checked
 * to be this account's master, one of its bestias or one of its chests before anything is touched.
 */
@Component
class TransferItemHandler(
  private val connectionInfoService: ConnectionInfoService,
  private val deadActionGuard: DeadActionGuard,
  private val outMessageProcessor: OutMessageProcessor,
  private val itemTemplates: ItemTemplateRegistry,
  private val handover: ItemHandover,
) : TickMessageHandler<TransferItemCMSG> {

  override val wire = decoder(MessageCase.TRANSFER_ITEM) { accountId, envelope ->
    TransferItemCMSG.fromBnet(accountId, envelope.transferItem)
  }

  override fun handle(world: World, msg: TransferItemCMSG): Boolean {
    if (msg.amount <= 0 || msg.fromEntityId == msg.toEntityId) {
      LOG.warn { "Invalid transfer of ${msg.amount} from ${msg.fromEntityId} to ${msg.toEntityId} by account ${msg.playerId}" }
      return true
    }

    val masterId = connectionInfoService.getMasterId(msg.playerId)
    val giver = ItemOwner.holderOf(world, msg.fromEntityId, masterId)
    val receiver = ItemOwner.holderOf(world, msg.toEntityId, masterId)
    if (giver == null || receiver == null) {
      LOG.warn { "Account ${msg.playerId} tried to move goods between ${msg.fromEntityId} and ${msg.toEntityId}, not both its own" }
      return true
    }

    if (deadActionGuard.refuses(world, msg.fromEntityId, "hand over an item") ||
      deadActionGuard.refuses(world, msg.toEntityId, "receive an item")
    ) {
      return true
    }

    if (!ExchangeReach.within(world, msg.fromEntityId, msg.toEntityId)) {
      outMessageProcessor.sendToPlayer(msg.playerId, OperationErrorSMSG(OpError.EXCHANGE_OUT_OF_REACH))
      return true
    }

    val inventory = world.get(msg.fromEntityId, Inventory::class)
    val held = inventory?.byPlayerItemId(msg.playerItemId)
    if (inventory == null || held == null || !mayHandOver(held, world.get(msg.fromEntityId, Equipment::class), msg.amount)) {
      LOG.warn { "Entity ${msg.fromEntityId} does not freely hold ${msg.amount}x of item ${msg.playerItemId} to hand over" }
      resendBags(world, msg)
      return true
    }
    if (itemTemplates.isBound(held.itemId)) {
      outMessageProcessor.sendToPlayer(msg.playerId, OperationErrorSMSG(OpError.ITEM_BOUND))
      return true
    }

    val noRoom = noRoomFor(world, msg.toEntityId, held, msg.amount)
    if (noRoom != null) {
      outMessageProcessor.sendToPlayer(msg.playerId, OperationErrorSMSG(noRoom))
      return true
    }

    handover.hand(
      world, masterId, Party(msg.fromEntityId, giver), Party(msg.toEntityId, receiver), msg.playerItemId, msg.amount
    )

    return true
  }

  /** A chest counts slots, everything else carries by weight. */
  private fun noRoomFor(world: World, receiverId: EntityId, held: Inventory.Item, amount: Int): OpError? {
    val chest = world.get(receiverId, ChestStorage::class)
    if (chest != null) {
      val contents = world.get(receiverId, Inventory::class) ?: return OpError.CHEST_FULL

      return if (chest.hasRoomFor(contents, held)) null else OpError.CHEST_FULL
    }

    return if (CarryCheck.fits(world, receiverId, held.weight * amount)) null else OpError.EXCHANGE_TOO_HEAVY
  }

  /** Worn gear stays on until it is taken off, and a copy that is its own item moves one at a time. */
  private fun mayHandOver(held: Inventory.Item, equipment: Equipment?, amount: Int): Boolean {
    val worn = held.equipped || equipment?.isWorn(held.uniqueId) == true
    val wholeCopies = held.isStackable || amount == 1

    return !worn && wholeCopies && held.amount >= amount
  }

  /** The client asked for something its bags do not show any more; send it both again rather than guess. */
  private fun resendBags(world: World, msg: TransferItemCMSG) {
    world.get(msg.fromEntityId, Inventory::class)?.markDirty()
    world.get(msg.toEntityId, Inventory::class)?.markDirty()
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
