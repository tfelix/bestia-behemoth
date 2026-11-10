package net.bestia.zone.treasure

import net.bestia.zone.cartography.chart.ChartService
import net.bestia.zone.item.container.InventoryService
import net.bestia.zone.item.persistence.Item
import net.bestia.zone.item.persistence.ItemInstanceRepository
import net.bestia.zone.item.persistence.ItemRepository
import net.bestia.zone.treasure.persistence.TreasureMapRecord
import net.bestia.zone.treasure.persistence.TreasureMapRecordRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** The database half of treasure maps, each in one transaction. Called from async jobs, never on the tick. */
@Service
class TreasureMapMinter(
  private val inventoryService: InventoryService,
  private val itemRepository: ItemRepository,
  private val itemInstanceRepository: ItemInstanceRepository,
  private val records: TreasureMapRecordRepository,
) {

  class Minted(val record: TreasureMapRecord, val item: Item, val blankItemId: Long)

  /** Spends a blank and draws the map onto a new copy, or null when the master holds no blank any more. */
  @Transactional
  fun mint(masterId: Long, cacheId: Long, centreX: Double, centreY: Double, radius: Double): Minted? {
    val blank = itemRepository.findByIdentifier(ChartService.BLANK_IDENTIFIER) ?: return null
    val map = itemRepository.findByIdentifier(TREASURE_MAP) ?: return null
    inventoryService.removeOneFromMaster(masterId, blank.id, 1) ?: return null

    val instance = inventoryService.mintInstanceForMaster(masterId, map)
    val record = records.save(TreasureMapRecord(instance.id, cacheId, centreX, centreY, radius))
    return Minted(record, map, blank.id)
  }

  /** Every copy turns into a crumbled map wherever it is held, and its circle is gone. */
  @Transactional
  fun crumble(copies: Collection<Long>) {
    val crumbled = itemRepository.findByIdentifier(CRUMBLED_TREASURE_MAP) ?: return
    itemInstanceRepository.retemplate(copies, crumbled)
    records.deleteAllById(copies)
  }

  companion object {
    const val TREASURE_MAP = "treasure_map"
    const val CRUMBLED_TREASURE_MAP = "crumbled_treasure_map"
  }
}
