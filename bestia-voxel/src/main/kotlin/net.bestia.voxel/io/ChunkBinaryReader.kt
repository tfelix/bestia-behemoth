package net.bestia.voxel.io

import net.bestia.voxel.Chunk
import net.bestia.voxel.DEFAULT_CHUNK_SIZE
import net.bestia.voxel.Vector3
import net.bestia.voxel.Voxel
import java.nio.BufferUnderflowException
import java.nio.ByteBuffer
import kotlin.experimental.and

@ExperimentalUnsignedTypes
class ChunkBinaryReader(
    private val chunkSize: Int = DEFAULT_CHUNK_SIZE
) : ChunkReader {

  override fun read(data: ByteArray): Chunk {
    val buffer = ByteBuffer.wrap(data)
    val totalCount = chunkSize * chunkSize * chunkSize
    val chunk = Chunk.makeEmpy(chunkSize)

    var pos = 0

    while (pos < totalCount) {
      val b1 = buffer.get().toUByte()

      val hasRle = (b1.toInt() and 0b10000000) shr 7 == 1
      val hasOccupance = (b1.toInt() and 0b10000000) shr 6 == 1

      // because we need to convert to int we now need to shift as well for the differences in bit
      // for ints and bytes e.g. 8 instead of 4.
      val material = (b1.toInt() and 0b00111111)

      val occupancy = if (hasOccupance) {
        buffer.get().toUByte()
      } else {
        if (material == 0) {
          Voxel.NOT_OCCUIPIED
        } else {
          Voxel.FULL_OCCUPIED
        }
      }

      if (hasRle) {
        val count = buffer.get().toUByte().toInt()
        for (i in 0 until count) {
          val posVec = posToVec3(pos)
          pos++
          chunk.setVoxel(posVec, Voxel.of(material, occupancy))
        }
      } else {
        val posVec = posToVec3(pos)
        chunk.setVoxel(posVec, Voxel.of(material, occupancy))
        pos++
      }
    }

    return chunk
  }

  private fun posToVec3(pos: Int): Vector3 {
    val z = pos % chunkSize
    val y = (pos / chunkSize) % chunkSize
    val x = (pos / (chunkSize * chunkSize)) % chunkSize

    return Vector3(x, y, z)
  }

  companion object {
    private val OCCUPANCY_MASK = 0b00111111.toUByte()
  }
}