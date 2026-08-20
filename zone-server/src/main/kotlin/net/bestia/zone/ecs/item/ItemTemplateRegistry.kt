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
  itemRepository: ItemRepository
) {

  private data class Template(val level: Int, val weight: Int)

  private val byItemId: Map<Long, Template>

  private val idByIdentifier: Map<String, Long>

  init {
    val all = itemRepository.findAll()
    byItemId = all.associate { it.id to Template(it.level, it.weight) }
    idByIdentifier = all.associate { it.identifier to it.id }
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
