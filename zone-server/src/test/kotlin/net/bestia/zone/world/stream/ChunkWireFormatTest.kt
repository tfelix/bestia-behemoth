package net.bestia.zone.world.stream

import net.bestia.bnet.proto.ChunkProto
import net.bestia.bnet.proto.EnvelopeProto
import net.bestia.worldgen.core.ChunkPos
import net.bestia.worldgen.derived.ChunkDelta
import net.bestia.worldgen.voxel.Occupancy
import net.bestia.worldgen.pipeline.StandardWorld
import net.bestia.worldgen.store.BaseHash
import net.bestia.worldgen.voxel.RleCodec
import net.bestia.worldgen.voxel.VoxelChunk
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.util.zip.Deflater
import java.util.zip.Inflater
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A real generated chunk, all the way out to protobuf bytes and back.
 *
 * Every layer here is separately tested somewhere - the codec in `worldgen`, the framing in
 * `NettyChunkFanOutTest` - and that is exactly why this test exists: the failures that matter are at the
 * joins. A payload that deflates on the way out and is inflated with the wrong bound, or a `bytes` field that
 * loses its final byte, breaks nothing any single-layer test can see.
 *
 * It also pins the size claims the streaming design is budgeted against. Those numbers decide the view radius
 * and the per-tick send budget, so a codec change that quintupled them should fail here rather than turn up as
 * a stuttering client.
 */
class ChunkWireFormatTest {

  private val world by lazy {
    StandardWorld.build(StandardWorld.demoConfig().copy(widthCells = 160, heightCells = 160))
  }

  /** A chunk with terrain in it. Column 1600 of a 160 km world is well inside the land, past the ocean margin. */
  private val surfaceChunk: VoxelChunk by lazy {
    val config = world.config
    val heights = world.columns.heights(ChunkPos(1600, 1600, 0), 0)
    val z = config.chunkZOf(heights[16, 16])

    world.materializer.materialize(ChunkPos(1600, 1600, z))
  }

  private fun deflate(blob: ByteArray): ByteArray {
    val deflater = Deflater(Deflater.BEST_COMPRESSION)
    try {
      deflater.setInput(blob)
      deflater.finish()

      val out = ByteArrayOutputStream()
      val buffer = ByteArray(8192)
      while (!deflater.finished()) out.write(buffer, 0, deflater.deflate(buffer))
      return out.toByteArray()
    } finally {
      deflater.end()
    }
  }

  private fun inflate(blob: ByteArray): ByteArray {
    val inflater = Inflater()
    try {
      inflater.setInput(blob)

      val out = ByteArrayOutputStream()
      val buffer = ByteArray(8192)
      while (!inflater.finished()) {
        val n = inflater.inflate(buffer)
        if (n == 0) break
        out.write(buffer, 0, n)
      }
      return out.toByteArray()
    } finally {
      inflater.end()
    }
  }

  @Test
  fun `a chunk survives encode, deflate, protobuf, inflate and decode`() {
    val encoded = RleCodec.encode(surfaceChunk)

    val message = ChunkDataSMSG(
      chunk = surfaceChunk.chunk,
      revision = 0,
      encoding = ChunkDataSMSG.Encoding.RLE_V2,
      compression = ChunkDataSMSG.Compression.DEFLATE,
      payload = deflate(encoded),
      baseHash = BaseHash.of(surfaceChunk)
    )

    // Through real protobuf bytes rather than the builder, because a `bytes` field mishandled by one byte is
    // the failure this is looking for and a builder round trip would never show it.
    val wire = message.toBnetEnvelope().toByteArray()
    val parsed = EnvelopeProto.Envelope.parseFrom(wire).chunkData

    assertEquals(ChunkProto.ChunkEncoding.CHUNK_ENCODING_RLE_V2, parsed.encoding)
    assertEquals(ChunkProto.ChunkCompression.CHUNK_COMPRESSION_DEFLATE, parsed.compression)

    val decoded = RleCodec.decode(
      ChunkCoords.fromProto(parsed.pos),
      inflate(parsed.payload.toByteArray())
    )

    assertEquals(surfaceChunk.chunk, decoded.chunk)
    assertEquals(surfaceChunk.size, decoded.size)
    assertEquals(surfaceChunk.height, decoded.height)
    assertTrue(surfaceChunk.blocks.contentEquals(decoded.blocks), "materials differ after the round trip")
    assertTrue(
      surfaceChunk.occupancy.contentEquals(decoded.occupancy),
      "occupancy differs after the round trip - partial surface voxels are the whole reason it is sent"
    )
    assertEquals(
      BaseHash.of(surfaceChunk), BaseHash.of(decoded),
      "the hash a client would verify against must match what the server sent"
    )
  }

