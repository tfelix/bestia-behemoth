package net.bestia.zone.item.net

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.bnet.proto.EnvelopeProto.Envelope.MessageCase
import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.entity.ecs.DeadActionGuard
import net.bestia.zone.item.container.InventoryService
import net.bestia.zone.item.container.ItemHolder
import net.bestia.zone.item.container.ReservedItem
import net.bestia.zone.item.ecs.Equipment
import net.bestia.zone.item.ecs.CarryCheck
import net.bestia.zone.item.ecs.ChestStorage
import net.bestia.zone.item.ecs.Inventory
import net.bestia.zone.item.ecs.ItemOwner
import net.bestia.zone.item.ecs.ItemTemplateRegistry
import net.bestia.zone.item.ecs.grantToLive
import net.bestia.zone.message.OperationErrorSMSG
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.message.TickMessageHandler
import net.bestia.zone.message.decoder
import net.bestia.zone.persistence.AsyncJobExecutor
import net.bestia.zone.session.ConnectionInfoService
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/**
 * Hands a held item from one of a master's own entities to another standing next to it, or into or out of a chest
 * the master built.
 *
 * The goods leave the giver's live bag on the tick that checks the move, so from then on nothing else - a second
 * move, a drop, a craft - can reach them. The durable move follows as one transaction on the master's job key,
 * the key every other write to these containers uses. Only that transaction failing gives the goods back: then
 * the database never changed. When it finds the giver no longer holds them, the live bag showed more than the
 * database had, and they stay gone. At no point do both bags hold the same goods.
 *
 * Neither entity has to be the one the player drives, so both ids come from the message - and each is checked
 * to be this account's master, one of its bestias or one of its chests before anything is touched.
 */
@Component
class TransferItemHandler(
  private val inventoryService: InventoryService,
  private val connectionInfoService: ConnectionInfoService,
  private val deadActionGuard: DeadActionGuard,
  private val asyncJobExecutor: AsyncJobExecutor,
  private val outMessageProcessor: OutMessageProcessor,
  private val worldView: WorldView,
  private val itemTemplates: ItemTemplateRegistry,
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

    val taken = held.portion(msg.amount)
    inventory.take(msg.playerItemId, msg.amount)

    val queued = asyncJobExecutor.trySubmit(masterId) {
      transferDurably(msg, taken, masterId, giver, receiver)
    }
    if (!queued) {
      LOG.warn { "Transfer of item ${taken.itemId} for master $masterId was not queued; the giver keeps it" }
      inventory.addItem(taken)
    }

    return true
  }

  private fun transferDurably(
    msg: TransferItemCMSG,
    taken: Inventory.Item,
    masterId: Long,
    giver: ItemHolder,
    receiver: ItemHolder,
  ) {
    val moved: ReservedItem? = try {
      inventoryService.transferBetweenOwners(
        masterId = masterId,
        from = giver,
        to = receiver,
        itemId = taken.itemId,
        uniqueId = taken.uniqueId,
        amount = taken.amount,
        provenance = taken.provenance,
      )
    } catch (e: Exception) {
      LOG.error(e) { "Transfer of item ${taken.itemId} for master $masterId rolled back; the giver gets it back" }
      worldView.modify(msg.fromEntityId) { id -> get(id, Inventory::class)?.addItem(taken) }
      return
    }

    if (moved == null) {
      LOG.warn {
        "Entity ${msg.fromEntityId} showed ${taken.amount}x item ${taken.itemId} its container does not freely hold; " +
                "nothing is transferred"
      }
      return
    }

    worldView.grantToLive(msg.toEntityId, listOf(moved))
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

  /** A detached copy of [amount] of this held item, to give back or to name to the database. */
  private fun Inventory.Item.portion(amount: Int): Inventory.Item {
    return Inventory.Item(
      itemId = itemId,
      amount = amount,
      weight = weight,
      uniqueId = uniqueId,
      stackable = stackable,
      durability = durability,
      maxDurability = maxDurability,
      slots = slots,
      upgradeLevel = upgradeLevel,
      itemLevel = itemLevel,
      rolledStats = rolledStats,
      provenance = provenance,
    )
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
