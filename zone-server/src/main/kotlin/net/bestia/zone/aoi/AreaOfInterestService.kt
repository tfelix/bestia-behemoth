package net.bestia.zone.aoi

import net.bestia.zone.ecs.core.Long2IntOpenHashMap
import net.bestia.zone.geometry.Vec3L

/**
 * Spatial index over entity positions, answering "what is inside this box".
 *
 * A uniform grid of [CELL_SIZE]-column cells rather than a tree: a step inside one cell is three array
 * writes, a step into the next cell is two list splices, and a query visits the cells it overlaps without
 * allocating. One grid per [AoiLayer], so a question about moving things never walks the static ones.
 *
 * Touched only with the world to itself - the tick thread, or a world scope - so it takes no lock.
 */
open class AreaOfInterestService {

  private val dynamic = Grid()
  private val static = Grid()

  /** How often an entity crossed into another cell; for tests that check a step stays cheap. */
  val cellChanges: Long
    get() {
      return dynamic.cellChanges + static.cellChanges
    }

  fun setEntityPosition(entity: Long, pos: Vec3L, layer: AoiLayer = AoiLayer.DYNAMIC) {
    setEntityPosition(entity, pos.x, pos.y, pos.z, layer)
  }

  fun setEntityPosition(entity: Long, x: Long, y: Long, z: Long, layer: AoiLayer = AoiLayer.DYNAMIC) {
    gridOf(layer).set(entity, x, y, z)
    // An entity lives in one layer: a promoted prop that turns dynamic must leave the static grid.
    gridOf(otherThan(layer)).remove(entity)
  }

  fun removeEntityPosition(entity: Long) {
    dynamic.remove(entity)
    static.remove(entity)
  }

  /**
   * Entities inside an axis-aligned cube of [size] centred on [center], bounds inclusive.
   *
   * [size] is the cube's **edge**, not its radius - this halves it. Callers wanting the range to line
   * up with the terrain a player has should not compute it themselves; see
   * [InterestRange][net.bestia.zone.world.stream.InterestRange].
   */
  fun queryEntitiesInCube(center: Vec3L, size: Long, layers: Set<AoiLayer> = AoiLayer.ALL): Set<Long> {
    val result = LinkedHashSet<Long>()
    forEachInCube(center, size, layers) { id, _, _, _ -> result.add(id) }

    return result
  }

  /** Like [queryEntitiesInCube], without building a set; [action] gets each entity and its position. */
  fun forEachInCube(center: Vec3L, size: Long, layers: Set<AoiLayer>, action: (Long, Long, Long, Long) -> Unit) {
    val minX = center.x - size / 2
    val minY = center.y - size / 2
    val minZ = center.z - size / 2
    forEachInBox(minX, minY, minZ, minX + size, minY + size, minZ + size, layers, action)
  }

  /** Every entity with a position inside the box, bounds inclusive. */
  fun forEachInBox(
    minX: Long, minY: Long, minZ: Long,
    maxX: Long, maxY: Long, maxZ: Long,
    layers: Set<AoiLayer>,
    action: (Long, Long, Long, Long) -> Unit,
  ) {
    if (AoiLayer.DYNAMIC in layers) dynamic.forEachInBox(minX, minY, minZ, maxX, maxY, maxZ, action)
    if (AoiLayer.STATIC in layers) static.forEachInBox(minX, minY, minZ, maxX, maxY, maxZ, action)
  }

  /** Whether any dynamic entity stands within [radius] of ([x], [y]) on the ground plane, height ignored. */
  fun anyWithinHorizontal(x: Long, y: Long, radius: Long): Boolean {
    return dynamic.anyWithinHorizontal(x, y, radius)
  }

  fun getTotalEntityCount(): Int {
    return dynamic.size + static.size
  }

  private fun gridOf(layer: AoiLayer): Grid {
    return if (layer == AoiLayer.STATIC) static else dynamic
  }

  private fun otherThan(layer: AoiLayer): AoiLayer {
    return if (layer == AoiLayer.STATIC) AoiLayer.DYNAMIC else AoiLayer.STATIC
  }

  /**
   * Entities in slots of parallel arrays, each slot linked into the list of the cell it stands in. Slots
   * are reused, so the arrays only grow to the largest population there ever was.
   */
  private class Grid {
    private val slotOf = Long2IntOpenHashMap()
    private val headOf = Long2IntOpenHashMap()

    private var ids = LongArray(INITIAL_SLOTS)
    private var xs = LongArray(INITIAL_SLOTS)
    private var ys = LongArray(INITIAL_SLOTS)
    private var zs = LongArray(INITIAL_SLOTS)
    private var cells = LongArray(INITIAL_SLOTS)
    private var next = IntArray(INITIAL_SLOTS)
    private var prev = IntArray(INITIAL_SLOTS)

