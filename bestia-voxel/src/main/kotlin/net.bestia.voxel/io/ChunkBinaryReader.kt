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
        val b2 = buffer.get().toUByte()

        val hasRle = (b1.toInt() shr 7) == 1
        // because we need to convert to int we now need to shift as well for the differences in bit
        // for ints and bytes e.g. 8 instead of 4.
        val materialUpper = (b1.toInt() and 0b01111111) shl 2
        val materialLower = (b2.toInt() and 0b11000000) shr 6
        val material = materialUpper or materialLower
        val occupancy = (b2 and OCCUPANCY_MASK).toByte()

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