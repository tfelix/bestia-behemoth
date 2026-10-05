package net.bestia.zone.ecs.item

import net.bestia.zone.item.ItemRepository
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
    val level: Int,
    val weight: Int,
    val stackable: Boolean,
    val maxDurability: Int,
  )

  // Lazy: the importer writes the table after this bean exists, so an eager read finds a fresh database empty.
  private val byItemId: Map<Long, Template> by lazy {
    itemRepository.findAll().associate { it.id to Template(it.id, it.level, it.weight, it.stackable, it.maxDurability) }
  }

  private val idByIdentifier: Map<String, Long> by lazy {
    itemRepository.findAll().associate { it.identifier to it.id }
  }

  /** Null for an id the catalogue does not know. */
  fun templateOf(itemId: Long): Template? {
    return byItemId[itemId]
  }

  /** Loads the catalogue now, at boot, so the first lookup on the tick does not reach the database. */
  fun warmUp() {
    byItemId.size
    idByIdentifier.size
  }

  /**
   * The item's tier, or null for an id the catalogue does not know.
   *
   * Null rather than a default, because every caller has to decide for itself what an unknown item means -
   * a craft treats it as a refusal, and silently calling it tier 1 would let a broken reference through as
   * the easiest possible item.
   */
  fun levelOf(itemId: Long): Int? = byItemId[itemId]?.level

  fun weightOf(itemId: Long): Int? = byItemId[itemId]?.weight

  /**
   * The catalogue id behind an `items.yml` identifier, or null when no such item was imported.
   *
   * For the callers that know an item by name rather than by id - a script naming its reagent - and have to
   * resolve it where a `findByIdentifier` round trip cannot go: inside a world lock scope.
   */
  fun idOf(identifier: String): Long? = idByIdentifier[identifier]
}
