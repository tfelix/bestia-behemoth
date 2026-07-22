package net.bestia.zone.item.equip.script

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.item.Item
import org.springframework.stereotype.Component

/**
 * Resolves the `script` name on an [Item.ItemType.EQUIP] item (e.g. `ShoesScript`) to the
 * [EquipmentScript] bean implementing it - identical shape to
 * [net.bestia.zone.battle.status.StatusEffectScriptRegistry].
 *
 * On top of the name lookup it also resolves scripts straight by **item id**. That mapping is not
 * derivable from the beans alone (it lives in `items.yml`), so it is injected once at boot by
 * [net.bestia.zone.boot.EquipmentScriptBinderBootRunner] via [bind]. It matters because
 * [net.bestia.zone.ecs.battle.effects.StatusValueRecalcSystem] needs a script for every worn item
 * on the tick thread, where a repository round trip per item is not acceptable.
 */
@Component
class EquipmentScriptRegistry(
  scripts: List<EquipmentScript>
) {

  private val byName: Map<String, EquipmentScript> = scripts
    .mapNotNull { script -> script::class.simpleName?.let { it to script } }
    .toMap()

  @Volatile
  private var byItemId: Map<Long, EquipmentScript> = emptyMap()

  /**
   * Binds the equip items that actually declare a script to their [EquipmentScript]. Items without
   * a script (plain gear with no stat effect) are simply absent from the mapping.
   */
  fun bind(equipItems: List<Item>) {
    byItemId = equipItems
      .mapNotNull { item -> item.script?.let { name -> byName[name]?.let { item.id to it } } }
      .toMap()

    LOG.info { "Registered ${byName.size} equipment script(s): ${byName.keys.sorted()}, bound to ${byItemId.size} item(s)" }
  }

  fun get(scriptName: String): EquipmentScript? = byName[scriptName]

  /** The script of the worn item [itemId], or null when that item has no stat effect at all. */
  fun getByItemId(itemId: Long): EquipmentScript? = byItemId[itemId]

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
