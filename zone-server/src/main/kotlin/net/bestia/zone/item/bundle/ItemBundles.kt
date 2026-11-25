package net.bestia.zone.item.bundle

import net.bestia.zone.item.container.InventoryService
import net.bestia.zone.item.container.LooseInstanceDisposal
import net.bestia.zone.item.container.ReservedItem
import net.bestia.zone.item.persistence.ItemBundle
import net.bestia.zone.item.persistence.ItemBundleRepository
import net.bestia.zone.item.persistence.ItemContainer
import net.bestia.zone.item.persistence.ItemInstance
import net.bestia.zone.item.persistence.ItemInstanceRepository
import net.bestia.zone.item.persistence.ItemRepository
import net.bestia.zone.item.persistence.findByIdentifierOrThrow
import net.bestia.zone.util.PlayerBestiaId
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** Packs a whole bag into one item and unpacks it again, so a player picks up many items with one click. */
@Service
class ItemBundles(
  private val bundles: ItemBundleRepository,
  private val itemRepository: ItemRepository,
  private val itemInstances: ItemInstanceRepository,
  private val inventoryService: InventoryService,
  private val looseInstances: LooseInstanceDisposal,
) {

  /**
   * Takes every item out of [bag], worn ones too, and packs them into a new bundle that sits in no container yet.
   *
   * @return the bundle's instance, or null when [bag] held nothing to pack
   */
  @Transactional
  fun packAll(bag: ItemContainer): ItemInstance? {
    bag.equipped().keys.forEach { bag.unequip(it) }

    return pack(bag.removeAllFree())
  }

  /**
   * Packs [detached], which their container has already given up, into a new bundle that sits in no container yet.
   *
   * @return the bundle's instance, or null when there was nothing to pack
   */
  @Transactional
  fun pack(detached: List<ItemContainer.DetachedItem>): ItemInstance? {
    val items = detached.flatMap(::unpackedNested)
    if (items.isEmpty()) {
      return null
    }

    val instance = itemInstances.save(ItemInstance(item = itemRepository.findByIdentifierOrThrow(ITEM_IDENTIFIER)))
    val bundle = ItemBundle(instance)
    items.forEach { add(bundle.container, it) }
    instance.heldWeight = bundle.container.slots.sumOf { it.weight * it.amount }
    bundles.save(bundle)

    return instance
  }

  /**
   * Moves everything in the bundle [bundleInstanceId] into the bag of its holder and destroys the bundle. The
   * holder's bag has already given the bundle up.
   *
   * @return what arrived, for the live inventory
   */
  @Transactional
  fun unpack(bundleInstanceId: Long, masterId: Long, playerBestiaId: PlayerBestiaId?): List<ReservedItem> {
    val bundle = bundles.findByItemInstanceId(bundleInstanceId) ?: return emptyList()
    val arrived = inventoryService.moveFromEscrow(bundle.container, masterId, playerBestiaId)
    looseInstances.destroy(bundleInstanceId)

    return arrived
  }

  /** A bundle never holds another: a nested one gives up its items instead. */
  private fun unpackedNested(item: ItemContainer.DetachedItem): List<ItemContainer.DetachedItem> {
    val nestedId = item.instance?.takeIf { item.template.identifier == ITEM_IDENTIFIER }?.id ?: return listOf(item)
    val nested = bundles.findByItemInstanceId(nestedId) ?: return emptyList()
    val items = nested.container.removeAllFree()
    looseInstances.destroy(nestedId)

    return items
  }

  private fun add(to: ItemContainer, item: ItemContainer.DetachedItem) {
    if (item.instance != null) {
      to.addInstance(item.instance)
    } else {
      to.addStackable(item.template, item.amount, item.provenance)
    }
  }

  companion object {
    const val ITEM_ID = 626L
    const val ITEM_IDENTIFIER = "item_bundle"
  }
}
