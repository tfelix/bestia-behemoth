package net.bestia.zone.bestiastorage

import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.item.container.InventoryService
import net.bestia.zone.item.container.ReservedItem
import net.bestia.zone.item.ecs.grantToLive
import net.bestia.zone.item.persistence.ItemRepository
import net.bestia.zone.item.persistence.findByIdentifierOrThrow
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Service

/** A keeper gives every master one Bestia Ledger, for free. Call it off the tick. */
@Service
class BestiaLedgers(
  private val ledgerAccess: BestiaLedgerScript,
  private val itemRepository: ItemRepository,
  private val inventoryService: InventoryService,
  private val worldView: WorldView,
) {

  fun handOutIfMissing(masterId: Long, masterEntityId: EntityId) {
    if (worldView.read { ledgerAccess.grants(this, masterEntityId) }) {
      return
    }

    val template = itemRepository.findByIdentifierOrThrow(ITEM_IDENTIFIER)
    val ledger = inventoryService.mintInstanceForMaster(masterId, template)
    val handedOut = ReservedItem(
      offerSlotId = 0L, itemId = template.id, amount = 1, weight = template.weight, uniqueId = ledger.id,
      stackable = false, durability = 0, maxDurability = 0, slots = 0, upgradeLevel = 0,
      itemLevel = template.baseLevel, rolledStats = emptyList(),
    )
    worldView.grantToLive(masterEntityId, listOf(handedOut))
  }

  private companion object {
    const val ITEM_IDENTIFIER = "bestia_ledger"
  }
}
