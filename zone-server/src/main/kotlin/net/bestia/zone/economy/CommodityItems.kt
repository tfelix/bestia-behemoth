package net.bestia.zone.economy

import net.bestia.zone.item.ItemRepository
import org.springframework.stereotype.Service

/**
 * The join between a commodity and the item a player actually carries.
 *
 * Its own bean because the two halves are filled in at different times: the commodity catalogue is a yml
 * file read at construction, and the item table is written by a `CommandLineRunner` after the context is
 * up. Resolved on first use rather than in a listener, so nothing depends on which listener runs first.
 */
@Service
class CommodityItems(
  private val catalogue: EconomyCatalogue,
  private val items: ItemRepository,
) {

  private val byItemId: Map<Long, Commodity> by lazy {
    catalogue.commodities()
      .mapNotNull { commodity -> items.findByIdentifier(commodity.item)?.let { it.id to commodity } }
      .toMap()
  }

  private val itemIdByCommodity: Map<String, Long> by lazy {
    byItemId.entries.associate { (itemId, commodity) -> commodity.id to itemId }
  }

  /** Null for an item this economy does not price, which is most of the catalogue. */
  fun commodityOf(itemId: Long): Commodity? {
    return byItemId[itemId]
  }

  fun itemIdOf(commodity: String): Long? {
    return itemIdByCommodity[commodity]
  }

  /** Every priced item, for the boot checks that have to reason over the whole set. */
  fun priced(): Map<Long, Commodity> {
    return byItemId
  }

  /** What a player pays with. Absent only from a catalogue that has not imported `items.yml` yet. */
  fun coinItemId(): Long? {
    return items.findByIdentifier(COIN)?.id
  }

  companion object {
    const val COIN = "gold_coin"
  }
}
