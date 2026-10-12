package net.bestia.zone.item.stat

import net.bestia.zone.item.persistence.ContainerSlot
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component

/**
 * The [ItemStatSheet] of every item, held in memory for the tick thread.
 *
 * Filled once by [net.bestia.zone.boot.ItemImporterBootRunner] from `items.yml` and never written again, the
 * same convention as [net.bestia.zone.crafting.RecipeRegistry]. Not stored in the item table: nothing but the
 * tick reads it, and the YAML is the source either way.
 */
@Component
class ItemStatCatalog {

  @Volatile
  private var byItemId: Map<Long, ItemStatSheet> = emptyMap()

  fun load(sheets: Map<Long, ItemStatSheet>) {
    byItemId = sheets

    LOG.debug { "Item stat catalog loaded ${sheets.count { it.value.stats.isNotEmpty() }} item(s) with stats" }
  }

  /** A copy's level from what it carries, for a copy that has none stored. */
  fun itemLevelOf(itemId: Long, rolled: List<StatValue> = emptyList(), sockets: Int = 0): Int {
    val sheet = sheetOf(itemId)

    return ItemLevel.of(sheet.baseLevel, sheet.fixedStats() + rolled, sockets)
  }

  fun itemLevelOf(slot: ContainerSlot): Int {
    return slot.storedItemLevel ?: itemLevelOf(slot.template.id, slot.rolledStats, slot.slots)
  }

  /** [ItemStatSheet.NONE] for an item that declares nothing, which is most of them. */
  fun sheetOf(itemId: Long): ItemStatSheet {
    return byItemId[itemId] ?: ItemStatSheet.NONE
  }

  private companion object {
    val LOG = KotlinLogging.logger { }
  }
}