  @Test
  fun `a surface chunk costs what the streaming budget assumes`() {
    val encoded = RleCodec.encode(surfaceChunk)
    val deflated = deflate(encoded)
    val envelope = ChunkDataSMSG(
      chunk = surfaceChunk.chunk,
      revision = 0,
      encoding = ChunkDataSMSG.Encoding.RLE_V2,
      compression = ChunkDataSMSG.Compression.DEFLATE,
      payload = deflated,
      baseHash = 0L
    ).toBnetEnvelope().toByteArray()

    // Measured at roughly 14.7 kB encoded and 3.1 kB deflated. Bounds are generous: they catch a regression of
    // a factor, which is what would invalidate the view radius, not one of a few percent.
    assertTrue(encoded.size < 60_000, "a surface chunk encoded to ${encoded.size} B")
    assertTrue(deflated.size < 12_000, "a surface chunk deflated to ${deflated.size} B")

    // One chunk per message is what keeps this comfortably clear of the socket's 1 MB frame ceiling.
    assertTrue(
      envelope.size < 1_048_576 / 4,
      "one chunk envelope is ${envelope.size} B, uncomfortably close to the frame limit"
    )
  }

  @Test
  fun `an empty chunk stays tiny and is not compressed`() {
    // High above the terrain: all air, so run-length encoding takes it to a few bytes and deflate would make
    // it bigger. Most chunks in a world are this chunk, which is why the compression flag is per payload.
    val air = world.materializer.materialize(ChunkPos(1600, 1600, 20))
    val encoded = RleCodec.encode(air)

    assertTrue(encoded.size < 64, "an air chunk encoded to ${encoded.size} B")
    assertTrue(
      deflate(encoded).size >= encoded.size,
      "deflate was expected to lose on a payload this small - if it wins, the NONE branch is dead code"
    )
  }

  @Test
  fun `a patch is orders of magnitude smaller than the chunk it describes`() {
    val encoded = RleCodec.encode(surfaceChunk)

    val removals = IntArray(10) { ChunkDelta.pack(it * 256, Occupancy.EMPTY) }
    val patch = ChunkPatchSMSG.of(surfaceChunk.chunk, 0, 1, removals)

    // The number the change-broadcast design is argued from: thirty players in range of a swing's worth of
    // mining cost thirty of these rather than thirty of the chunk.
    //
    // MAX_BYTES_PER_REMOVAL is an upper bound, so a patch is never larger than the sizing assumed - the
    // direction that matters, since the patch-versus-snapshot decision is made against the real encoded size.
    // These indices are 256 apart, so each gap costs two varint bytes; a brush's are adjacent and cost one.
    assertTrue(
      patch.removals.size <= 10 * ChunkPatchCodec.MAX_BYTES_PER_REMOVAL,
      "a ten-voxel patch is ${patch.removals.size} B, over the " +
          "${10 * ChunkPatchCodec.MAX_BYTES_PER_REMOVAL} B bound"
    )
    assertEquals(10, ChunkPatchCodec.decode(patch.removals).size, "all ten removals must survive")
    assertEquals(10, patch.removalCount)
    assertTrue(
      patch.removals.size * 50 < encoded.size,
      "a ten-voxel patch is ${patch.removals.size} B against ${encoded.size} B of chunk - expected far more " +
          "than a fiftyfold saving"
    )
  }
}
