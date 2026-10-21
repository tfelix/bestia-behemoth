package net.bestia.zone.companion.query

import net.bestia.bus.contract.ZoneAnswer
import net.bestia.zone.account.persistence.PlayerBestiaRepository
import net.bestia.zone.item.persistence.ContainerSlot
import net.bestia.zone.item.persistence.ItemContainer
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** What a master and each of its bestias carry. Read from the database, which every inventory write goes through. */
@Service
class InventoryQueries(
  private val owned: OwnedMasters,
  private val playerBestias: PlayerBestiaRepository,
) {

  @Transactional(readOnly = true)
  fun bag(accountId: Long, masterId: Long): ZoneAnswer.Bag? {
    val master = owned.find(accountId, masterId) ?: return null

    val holders = listOf(holder("MASTER", master.id, master.name, master.container)) +
      playerBestias.findAllByMasterId(masterId).sortedBy { it.id }.map { holder("BESTIA", it.id, it.name, it.container) }

    return ZoneAnswer.Bag(holders)
  }

  private fun holder(kind: String, id: Long, name: String?, container: ItemContainer): ZoneAnswer.Bag.Holder {
    return ZoneAnswer.Bag.Holder(kind, id, name, container.slots.sortedBy { it.id }.map { slot(it) })
  }

  private fun slot(slot: ContainerSlot): ZoneAnswer.Bag.Slot {
    return ZoneAnswer.Bag.Slot(
      itemId = slot.template.id,
      item = slot.template.identifier,
      amount = slot.amount,
      equippedIn = slot.equippedIn?.name,
      durability = slot.durability,
      maxDurability = slot.maxDurability,
      upgradeLevel = slot.itemInstance?.upgradeLevel ?: 0,
    )
  }
}