    private var freeSlots = IntArray(INITIAL_SLOTS)
    private var freeCount = 0
    private var highWater = 0

    var cellChanges = 0L
      private set

    val size: Int
      get() {
        return slotOf.size
      }

    fun set(id: Long, x: Long, y: Long, z: Long) {
      val cell = cellKey(x, y)
      var slot = slotOf.get(id)

      if (slot == Long2IntOpenHashMap.ABSENT) {
        slot = allocate()
        slotOf.put(id, slot)
        ids[slot] = id
        link(slot, cell)
      } else if (cells[slot] != cell) {
        unlink(slot)
        link(slot, cell)
        cellChanges++
      }

      xs[slot] = x
      ys[slot] = y
      zs[slot] = z
    }

    fun remove(id: Long) {
      val slot = slotOf.remove(id)
      if (slot == Long2IntOpenHashMap.ABSENT) return

      unlink(slot)
      if (freeCount == freeSlots.size) freeSlots = freeSlots.copyOf(freeCount * 2)
      freeSlots[freeCount++] = slot
    }

    fun forEachInBox(
      minX: Long, minY: Long, minZ: Long,
      maxX: Long, maxY: Long, maxZ: Long,
      action: (Long, Long, Long, Long) -> Unit,
    ) {
      for (cx in cellOf(minX)..cellOf(maxX)) {
        for (cy in cellOf(minY)..cellOf(maxY)) {
          var slot = headOf.get(cellKeyOfCell(cx, cy))
          while (slot != NONE && slot != Long2IntOpenHashMap.ABSENT) {
            val x = xs[slot]
            val y = ys[slot]
            val z = zs[slot]
            if (x in minX..maxX && y in minY..maxY && z in minZ..maxZ) action(ids[slot], x, y, z)
            slot = next[slot]
          }
        }
      }
    }

    fun anyWithinHorizontal(x: Long, y: Long, radius: Long): Boolean {
      val radiusSquared = radius * radius
      for (cx in cellOf(x - radius)..cellOf(x + radius)) {
        for (cy in cellOf(y - radius)..cellOf(y + radius)) {
          var slot = headOf.get(cellKeyOfCell(cx, cy))
          while (slot != NONE && slot != Long2IntOpenHashMap.ABSENT) {
            val dx = xs[slot] - x
            val dy = ys[slot] - y
            if (dx * dx + dy * dy <= radiusSquared) return true
            slot = next[slot]
          }
        }
      }

      return false
    }

    private fun allocate(): Int {
      if (freeCount > 0) return freeSlots[--freeCount]

      if (highWater == ids.size) grow()
      return highWater++
    }

    private fun link(slot: Int, cell: Long) {
      val head = headOf.get(cell)
      val oldHead = if (head == Long2IntOpenHashMap.ABSENT) NONE else head

      cells[slot] = cell
      prev[slot] = NONE
      next[slot] = oldHead
      if (oldHead != NONE) prev[oldHead] = slot
      headOf.put(cell, slot)
    }

    private fun unlink(slot: Int) {
      val before = prev[slot]
      val after = next[slot]

      if (after != NONE) prev[after] = before
      if (before != NONE) {
        next[before] = after
      } else if (after != NONE) {
        headOf.put(cells[slot], after)
      } else {
        headOf.remove(cells[slot])
      }
    }

    private fun grow() {
      val capacity = ids.size * 2
      ids = ids.copyOf(capacity)
      xs = xs.copyOf(capacity)
      ys = ys.copyOf(capacity)
      zs = zs.copyOf(capacity)
      cells = cells.copyOf(capacity)
      next = next.copyOf(capacity)
      prev = prev.copyOf(capacity)
    }
  }

  companion object {
    /** Columns per cell edge: a chunk column, so a cell lines up with the ground a client holds. */
    const val CELL_SIZE = 32L

    private const val CELL_SHIFT = 5
    private const val NONE = -1
    private const val INITIAL_SLOTS = 64

    /** Floors, so -1 lands in the cell left of the origin rather than sharing cell 0. */
    private fun cellOf(coordinate: Long): Long {
      return coordinate shr CELL_SHIFT
    }

    private fun cellKey(x: Long, y: Long): Long {
      return cellKeyOfCell(cellOf(x), cellOf(y))
    }

    private fun cellKeyOfCell(cellX: Long, cellY: Long): Long {
      return (cellX shl 32) or (cellY and 0xFFFFFFFFL)
    }
  }
}
