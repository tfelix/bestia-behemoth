package net.bestia.voxel.io

import net.bestia.voxel.Chunk
import net.bestia.voxel.DEFAULT_CHUNK_SIZE
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
    var sameCount = 1

    do {
      val x = (i / (chunkSize * chunkSize)) % chunkSize
      val y = (i / chunkSize) % chunkSize
      val z = i % chunkSize

      currentVoxelData = chunk.getVoxel(x, y, z)

      if (currentVoxelData == lastVoxelData) {
        sameCount += 1
      }

      if (sameCount == 255 || currentVoxelData != lastVoxelData) {
        writeRLEData(lastVoxelData, sameCount.toUByte())
        processed += sameCount
        sameCount = 1
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

    val storeCount = when (hasCount) {
      true -> 0b10000000.toByte()
      false -> 0b00000000.toByte()
    }

    val hasOccupancy = voxel.occupancy != Voxel.NOT_OCCUIPIED && voxel.occupancy != Voxel.FULL_OCCUPIED
    val storeOccupancy = when (hasOccupancy) {
      true -> 0b01000000.toByte()
      false -> 0.toByte()
    }

    val material = (voxel.material and 0b00111111).toByte()
    val occupancy = voxel.occupancy

    val b1 = storeCount or storeOccupancy or material

    buffer.put(b1)

    if(hasOccupancy) {
      buffer.put(occupancy.toByte())
    }
    if (hasCount) {
      buffer.put(count.toByte())
    }
  }

  companion object {
    private const val OCCUPANCY_MASK = 0b111111.toByte()
  }
}