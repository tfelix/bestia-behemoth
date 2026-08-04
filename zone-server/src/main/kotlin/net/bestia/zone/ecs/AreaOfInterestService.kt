package net.bestia.zone.ecs

import net.bestia.zone.geometry.Cube
import net.bestia.zone.geometry.Vec3L
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * Spatial index over entity positions: an octree answering "what is inside this volume".
 *
 * ### Threading
 *
 * Writes come from the tick thread only ([ZoneEngine][net.bestia.zone.ecs.ZoneEngine]'s dirty-position
 * pass, and world-object residency). Reads come from anywhere: the tick thread via
 * `PerceptionSystem`, `AsyncJobExecutor` workers via `OutMessageProcessor`, and a Netty worker via
 * `GetAllEntitiesHandler`. Those reads used to run against plain `LinkedHashMap`s being mutated
 * concurrently, which is a data race that can hang a reader inside `HashMap.get` on a resize - so
 * everything now goes through a [ReentrantReadWriteLock]. Reads are frequent and concurrent, writes
 * are brief and single-threaded, which is exactly what that lock is shaped for.
 *
 * No public method takes more than one lock, and none calls another, so there is no upgrade path to
 * deadlock on.
 *
 * ### Why entries carry a layer
 *
 * See [AoiLayer]. The index holds a handful of moving things and potentially tens of thousands of
 * still ones, and most questions are about one or the other.
 *
 * ### What was wrong with the previous implementation
 *
 * Worth recording, because two of the three were silent and would have stayed silent:
 *
 * 1. **`merge` dropped grandchildren.** It copied entries up from children that were leaves and then
 *    discarded the child array wholesale, so anything one level further down vanished from the index
 *    without vanishing from the world. Reachable as soon as any node subdivided twice, which needs
 *    only forty-one entities in one cube.
 * 2. **Removal was O(subtree).** `entityNodeMap` claimed to record an entity's node but stored the
 *    root ("For simplicity, always point to root"), so every removal searched the whole tree; and the
 *    merge check recomputed a full recursive `totalEntities()` at every level as the recursion
 *    unwound. Subtree sizes are now maintained incrementally and an entity's leaf is recorded, so a
 *    removal is a hash lookup plus a walk up the parent chain.
 * 3. **Entities at one position were dropped.** Subdivision recursed while a node was over the
 *    threshold, and entries sharing a position never separate, so it split until the cube reached zero
 *    size and then failed every insert. Splitting now stops when a cube can no longer be halved, and a
 *    leaf simply holds however many coincident entries it has.
 *
 * A fourth was harmless but confusing: the leaf branch of `remove` guarded on `children != null`
 * inside a branch conditioned on `children == null`, so that merge could never fire.
 */
open class AreaOfInterestService<T> {

  private class Entry(val pos: Vec3L, val layer: AoiLayer)

  private inner class Node(val bounds: Cube, val parent: Node?) {

    /**
     * Entries held directly by this node.
     *
     * Normally empty once the node has children. Not *guaranteed* empty: if a position somehow falls
     * in no child - which exact power-of-two partitioning should make impossible, but which is one
     * arithmetic slip away - the entry stays here rather than being discarded, and [collect] and the
     * query both look here on every node so it stays reachable.
     */
    val entries = HashMap<T, Entry>()

    /** Eight children once subdivided, none of them null. */
    var children: Array<Node>? = null

    /** Entries in this whole subtree. Maintained incrementally; never recomputed. */
    var count = 0

    /**
     * Upper bounds exclusive, unlike [Cube.collide].
     *
     * That difference is deliberate and load bearing: this decides *ownership*, so a position on a
     * boundary must belong to exactly one of the two cubes that share it. `collide` decides
     * *overlap*, where being generous by one unit costs nothing.
     */
    fun contains(pos: Vec3L) =
      pos.x >= bounds.x && pos.x < bounds.x + bounds.width &&
          pos.y >= bounds.y && pos.y < bounds.y + bounds.height &&
          pos.z >= bounds.z && pos.z < bounds.z + bounds.depth

    fun childContaining(pos: Vec3L): Node? = children?.firstOrNull { it.contains(pos) }
  }

  private val lock = ReentrantReadWriteLock()

  private var root = Node(
    Cube(-ROOT_SIZE / 2, -ROOT_SIZE / 2, -ROOT_SIZE / 2, ROOT_SIZE, ROOT_SIZE, ROOT_SIZE),
    parent = null
  )

  /** The leaf actually holding each entity, so removal does not have to search for it. */
  private val entityLeaf = HashMap<T, Node>()

  fun setEntityPosition(entity: T, pos: Vec3L, layer: AoiLayer = AoiLayer.DYNAMIC) {
    lock.write {
      detach(entity)
      growRootToContain(pos)
      attach(entity, Entry(pos, layer))
    }
  }

  fun removeEntityPosition(entityId: T) {
    lock.write { detach(entityId) }
  }

  /**
   * Entities inside an axis-aligned cube of [size] centred on [center].
   *
   * [size] is the cube's **edge**, not its radius - this halves it. Callers wanting the range to line
   * up with the terrain a player has should not compute it themselves; see
   * [InterestRange][net.bestia.zone.world.stream.InterestRange].
   */
  fun queryEntitiesInCube(center: Vec3L, size: Long, layers: Set<AoiLayer> = AoiLayer.ALL): Set<T> {
    val half = size / 2
    val cube = Cube(center.x - half, center.y - half, center.z - half, size, size, size)
    val result = mutableSetOf<T>()

    lock.read { query(root, cube, layers, result) }

    return result
  }

