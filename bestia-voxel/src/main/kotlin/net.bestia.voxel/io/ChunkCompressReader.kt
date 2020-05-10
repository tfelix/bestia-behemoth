package net.bestia.voxel.io

import net.bestia.voxel.Chunk
import net.bestia.voxel.DEFAULT_CHUNK_SIZE
import java.util.zip.Inflater

@ExperimentalUnsignedTypes
internal class ChunkCompressReader(
  private val chunkSize: Int = DEFAULT_CHUNK_SIZE
) : ChunkReader {
  private val decompressor = Inflater()
  private val reader = ChunkBinaryReader()

  override fun read(data: ByteArray): Chunk {
    decompressor.setInput(data)
    val result = ByteArray(chunkSize * chunkSize * chunkSize)
    val resultLength = decompressor.inflate(result)
    decompressor.end()

    val inflated = result.copyOfRange(0, resultLength)

    return reader.read(inflated)
  }
}