package net.bestia.zone.item.ecs

import net.bestia.zone.battle.gear.ToolKind
import net.bestia.zone.battle.gear.ArmorType
import net.bestia.zone.battle.gear.WeaponType
import net.bestia.zone.battle.Element
import net.bestia.zone.battle.RangedWeapon
import net.bestia.zone.battle.ammo.AmmoType
import net.bestia.zone.item.material.ItemMaterial
import net.bestia.zone.item.persistence.Item
import net.bestia.zone.item.persistence.ItemRepository
import org.springframework.stereotype.Component

/**
 * The parts of the item catalogue the tick thread needs, held in memory.
 *
 * An item template never changes after boot - `ItemImporterBootRunner` writes the table and nothing else
 * touches it - so this is read once and never invalidated. It exists because the callers that need an item
 * fact run on the tick thread, where a JPA round trip would stall the whole simulation.
 *
 * Replaces the `ItemWeightRegistry` this grew out of, which had no callers at all: the weight lookup below is
 * the same one it offered, still unread, and now sitting next to a level lookup that is read on every craft.
 * Keeping it means the next thing that needs a weight off the tick thread has somewhere to ask.
 */
@Component
class ItemTemplateRegistry(
  private val itemRepository: ItemRepository
) {

  data class Template(
    val id: Long,
    val baseLevel: Int,
    val weight: Int,
    val stackable: Boolean,
    val maxDurability: Int,
    val material: ItemMaterial,
    val type: Item.ItemType = Item.ItemType.ETC,
    val ammoType: AmmoType? = null,
    val element: Element? = null,
    val attackRange: Int? = null,
    val twoHanded: Boolean = false,
    val script: String? = null,
    val bound: Boolean = false,
    val weaponType: WeaponType? = null,
    val armorType: ArmorType? = null,
    val toolKind: ToolKind? = null,
  ) {
    val isAmmo: Boolean
      get() {
        return type == Item.ItemType.AMMO
      }

    val rangedWeapon: RangedWeapon?
      get() {
        if (isAmmo || ammoType == null || attackRange == null) {
          return null
        }

        return RangedWeapon(range = attackRange.toLong(), ammoType = ammoType)
      }
  }

  // Lazy: the importer writes the table after this bean exists, so an eager read finds a fresh database empty.
  private val byItemId: Map<Long, Template> by lazy {
    itemRepository.findAll().associate {
      it.id to Template(
        it.id, it.baseLevel, it.weight, it.stackable, it.maxDurability, it.material, it.type, it.ammoType, it.element,
        it.attackRange, it.twoHanded, it.script, it.bound, it.weaponType, it.armorType, it.toolKind
      )
    }
  }

  private val idByIdentifier: Map<String, Long> by lazy {
    itemRepository.findAll().associate { it.identifier to it.id }
  }

  private val identifierById: Map<Long, String> by lazy {
    idByIdentifier.entries.associate { (identifier, id) -> id to identifier }
  }

  fun isBound(itemId: Long): Boolean {
    return templateOf(itemId)?.bound == true
  }

  /** Null for an id the catalogue does not know. */
  fun templateOf(itemId: Long): Template? {
    return byItemId[itemId]
  }

  /** Loads the catalogue now, at boot, so the first lookup on the tick does not reach the database. */
  fun warmUp() {
    byItemId.size
    idByIdentifier.size
    identifierById.size
  }

  /**
   * The item's tier, or null for an id the catalogue does not know.
   *
   * Null rather than a default, because every caller has to decide for itself what an unknown item means -
   * a craft treats it as a refusal, and silently calling it tier 1 would let a broken reference through as
   * the easiest possible item.
   */
  fun baseLevelOf(itemId: Long): Int? = byItemId[itemId]?.baseLevel

  fun weightOf(itemId: Long): Int? = byItemId[itemId]?.weight

  /**
   * The catalogue id behind an `items.yml` identifier, or null when no such item was imported.
   *
   * For the callers that know an item by name rather than by id - a script naming its reagent - and have to
   * resolve it where a `findByIdentifier` round trip cannot go: inside a world scope.
   */
  fun idOf(identifier: String): Long? = idByIdentifier[identifier]

  /** The `items.yml` identifier of [itemId], the name the companion app shows an item by. */
  fun identifierOf(itemId: Long): String? = identifierById[itemId]
}