  fun getTotalEntityCount(): Int = lock.read { entityLeaf.size }

  private fun query(node: Node, cube: Cube, layers: Set<AoiLayer>, result: MutableSet<T>) {
    if (!node.bounds.intersects(cube)) return

    for ((id, entry) in node.entries) {
      if (entry.layer in layers && cube.collide(entry.pos)) result.add(id)
    }

    node.children?.forEach { query(it, cube, layers, result) }
  }

  /** Places an entry in the leaf that owns its position, splitting that leaf if it grows too full. */
  private fun attach(entity: T, entry: Entry) {
    if (!root.contains(entry.pos)) return

    var node = root
    while (true) {
      node.count++
      val child = node.childContaining(entry.pos) ?: break
      node = child
    }

    node.entries[entity] = entry
    entityLeaf[entity] = node

    if (node.entries.size > SUBDIVIDE_THRESHOLD) subdivide(node)
  }

  /**
   * Removes an entity, decrements the subtree counts above it, and collapses the largest ancestor
   * that has become sparse enough to hold its whole subtree itself.
   */
  private fun detach(entity: T) {
    val leaf = entityLeaf.remove(entity) ?: return
    if (leaf.entries.remove(entity) == null) return

    var node: Node? = leaf
    while (node != null) {
      node.count--
      node = node.parent
    }

    // The highest qualifying ancestor, because merging it subsumes every merge below it.
    var candidate: Node? = leaf.parent
    var mergeAt: Node? = null
    while (candidate != null) {
      if (candidate.count < MERGE_THRESHOLD) mergeAt = candidate
      candidate = candidate.parent
    }

    mergeAt?.let { merge(it) }
  }

  /**
   * Splits a leaf into eight and re-homes its entries.
   *
   * Stops when a cube can no longer be halved, which is what keeps coincident entries from splitting
   * forever. Recurses into children that are themselves over the threshold, so a lopsided
   * distribution settles in one pass rather than waiting for the next insert.
   */
  private fun subdivide(node: Node) {
    val halfW = node.bounds.width / 2
    val halfH = node.bounds.height / 2
    val halfD = node.bounds.depth / 2

    if (halfW < 1 || halfH < 1 || halfD < 1) return

    val ox = node.bounds.x
    val oy = node.bounds.y
    val oz = node.bounds.z

    node.children = Array(8) { i ->
      val dx = if (i and 1 == 0) 0 else halfW
      val dy = if (i and 2 == 0) 0 else halfH
      val dz = if (i and 4 == 0) 0 else halfD
      Node(Cube(ox + dx, oy + dy, oz + dz, halfW, halfH, halfD), parent = node)
    }

    val moved = node.entries.toList()
    node.entries.clear()

    for ((id, entry) in moved) {
      val child = node.childContaining(entry.pos)

      if (child == null) {
        // Cannot happen while the root is a power of two, and not worth losing an entity over.
        node.entries[id] = entry
        continue
      }

      child.entries[id] = entry
      child.count++
      entityLeaf[id] = child
    }

    node.children?.forEach { if (it.entries.size > SUBDIVIDE_THRESHOLD) subdivide(it) }
  }

  /** Pulls a whole subtree's entries - at any depth - onto [node] and drops its children. */
  private fun merge(node: Node) {
    if (node.children == null) return

    val collected = HashMap<T, Entry>()
    collect(node, collected)

    node.children = null
    node.entries.clear()
    node.entries.putAll(collected)

    for (id in collected.keys) entityLeaf[id] = node
  }

  private fun collect(node: Node, into: MutableMap<T, Entry>) {
    into.putAll(node.entries)
    node.children?.forEach { collect(it, into) }
  }

  /**
   * Doubles the root until it contains [pos], re-inserting everything already indexed.
   *
   * The root has to stay a power of two and centred on the origin, because that is what makes every
   * subdivision partition its parent exactly - an odd extent leaves a one-unit sliver in no child at
   * all.
   */
  private fun growRootToContain(pos: Vec3L) {
    if (root.contains(pos)) return

    val all = HashMap<T, Entry>()
    collect(root, all)

    var size = root.bounds.width
    var bounds = root.bounds

    while (!containsIn(bounds, pos)) {
      require(size <= MAX_ROOT_SIZE / 2) { "Position $pos is too far from the origin to index" }
      size *= 2
      bounds = Cube(-size / 2, -size / 2, -size / 2, size, size, size)
    }

    root = Node(bounds, parent = null)
    entityLeaf.clear()

    for ((id, entry) in all) attach(id, entry)
  }

  private fun containsIn(bounds: Cube, pos: Vec3L) =
    pos.x >= bounds.x && pos.x < bounds.x + bounds.width &&
        pos.y >= bounds.y && pos.y < bounds.y + bounds.height &&
        pos.z >= bounds.z && pos.z < bounds.z + bounds.depth

  companion object {
    private const val SUBDIVIDE_THRESHOLD = 40
    private const val MERGE_THRESHOLD = 15
    private const val ROOT_SIZE = 1024L

    /**
     * Ceiling on root growth, so a nonsense position fails loudly instead of doubling until the
     * extent overflows to negative and the loop never ends. Far beyond any world: a 128 km world
     * needs 2^18.
     */
    private const val MAX_ROOT_SIZE = 1L shl 40
  }
}
