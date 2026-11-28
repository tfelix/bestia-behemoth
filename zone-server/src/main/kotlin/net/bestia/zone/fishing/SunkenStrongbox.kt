package net.bestia.zone.fishing

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.battle.attack.ThreadLocalRandomSource
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.item.bundle.ItemBundles
import net.bestia.zone.item.ecs.Inventory
import net.bestia.zone.item.ecs.ItemTemplateRegistry
import net.bestia.zone.item.persistence.Item
import net.bestia.zone.item.stat.ItemRoll
import net.bestia.zone.item.stat.ItemStatCatalog
import net.bestia.zone.item.stat.ItemStatRoller
import net.bestia.zone.persistence.AsyncJobExecutor
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Service
import java.util.Random
import kotlin.math.min

/**
 * The rarest thing a line brings up: a barnacled strongbox with one piece of gear sealed inside. The gear is chosen
 * and rolled the moment it is hauled up, from the level band of the country round the water, so a box traded on
 * keeps what it was fished up with.
 */
@Service
class SunkenStrongbox(
  private val catalogue: FishingCatalogue,
  private val items: ItemTemplateRegistry,
  private val stats: ItemStatCatalog,
  private val roller: ItemStatRoller,
  private val bundles: ItemBundles,
  private val asyncJobExecutor: AsyncJobExecutor,
  private val worldView: WorldView,
  private val random: Random = ThreadLocalRandomSource,
) {

  /** One piece of gear, chosen and rolled. */
  data class Contents(val itemId: Long, val roll: ItemRoll)

  /** Whether this strike hauls up a strongbox rather than what the table would give. */
  fun isHauled(): Boolean {
    return random.nextDouble() < catalogue.sunkenChestChance
  }

  /**
   * The gear for a box fished where the wild bestias are [levels]: up to [FishingCatalogue.sunkenChestLevelsAbove]
   * above the band, never above [FishingCatalogue.sunkenChestMaxLevel]. Null when no gear fits at all.
   */
  fun contentsFor(levels: IntRange): Contents? {
    val range = gearLevels(levels, catalogue.sunkenChestLevelsAbove, catalogue.sunkenChestMaxLevel)
    val candidates = items.all()
      .filter { it.type == Item.ItemType.EQUIP && it.equipSlot != null && it.toolKind == null }
      .filter { !it.bound && !it.noviceOnly && it.baseLevel in range }
      .map { it.id }
      .sorted()
    if (candidates.isEmpty()) {
      return null
    }

    val itemId = candidates[random.nextInt(candidates.size)]
    return Contents(itemId, roller.roll(stats.sheetOf(itemId), range.last))
  }

  /** Seals [contents] into a new strongbox in the master's bag, off the tick, and shows it in the live bag. */
  fun haulUp(entityId: EntityId, masterId: Long, contents: Contents) {
    val boxId = items.idOf(ITEM_IDENTIFIER) ?: return
    val boxWeight = items.weightOf(boxId) ?: 0
    val contentWeight = items.weightOf(contents.itemId) ?: 0

    asyncJobExecutor.submit(key = masterId) {
      val box = bundles.sealForMaster(masterId, boxId, contents.itemId, contents.roll)
      LOG.info { "Master $masterId fished up strongbox ${box.id} holding item ${contents.itemId}" }

      worldView.modify(entityId) { id ->
        get(id, Inventory::class)?.addItem(
          Inventory.Item(boxId, amount = 1, weight = boxWeight + contentWeight, uniqueId = box.id, stackable = false)
        )
      }
    }
  }

  companion object {
    private val LOG = KotlinLogging.logger { }

    const val ITEM_IDENTIFIER = "barnacled_strongbox"

    /** `barnacled_strongbox` in items.yml. */
    const val ITEM_ID = 797L

    /**
     * The levels a box's gear may have: from the band's floor up to [above] over its top, held under [max]. A band
     * that starts above [max] (corrupted ground) keeps the top [above] levels under it.
     */
    fun gearLevels(levels: IntRange, above: Int, max: Int): IntRange {
      val top = min(levels.last + above, max)
      val bottom = min(levels.first, top - above).coerceAtLeast(1)

      return bottom..top
    }
  }
}
