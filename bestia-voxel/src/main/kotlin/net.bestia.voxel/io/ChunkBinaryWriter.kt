package net.bestia.voxel.io

import net.bestia.voxel.Chunk
import net.bestia.voxel.DEFAULT_CHUNK_SIZE
import net.bestia.voxel.Vector3
import net.bestia.voxel.Voxel
import java.nio.ByteBuffer
import kotlin.experimental.or

internal class ChunkBinaryWriter(
    private val chunkSize: Int = DEFAULT_CHUNK_SIZE
) : ChunkWriter {
  private val buffer: ByteBuffer

  init {
    val chunkByteSize = chunkSize * chunkSize * chunkSize * 2
    buffer = ByteBuffer.allocate(chunkByteSize)
  }

  @ExperimentalUnsignedTypes
  override fun write(chunk: Chunk): ByteArray {
    buffer.clear()

    var currentVoxelData: Voxel
    var lastVoxelData = chunk.getVoxel(0, 0, 0)

    val totalSize = chunkSize * chunkSize * chunkSize
    var i = 1
    var processed = 0
    var sameCount = 0

    do {
      val x = (i / (chunkSize * chunkSize)) % chunkSize
      val y = (i / chunkSize) % chunkSize
      val z = i % chunkSize

      currentVoxelData = chunk.getVoxel(x, y, z)

      if(currentVoxelData == lastVoxelData) {
        sameCount += 1
      }

      if (sameCount == 255 || currentVoxelData != lastVoxelData) {
        writeRLEData(currentVoxelData, sameCount.toUByte())
        processed += sameCount
        sameCount = 0
        lastVoxelData = currentVoxelData
      }
    } while (i++ < totalSize)

    // Write the last batch
    writeRLEData(currentVoxelData, sameCount.toUByte())

    buffer.flip()
    val result = ByteArray(buffer.remaining())
    buffer.get(result)

    return result
  }

  @ExperimentalUnsignedTypes
  private fun writeRLEData(voxel: Voxel, count: UByte) {
    val hasCount = count > 1.toUByte()

    val flagHasRle = when (hasCount) {
      true -> 0b10000000.toByte()
      false -> 0b00000000.toByte()
    }
    val materialUpper = (voxel.material shr 2).toByte()
    val materialLower = ((voxel.material and 0b11) shl 6).toByte()
    val occupancy = voxel.occupancy

    val b1 = flagHasRle or materialUpper
    val b2 = materialLower or occupancy

    buffer.put(b1)
    buffer.put(b2)
    if (hasCount) {
      buffer.put(count.toByte())
    }
  }

  companion object {
    private const val OCCUPANCY_MASK = 0b111111.toByte()
  }
}