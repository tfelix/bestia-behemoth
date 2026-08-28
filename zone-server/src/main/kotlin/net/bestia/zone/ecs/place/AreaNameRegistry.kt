package net.bestia.zone.ecs.place

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.worldgen.civ.SettlementChannels
import net.bestia.worldgen.civ.SettlementTier
import net.bestia.worldgen.core.World
import net.bestia.worldgen.history.Names
import net.bestia.worldgen.vector.FeatureKind
import net.bestia.worldgen.vector.PointMarker
import org.springframework.stereotype.Service

/**
 * Every named area with a radius, indexed so a position lookup is a single bucket read.
 *
 * ### Generated towns and player claims are one thing here
 *
 * That is the load-bearing decision. A settlement the generator placed and an area a player founded differ
 * in how they come to exist and in nothing else that matters to "what is this place called", so both live
 * in this one index and [PlaceNameService] has one rule. The claims feature therefore adds a table, some
 * messages and validation - not a second resolver.
 *
 * ### Why an index and not the entity octree
 *
 * A player-founded area *is* an entity ([AreaName] rides on it), and `AreaOfInterestService` could answer
 * "areas near me" - but only with a query cube as wide as the largest area in the world, which drags in
 * every dynamic entity within kilometres to find one town. This index answers the same question by reading
 * one bucket.
 *
 * ### One bucket, not a neighbourhood
 *
 * An area is indexed into **every** lattice cell its radius covers, so a lookup reads only the cell the
 * position falls in: if a point is inside an area, that point's cell was necessarily written when the area
 * was indexed. [LATTICE_METRES] is therefore a memory-versus-insert trade and not a correctness bound - a
 * city's 1310 m footprint lands in about sixteen cells, which is nothing.
 *
 * `cartography/coverage/SurveyGrid` is the shape, and its warning applies unchanged: the stored keys are
 * positional, so changing the cell size reinterprets where everything is. It is a `const`, not a setting.
 *
 * ### Threading
 *
 * Unsynchronised, like `world/prop/PlayerStructureRegistry`, and what makes that safe is the **world
 * lock** rather than a single thread. Both readers hold it: `PlaceSystem` runs inside `world.tick`, and
 * `MasterEntitySpawner` resolves inside its `createEntity` block, which is a login thread. Filled by
 * `boot/PlaceIndexBootRunner` before the tick loop starts.
 *
 * So a founding path must write from inside the world lock too. Writing from a request thread while a tick
 * reads is the mistake `AreaOfInterestService`'s KDoc records having shipped once: a reader can hang inside
 * `HashMap.get` during a resize, and it will not look like a locking bug when it happens.
 *
 * ### The seam
 *
 * The lattice does not wrap, so an area whose radius crosses the world seam is only found from one side.
 * That is unreachable rather than tolerated: the wrap is hidden inside `oceanBorderMetres` of forced deep
 * water, nothing can be founded there, and a generated settlement cannot be placed there either.
 */
@Service
class AreaNameRegistry {

  /**
   * One named area.
   *
   * [id] is namespaced so the two sources cannot collide: a generated settlement is `-(index + 1)` and a
   * player-founded area is its own positive row id. Without that a settlement at index 7 and the seventh
   * claim ever made would evict each other. It is also the tie-break when two areas of equal radius
   * contain the same point, so the answer does not depend on insertion order.
   */
  class Entry(
    val id: Long,
    val name: String,
    val x: Long,
    val y: Long,
    val radius: Long
  )

  private val byCell = HashMap<Long, MutableList<Entry>>()
  private val byId = HashMap<Long, Entry>()

  val size: Int
    get() {
      return byId.size
    }

  fun add(entry: Entry) {
    remove(entry.id)

    byId[entry.id] = entry
    forEachCoveredCell(entry) { cell ->
      byCell.getOrPut(cell) { mutableListOf() }.add(entry)
    }
  }

  fun remove(id: Long) {
    val existing = byId.remove(id) ?: return

    forEachCoveredCell(existing) { cell ->
      val bucket = byCell[cell] ?: return@forEachCoveredCell
      bucket.removeIf { it.id == id }
      if (bucket.isEmpty()) byCell.remove(cell)
    }
  }

  /**
   * The narrowest area containing a position, or null where none does.
   *
   * Smallest radius wins, because the smaller of two areas holding the same point is the more specific
   * answer - a claim inside a town, a town inside nothing. [Entry.id] breaks an exact tie so two runs of
   * one world cannot disagree.
   */
  fun at(x: Long, y: Long): Entry? {
    val bucket = byCell[cellOf(x, y)] ?: return null

    var best: Entry? = null
    for (entry in bucket) {
      val dx = entry.x - x
      val dy = entry.y - y
      if (dx * dx + dy * dy > entry.radius * entry.radius) continue

      val current = best
      if (current == null || entry.radius < current.radius ||
        (entry.radius == current.radius && entry.id < current.id)
      ) {
        best = entry
      }
    }
    return best
  }

  fun clear() {
    byCell.clear()
    byId.clear()
  }

  /**
   * Loads every generated settlement that history left standing.
   *
   * The name needs both markers, which is the join `cartography/render/PlaceInk` and
   * `civ/SettlementSpawnPoints` already perform for the same reason: placement knows the culture and
   * history knows the name. A settlement history never founded has no name and is not a place.
   */
  fun loadSettlements(world: World) {
    val chronicle = world.chronicle
    var loaded = 0

    for (feature in world.features.all()) {
      if (feature !is PointMarker || feature.kind != FeatureKind.SETTLEMENT) continue

      val index = feature.attribute(SettlementChannels.INDEX).toInt()
      val record = chronicle.settlements.getOrNull(index) ?: continue
      if (!record.wasFounded || record.isRuin || record.nameSeed == 0L) continue

      val tier = SettlementTier.entries[feature.attribute(SettlementChannels.TIER).toInt()]
      val culture = feature.attribute(SettlementChannels.CULTURE).toInt()

      add(
        Entry(
          id = -(index.toLong() + 1),
          name = Names.place(record.nameSeed, culture),
          x = feature.position.x.toLong(),
          y = feature.position.y.toLong(),
          radius = tier.footprintRadius.toLong()
        )
      )
      loaded++
    }

    LOG.info { "Loaded $loaded standing settlement(s) into the place index" }
  }

  private inline fun forEachCoveredCell(entry: Entry, action: (Long) -> Unit) {
    val firstX = Math.floorDiv(entry.x - entry.radius, LATTICE_METRES)
    val lastX = Math.floorDiv(entry.x + entry.radius, LATTICE_METRES)
    val firstY = Math.floorDiv(entry.y - entry.radius, LATTICE_METRES)
    val lastY = Math.floorDiv(entry.y + entry.radius, LATTICE_METRES)

    for (cy in firstY..lastY) {
      for (cx in firstX..lastX) {
        action(pack(cx, cy))
      }
    }
  }

  private fun cellOf(x: Long, y: Long): Long {
    return pack(Math.floorDiv(x, LATTICE_METRES), Math.floorDiv(y, LATTICE_METRES))
  }

  private fun pack(x: Long, y: Long): Long {
    return (x shl 32) or (y and 0xFFFFFFFFL)
  }

  companion object {
    /**
     * Edge of one index cell, in position units - which are metres.
     *
     * Positional and therefore a `const`: see the class note.
     */
    const val LATTICE_METRES = 1_000L

    private val LOG = KotlinLogging.logger { }
  }
}
