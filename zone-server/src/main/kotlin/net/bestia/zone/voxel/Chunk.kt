package net.bestia.zone.voxel

import net.bestia.zone.geometry.Vec3I

internal const val DEFAULT_CHUNK_SIZE = 32

typealias Box = Array<Array<Array<Voxel>>>

/**
 * We could add more arrays to this chunk for mipmapping
 * the stored voxels.
 */
data class Chunk(
  private val data: Array<Array<Array<Voxel>>>
) {

  fun getVoxel(pos: Vec3I): Voxel {
    return data[pos.x][pos.y][pos.z]
  }

  fun getVoxel(x: Int, y: Int, z: Int): Voxel {
    return data[x][y][z]
  }

  fun setVoxel(pos: Vec3I, voxel: Voxel) {
    data[pos.x][pos.y][pos.z] = voxel
  }

  fun fill(voxel: Voxel) {
    for (x in data.indices) {
      for (y in data[x].indices) {
        for (z in data[x][y].indices) {
          data[x][y][z] = voxel
        }
      }
    }
  }

  val size get() = data.size

  override fun hashCode(): Int {
    return data.contentDeepHashCode()
  }

  override fun equals(other: Any?): Boolean {
    if (other == null) {
      return false
    }

    if (other !is Chunk) {
      return false
    }

    return data.contentDeepEquals(other.data)
  }

  override fun toString(): String {
    return "Chunk[dimension: $size]"
  }

  companion object {
    @Suppress("UNCHECKED_CAST")
    private fun initBox(boxSize: Int = DEFAULT_CHUNK_SIZE): Box {
      val xs = arrayOfNulls<Array<Array<Voxel>>>(boxSize)
      for (x in 0 until boxSize) {
        val ys = arrayOfNulls<Array<Voxel>>(boxSize)
        for (y in 0 until boxSize) {
          val zs = arrayOfNulls<Voxel>(boxSize)
          for (z in 0 until boxSize) {
            zs[z] = Voxel.EMPTY
          }
          ys[y] = zs as Array<Voxel>
        }
        xs[x] = ys as Array<Array<Voxel>>
      }

      return xs as Array<Array<Array<Voxel>>>
    }

    @Suppress("UNCHECKED_CAST")
    fun makeEmpty(chunkSize: Int = DEFAULT_CHUNK_SIZE): Chunk {
      val xs = arrayOfNulls<Array<Array<Voxel>>>(chunkSize)
      for (x in 0 until chunkSize) {
        val ys = arrayOfNulls<Array<Voxel>>(chunkSize)
        for (y in 0 until chunkSize) {
          val zs = arrayOfNulls<Voxel>(chunkSize)
          for (z in 0 until chunkSize) {
            zs[z] = Voxel.Companion.EMPTY
          }
          ys[y] = zs as Array<Voxel>
        }
        xs[x] = ys as Array<Array<Voxel>>
      }

      return Chunk(xs as Array<Array<Array<Voxel>>>)
    }
  }
}