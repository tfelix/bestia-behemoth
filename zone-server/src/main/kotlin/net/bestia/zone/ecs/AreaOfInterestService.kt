package net.bestia.zone.ecs

import net.bestia.zone.geometry.Cube
import net.bestia.zone.geometry.Vec3L

open class AreaOfInterestService<T> {

  /**
   * Internal Octree node class.
   */
  private inner class OctreeNode(
    val bounds: Cube
  ) {

    private val entities = mutableMapOf<T, Vec3L>()
    private var children: Array<OctreeNode?>? = null

    fun isLeaf() = children == null

    fun insert(entityId: T, pos: Vec3L): Boolean {
      if (!contains(pos)) return false
      if (isLeaf()) {
        entities[entityId] = pos
        if (entities.size > SUBDIVIDE_THRESHOLD) {
          subdivide()
        }
        return true
      } else {
        for (child in children!!) {
          if (child!!.insert(entityId, pos)) {
            return true
          }
        }
      }
      return false
    }

    fun remove(entityId: T): Boolean {
      if (isLeaf()) {
        val removed = entities.remove(entityId) != null
        if (removed && children != null && totalEntities() < MERGE_THRESHOLD) {
          merge()
        }
        return removed
      } else {
        for (child in children!!) {
          if (child!!.remove(entityId)) {
            if (totalEntities() < MERGE_THRESHOLD) {
              merge()
            }
            return true
          }
        }
      }
      return false
    }

    fun move(entityId: T, newPos: Vec3L): Boolean {
      if (remove(entityId)) {
        return insert(entityId, newPos)
      }
      return false
    }

    fun query(cube: Cube, result: MutableSet<T>) {
      if (!intersects(cube)) return
      if (isLeaf()) {
        for ((id, pos) in entities) {
          if (cube.collide(pos)) {
            result.add(id)
          }
        }
      } else {
        for (child in children!!) {
          child!!.query(cube, result)
        }
      }
    }

    private fun contains(pos: Vec3L): Boolean {
      // Check if pos is inside bounds
      return pos.x >= bounds.origin.x && pos.x < bounds.origin.x + bounds.size.width &&
              pos.y >= bounds.origin.y && pos.y < bounds.origin.y + bounds.size.height &&
              pos.z >= bounds.origin.z && pos.z < bounds.origin.z + bounds.size.depth
    }

    private fun intersects(cube: Cube): Boolean {
      // Simple AABB intersection
      val a = bounds
      val b = cube
      return a.origin.x < b.origin.x + b.size.width && a.origin.x + a.size.width > b.origin.x &&
              a.origin.y < b.origin.y + b.size.height && a.origin.y + a.size.height > b.origin.y &&
              a.origin.z < b.origin.z + b.size.depth && a.origin.z + a.size.depth > b.origin.z
    }

    private fun subdivide() {
      val halfW = bounds.size.width / 2
      val halfH = bounds.size.height / 2
      val halfD = bounds.size.depth / 2
      val ox = bounds.origin.x
      val oy = bounds.origin.y
      val oz = bounds.origin.z
      children = Array(8) { i ->
        val dx = if (i and 1 == 0) 0 else halfW
        val dy = if (i and 2 == 0) 0 else halfH
        val dz = if (i and 4 == 0) 0 else halfD
        OctreeNode(Cube(ox + dx, oy + dy, oz + dz, halfW, halfH, halfD))
      }
      // Re-insert entities into children
      val toReinsert = entities.toList()
      entities.clear()
      for ((id, pos) in toReinsert) {
        insert(id, pos)
      }
    }

    private fun merge() {
      if (children == null) return
      for (child in children!!) {
        if (child != null && child.isLeaf()) {
          entities.putAll(child.entities)
        }
      }
      children = null
    }

    private fun totalEntities(): Int {
      if (isLeaf()) return entities.size
      return children!!.sumOf { it?.totalEntities() ?: 0 }
    }
  }

  // We need to think about how we want to cut the coordinate system. Do we want to go into negative numbers?
  // Probably not as this would make wrapping around harder. But if not we need to come up with a translation of coords
  // from the global into the engine local space.
  private val root = OctreeNode(
    Cube(-ROOT_SIZE / 2, -ROOT_SIZE / 2, -ROOT_SIZE / 2, ROOT_SIZE, ROOT_SIZE, ROOT_SIZE)
  )

  private val entityNodeMap = mutableMapOf<T, OctreeNode>()

  fun setEntityPosition(entity: T, pos: Vec3L) {
    // Remove from previous node if exists
    entityNodeMap[entity]?.remove(entity)

    // Insert into octree
    if (root.insert(entity, pos)) {
      entityNodeMap[entity] = root // For simplicity, always point to root
    }
  }

  fun removeEntityPosition(entityId: T) {
    entityNodeMap[entityId]?.remove(entityId)
    entityNodeMap.remove(entityId)
  }

  fun queryEntitiesInCube(center: Vec3L, size: Long): Set<T> {
    val half = size / 2
    val cube = Cube(center.x - half, center.y - half, center.z - half, size, size, size)
    val result = mutableSetOf<T>()
    root.query(cube, result)

    return result
  }

  fun getTotalEntityCount(): Int {
    return entityNodeMap.size
  }

  companion object {
    private const val SUBDIVIDE_THRESHOLD = 40 // configurable subdivision threshold
    private const val MERGE_THRESHOLD = 15 // configurable merge threshold
    private const val ROOT_SIZE = 1024L // size of root cube (can be made configurable)
  }
}