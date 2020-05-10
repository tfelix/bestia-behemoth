package net.bestia.voxel.io

import net.bestia.voxel.Chunk
import net.bestia.voxel.DEFAULT_CHUNK_SIZE
import java.util.zip.Deflater

@ExperimentalUnsignedTypes
internal class ChunkCompressWriter(
  private val chunkSize: Int = DEFAULT_CHUNK_SIZE
) : ChunkWriter {
  private val serializer = ChunkBinaryWriter(chunkSize)
  private val compressor = Deflater()

  override fun write(chunk: Chunk): ByteArray {
    val input = serializer.write(chunk)
    val output = ByteArray(chunkSize * chunkSize * chunkSize)
    compressor.setInput(input)
    compressor.finish()
    val compressedDataLength = compressor.deflate(output)

    return output.copyOfRange(0, compressedDataLength)
  }
}