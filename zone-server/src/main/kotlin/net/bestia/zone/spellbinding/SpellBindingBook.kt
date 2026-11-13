package net.bestia.zone.spellbinding

import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Every master's bindings, in memory, so the limit and the release menu need no database. The `spell_binding` rows
 * stay the truth: [SpellBindingBootRunner][net.bestia.zone.boot.SpellBindingBootRunner] loads this from them.
 */
@Component
class SpellBindingBook {

  /** Where one binding waits: a glyph on the ground, or an item instance. */
  data class Entry(
    val bound: BoundSpell,
    val glyphId: EntityId? = null,
    val position: Vec3L? = null,
    val itemUniqueId: Long? = null,
  )

  private val byMaster = ConcurrentHashMap<Long, List<Entry>>()
  private val nextId = AtomicLong(1)

  fun startIdsAfter(maxId: Long) {
    nextId.set(maxId + 1)
  }

  fun newId(): Long {
    return nextId.getAndIncrement()
  }

  /** Oldest first. */
  fun of(masterId: Long): List<Entry> {
    return byMaster[masterId].orEmpty()
  }

  fun find(masterId: Long, bindingId: Long): Entry? {
    return of(masterId).find { it.bound.id == bindingId }
  }

  fun add(entry: Entry) {
    byMaster.merge(entry.bound.owner.masterId, listOf(entry)) { old, new -> (old + new).sortedBy { it.bound.boundAt } }
  }

  fun remove(masterId: Long, bindingId: Long): Entry? {
    var removed: Entry? = null
    byMaster.computeIfPresent(masterId) { _, entries ->
      removed = entries.find { it.bound.id == bindingId }
      (entries - setOfNotNull(removed)).ifEmpty { null }
    }

    return removed
  }
}
