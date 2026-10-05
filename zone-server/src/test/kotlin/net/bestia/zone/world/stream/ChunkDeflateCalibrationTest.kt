package net.bestia.zone.world.stream

import net.bestia.worldgen.core.ChunkPos
import net.bestia.worldgen.core.WorldConfig
import net.bestia.worldgen.pipeline.StandardWorld
import net.bestia.worldgen.voxel.RleCodec
import org.junit.jupiter.api.Disabled
import java.io.ByteArrayOutputStream
import java.util.zip.Deflater
import kotlin.test.Test

/**
 * What each deflate level costs and saves on real chunks: the numbers behind `chunk-stream.deflate-level`.
 * A report, not a check - enable it when changing that level.
 */
@Disabled("Tuning report, not a check. See the class note.")
class ChunkDeflateCalibrationTest {

  @Test
  fun `bytes and time per deflate level`() {
    val world = StandardWorld.build(WorldConfig(seed = 9003L, widthCells = 48, heightCells = 48))
    val config = world.config

    // Surface chunks are where the bytes are; deep ones are mostly uniform and tiny either way.
    val payloads = (0 until 11).flatMap { y ->
      (0 until 11).flatMap { x ->
        val heights = world.columns.heights(ChunkPos(x, y, 0), 0)
        val surface = config.chunkZOf(heights[config.chunkSize / 2, config.chunkSize / 2])
        listOf(surface, surface - 1).map { z -> RleCodec.encode(world.materializer.materialize(ChunkPos(x, y, z))) }
      }
    }.filter { it.size >= 64 }

    for (level in listOf(1, 3, 6, 9)) {
      val deflater = Deflater(level)
      val buffer = ByteArray(8192)

      // Twice, so the first pass warms the JIT and the second is the one reported.
      var bytes = 0L
      var nanos = 0L
      repeat(2) {
        bytes = 0L
        val started = System.nanoTime()
        for (raw in payloads) bytes += deflate(deflater, buffer, raw)
        nanos = System.nanoTime() - started
      }

      println(
        "level $level: ${bytes / 1024} KiB for ${payloads.size} chunks of ${payloads.sumOf { it.size } / 1024} KiB raw, " +
          "${nanos / payloads.size / 1000} us per chunk"
      )
    }
  }

  private fun deflate(deflater: Deflater, buffer: ByteArray, raw: ByteArray): Int {
    deflater.reset()
    deflater.setInput(raw)
    deflater.finish()

    val out = ByteArrayOutputStream()
    while (!deflater.finished()) {
      val n = deflater.deflate(buffer)
      if (n == 0) break
      out.write(buffer, 0, n)
    }
    return out.size()
  }
}
